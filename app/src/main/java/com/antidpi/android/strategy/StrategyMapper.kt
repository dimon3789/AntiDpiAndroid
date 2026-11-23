package com.antidpi.android.strategy

import com.antidpi.android.model.AntiDpiStrategy
import com.antidpi.android.model.StrategyTier

/**
 * Конвертер стратегий из формата zapret2 (igareck/anti-dpi-for-russia)
 * в user-space аргументы ByeDPI (ciadpi), работающие без Root и RAW-сокетов.
 *
 * Документация аргументов ByeDPI: https://github.com/hufrea/byedpi/blob/main/README.md
 */
object StrategyMapper {

    /**
     * Стартовый набор проверенных user-space стратегий для мобильных операторов РФ.
     * Все стратегии работают без Root (не используют md5sig/RAW-сокеты).
     */
    val defaultPresets: List<AntiDpiStrategy> = listOf(
        AntiDpiStrategy(
            id = "gold_01",
            name = "Golden #1 — Disorder + TLS Record Split",
            tier = StrategyTier.GOLDEN,
            description = "Базовая универсальная связка: disorder 1 + автоподхват torst + разбиение TLS ClientHello по SNI.",
            ciadpiArgs = listOf("--disorder", "1", "--auto=torst", "--tlsrec", "1+s")
        ),
        AntiDpiStrategy(
            id = "gold_02",
            name = "Golden #2 — Split + Disorder SNI + HTTP Mod",
            tier = StrategyTier.GOLDEN,
            description = "Двойное разбиение со смещением по SNI и модификацией регистра HTTP Host.",
            ciadpiArgs = listOf("--split", "1", "--disorder", "3+s", "--mod-http=h,d", "--auto=torst", "--tlsrec", "1+s")
        ),
        AntiDpiStrategy(
            id = "gold_03",
            name = "Golden #3 — OOB в середину SNI",
            tier = StrategyTier.GOLDEN,
            description = "Внеканальный TCP URG байт внутри SNI — эффективно против ТСПУ на мобильных сетях.",
            ciadpiArgs = listOf("--oob", "1+s", "--auto=torst", "--tlsrec", "3+s")
        ),
        AntiDpiStrategy(
            id = "gold_04",
            name = "Golden #4 — DisOOB + Split",
            tier = StrategyTier.GOLDEN,
            description = "Комбинация обратного порядка пакетов и OOB-байта.",
            ciadpiArgs = listOf("--disoob", "1+s", "--split", "2", "--auto=torst", "--tlsrec", "1+s")
        ),
        AntiDpiStrategy(
            id = "gold_05",
            name = "Golden #5 — TLS Record Mid-SNI",
            tier = StrategyTier.GOLDEN,
            description = "Разрезание TLS-записи точно в середине SNI (0+sm) + split 1.",
            ciadpiArgs = listOf("--tlsrec", "0+sm", "--split", "1", "--mod-http=h,d")
        ),
        AntiDpiStrategy(
            id = "silver_01",
            name = "Silver #1 — Multi-Split TLS",
            tier = StrategyTier.SILVER,
            description = "Множественное разбиение ClientHello на мелкие сегменты.",
            ciadpiArgs = listOf("--split", "1", "--split", "2+s", "--tlsrec", "1+s")
        ),
        AntiDpiStrategy(
            id = "silver_02",
            name = "Silver #2 — Disorder Mid-SNI",
            tier = StrategyTier.SILVER,
            description = "Обратный порядок сегментов от середины SNI без OOB.",
            ciadpiArgs = listOf("--disorder", "0+sm", "--mod-http=h,d")
        ),
        AntiDpiStrategy(
            id = "silver_03",
            name = "Silver #3 — Conservative Split + Timeout",
            tier = StrategyTier.SILVER,
            description = "Мягкая стратегия срабатывает только при таймауте/RST (torst, timeout 3s).",
            ciadpiArgs = listOf("--auto=torst", "--timeout", "3", "--split", "1+s", "--tlsrec", "3+s")
        )
    )

    /**
     * Маппит строку аргументов zapret2 (nfqws/winws) в user-space аргументы ciadpi.
     * Исключает техники, требующие RAW-сокетов/Root (TTL-фейки, badseq, badsum, md5sig, WinDivert-фильтры).
     */
    fun mapZapret2Line(
        id: String,
        name: String,
        zapret2Args: String,
        tier: StrategyTier = StrategyTier.CUSTOM
    ): AntiDpiStrategy {
        val tokens = tokenize(zapret2Args)
        val result = mutableListOf<String>()
        val dropped = mutableListOf<String>()

        var splitPos = "1+s"
        var hasDesync = false
        val httpMods = linkedSetOf<String>()

        // Первый проход: извлекаем позицию разбиения, если задана
        for (token in tokens) {
            if (token.startsWith("--dpi-desync-split-pos=")) {
                val rawPos = token.substringAfter("=").split(",").firstOrNull().orEmpty()
                splitPos = mapZapretPosToCiadpi(rawPos)
            }
        }

        // Второй проход: маппинг режимов desync и HTTP-модификаций
        for (token in tokens) {
            when {
                token.startsWith("--dpi-desync=") -> {
                    val modes = token.substringAfter("=").split(",")
                    for (mode in modes) {
                        when (mode.trim().lowercase()) {
                            "split", "split2", "multisplit" -> {
                                result += listOf("--split", splitPos)
                                hasDesync = true
                            }
                            "disorder", "disorder2", "multidisorder" -> {
                                result += listOf("--disorder", splitPos)
                                hasDesync = true
                            }
                            "tlsrec", "fakedsplit" -> {
                                result += listOf("--tlsrec", splitPos)
                                hasDesync = true
                            }
                            "oob" -> {
                                result += listOf("--oob", splitPos)
                                hasDesync = true
                            }
                            "fake", "fakeddisorder", "syndata" -> {
                                // Fake с подделкой TTL/badseq требует RAW-сокетов или md5sig,
                                // заменяем на безопасный user-space disorder + tlsrec
                                dropped += "desync:$mode (заменено на disorder/tlsrec)"
                                result += listOf("--disorder", splitPos)
                                hasDesync = true
                            }
                            else -> dropped += "desync:$mode"
                        }
                    }
                }

                token == "--hostcase" -> httpMods += "h"
                token == "--domcase" -> httpMods += "d"
                token == "--hostnospace" || token == "--methodeol" -> httpMods += "r"

                // Флаги, несовместимые с user-space без Root
                token.startsWith("--dpi-desync-ttl") ||
                    token.startsWith("--dpi-desync-autottl") ||
                    token.startsWith("--dpi-desync-fooling") ||
                    token.startsWith("--dpi-desync-fake-") ||
                    token.startsWith("--dpi-desync-repeats") ||
                    token.startsWith("--wf-") ||
                    token.startsWith("--filter-") -> {
                    dropped += token
                }
            }
        }

        if (httpMods.isNotEmpty()) {
            result += "--mod-http=${httpMods.joinToString(",")}"
        }

        // Гарантируем, что стратегия не пустая и имеет fallback на torst
        if (!hasDesync) {
            result += listOf("--disorder", "1", "--tlsrec", "1+s")
        }
        if ("--auto=torst" !in result && !result.any { it.startsWith("--auto") }) {
            result += listOf("--auto=torst", "--tlsrec", "1+s")
        }

        return AntiDpiStrategy(
            id = id,
            name = name,
            tier = tier,
            description = if (dropped.isEmpty()) {
                "Сконвертировано из zapret2 без потерь."
            } else {
                "Адаптировано под user-space (исключены Root/RAW флаги: ${dropped.size})."
            },
            ciadpiArgs = result,
            sourceZapret2 = zapret2Args,
            droppedFlags = dropped
        )
    }

    private fun mapZapretPosToCiadpi(raw: String): String {
        val clean = raw.trim().lowercase()
        return when {
            clean == "midsld" || clean == "sniext" -> "0+sm"
            clean == "host" || clean == "endhost" -> "1+h"
            clean.toIntOrNull() != null -> clean
            else -> "1+s"
        }
    }

    private fun tokenize(input: String): List<String> {
        val regex = Regex("[^\s\"']+|\"([^\"]*)\"|'([^']*)'")
        return regex.findAll(input).map { match ->
            match.groups[1]?.value ?: match.groups[2]?.value ?: match.value
        }.toList()
    }
}

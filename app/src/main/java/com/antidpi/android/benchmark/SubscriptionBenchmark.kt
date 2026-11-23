package com.antidpi.android.benchmark

import android.content.Context
import android.util.Base64
import com.antidpi.android.model.AntiDpiStrategy
import com.antidpi.android.model.BenchmarkResult
import com.antidpi.android.service.AntiDpiProxyService
import com.antidpi.android.xray.VlessToXrayConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class TargetEndpoint(
    val host: String,
    val port: Int,
    val label: String
)

/**
 * Бенчмарк подписок (например, BLACK_VLESS_RUS_mobile.txt) через локальный SOCKS5-прокси.
 * Позволяет автоматически подобрать лучшую стратегию под текущего мобильного оператора.
 */
object SubscriptionBenchmark {

    private const val BENCHMARK_PORT = 1089
    private const val SOCKET_TIMEOUT_MS = 3500

    /**
     * Загружает подписку по URL или парсит вставленный текст (обычный список vless:// или Base64).
     */
    suspend fun extractEndpoints(input: String, maxNodes: Int = 8): List<TargetEndpoint> =
        withContext(Dispatchers.IO) {
            val rawText = if (input.trim().startsWith("http://") || input.trim().startsWith("https://")) {
                fetchUrlText(input.trim())
            } else {
                input.trim()
            }

            val decoded = decodeIfBase64(rawText)
            val endpoints = mutableListOf<TargetEndpoint>()

            for (line in decoded.lineSequence()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("vless://", ignoreCase = true)) continue
                runCatching {
                    val node = VlessToXrayConverter.parseVlessUri(trimmed)
                    TargetEndpoint(node.host, node.port, node.remark)
                }.onSuccess { endpoints += it }
                if (endpoints.size >= maxNodes) break
            }
            endpoints
        }

    /**
     * Прогоняет список стратегий на отдельном временном порту (1089),
     * проверяя TCP-подключение к узлам через SOCKS5 (RFC 1928) + замер задержки.
     */
    suspend fun runBenchmark(
        context: Context,
        strategies: List<AntiDpiStrategy>,
        endpoints: List<TargetEndpoint>,
        onProgress: (index: Int, total: Int, strategyName: String) -> Unit
    ): List<BenchmarkResult> = withContext(Dispatchers.IO) {
        val binary = File(context.applicationInfo.nativeLibraryDir, AntiDpiProxyService.BINARY_NAME)
        require(binary.exists()) {
            "Бинарник ${AntiDpiProxyService.BINARY_NAME} не найден. Сначала соберите его через NDK."
        }
        require(endpoints.isNotEmpty()) {
            "Не найдено ни одной валидной vless:// ссылки для теста."
        }

        val results = mutableListOf<BenchmarkResult>()

        strategies.forEachIndexed { idx, strategy ->
            onProgress(idx + 1, strategies.size, strategy.name)

            val cmd = buildList {
                add(binary.absolutePath)
                add("--ip")
                add("127.0.0.1")
                add("--port")
                add(BENCHMARK_PORT.toString())
                addAll(strategy.ciadpiArgs)
            }

            var proc: Process? = null
            try {
                proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                // Даем ciadpi время поднять сокет
                delay(350)

                var okCount = 0
                var totalLatency = 0L

                for (ep in endpoints) {
                    val latency = probeEndpointViaSocks5("127.0.0.1", BENCHMARK_PORT, ep.host, ep.port)
                    if (latency != null) {
                        okCount++
                        totalLatency += latency
                    }
                }

                val avg = if (okCount > 0) totalLatency / okCount else Long.MAX_VALUE
                results += BenchmarkResult(
                    strategy = strategy,
                    reachableCount = okCount,
                    totalTested = endpoints.size,
                    avgLatencyMs = avg
                )
            } finally {
                proc?.let {
                    if (it.isAlive) {
                        it.destroy()
                        if (!it.waitFor(800, TimeUnit.MILLISECONDS)) {
                            it.destroyForcibly()
                        }
                    }
                }
            }
        }

        results.sortedWith(
            compareByDescending<BenchmarkResult> { it.reachableCount }
                .thenBy { it.avgLatencyMs }
        )
    }

    /**
     * Проверяет доступность целевого host:port через локальный SOCKS5-прокси (RFC 1928).
     * Возвращает время отклика в мс или null при ошибке/таймауте.
     */
    fun probeEndpointViaSocks5(
        proxyHost: String,
        proxyPort: Int,
        targetHost: String,
        targetPort: Int
    ): Long? {
        val startNs = System.nanoTime()
        return runCatching {
            Socket().use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.connect(InetSocketAddress(proxyHost, proxyPort), SOCKET_TIMEOUT_MS)

                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                // 1. SOCKS5 Greeting: VER=5, NMETHODS=1, METHOD=0 (No Auth)
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()

                val greetingResp = ByteArray(2)
                if (inp.read(greetingResp) != 2 || greetingResp[0] != 0x05.toByte() || greetingResp[1] != 0x00.toByte()) {
                    return null
                }

                // 2. SOCKS5 CONNECT Request (ATYP=0x03 Domain Name)
                val hostBytes = targetHost.toByteArray(StandardCharsets.UTF_8)
                if (hostBytes.size > 255) return null

                val req = ByteArrayOutputStream().apply {
                    write(0x05) // VER
                    write(0x01) // CMD = CONNECT
                    write(0x00) // RSV
                    write(0x03) // ATYP = DOMAINNAME
                    write(hostBytes.size)
                    write(hostBytes)
                    write((targetPort shr 8) and 0xFF)
                    write(targetPort and 0xFF)
                }.toByteArray()

                out.write(req)
                out.flush()

                // 3. Читаем ответ SOCKS5: VER, REP (0x00 = succeeded), RSV, ATYP
                val header = ByteArray(4)
                if (inp.read(header) != 4 || header[0] != 0x05.toByte() || header[1] != 0x00.toByte()) {
                    return null
                }

                (System.nanoTime() - startNs) / 1_000_000L
            }
        }.getOrNull()
    }

    private fun fetchUrlText(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "AntiDpiAndroid/1.0")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun decodeIfBase64(text: String): String {
        if (text.contains("vless://", ignoreCase = true)) return text
        return runCatching {
            val cleaned = text.replace("\s+".toRegex(), "")
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        }.getOrDefault(text)
    }
}

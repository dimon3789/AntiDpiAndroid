package com.antidpi.android.xray

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedVlessNode(
    val uuid: String,
    val host: String,
    val port: Int,
    val remark: String,
    val encryption: String,
    val flow: String?,
    val network: String,
    val security: String,
    val sni: String?,
    val fingerprint: String?,
    val publicKey: String?,
    val shortId: String?,
    val spiderX: String?,
    val path: String?,
    val hostHeader: String?,
    val serviceName: String?,
    val grpcMode: String?,
    val alpn: List<String>,
    val allowInsecure: Boolean,
    val headerType: String?
)

/**
 * Парсер ссылок vless:// в полный JSON-конфиг Xray-core (Karing, v2rayNG, NekoBox, Throne)
 * с маршрутизацией исходящего трафика через локальный SOCKS5-прокси ByeDPI
 * с помощью streamSettings.sockopt.dialerProxy = "antidpi-out".
 *
 * Спецификация Xray-core: https://xtls.github.io/en/config/transport.html
 */
object VlessToXrayConverter {

    const val ANTIDPI_OUTBOUND_TAG = "antidpi-out"

    fun parseVlessUri(rawUri: String): ParsedVlessNode {
        val trimmed = rawUri.trim()
        require(trimmed.startsWith("vless://", ignoreCase = true)) {
            "Ссылка должна начинаться с vless://"
        }

        val uri = URI(trimmed)
        val uuid = uri.userInfo?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Отсутствует UUID пользователя в vless:// ссылке")
        val host = uri.host?.trim('[', ']')?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Отсутствует адрес сервера (host)")
        val port = if (uri.port > 0) uri.port else 443
        val remark = uri.rawFragment?.let(::decodeUrl).orEmpty().ifBlank { "$host:$port" }

        val query = parseQueryParams(uri.rawQuery.orEmpty())

        val rawAlpn = query["alpn"].orEmpty()
        val alpnList = rawAlpn.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ParsedVlessNode(
            uuid = uuid,
            host = host,
            port = port,
            remark = remark,
            encryption = query["encryption"]?.ifBlank { "none" } ?: "none",
            flow = query["flow"]?.ifBlank { null },
            network = query["type"]?.lowercase()?.ifBlank { "tcp" } ?: "tcp",
            security = query["security"]?.lowercase()?.ifBlank { "none" } ?: "none",
            sni = query["sni"]?.ifBlank { null } ?: query["peer"]?.ifBlank { null },
            fingerprint = query["fp"]?.ifBlank { null },
            publicKey = query["pbk"]?.ifBlank { null },
            shortId = query["sid"],
            spiderX = query["spx"]?.ifBlank { null },
            path = query["path"]?.ifBlank { null },
            hostHeader = query["host"]?.ifBlank { null },
            serviceName = query["serviceName"]?.ifBlank { null },
            grpcMode = query["mode"]?.ifBlank { null },
            alpn = alpnList,
            allowInsecure = query["allowInsecure"] == "1" || query["allowInsecure"].equals("true", true),
            headerType = query["headerType"]?.ifBlank { null }
        )
    }

    /**
     * Генерирует готовый к импорту JSON-конфиг Xray-core.
     *
     * @param vlessUri исходная ссылка vless://
     * @param antiDpiHost локальный хост ByeDPI (по умолчанию 127.0.0.1)
     * @param antiDpiPort локальный порт ByeDPI (по умолчанию 1080)
     * @param localSocksInPort порт локального inbound в Xray (по умолчанию 10808, чтобы не конфликтовать с 1080)
     */
    fun convertToXrayJson(
        vlessUri: String,
        antiDpiHost: String = "127.0.0.1",
        antiDpiPort: Int = 1080,
        localSocksInPort: Int = 10808
    ): String {
        val node = parseVlessUri(vlessUri)

        val userObj = JSONObject().apply {
            put("id", node.uuid)
            put("encryption", node.encryption)
            if (!node.flow.isNullOrBlank()) {
                put("flow", node.flow)
            }
        }

        val vnextObj = JSONObject().apply {
            put("address", node.host)
            put("port", node.port)
            put("users", JSONArray().put(userObj))
        }

        val streamSettings = buildStreamSettings(node)

        val proxyOutbound = JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().put("vnext", JSONArray().put(vnextObj)))
            put("streamSettings", streamSettings)
        }

        // Цепочка Proxy Chaining: Xray отправляет пакеты VLESS-узла в локальный ByeDPI SOCKS5
        val antiDpiOutbound = JSONObject().apply {
            put("tag", ANTIDPI_OUTBOUND_TAG)
            put("protocol", "socks")
            put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject().apply {
                            put("address", antiDpiHost)
                            put("port", antiDpiPort)
                        }
                    )
                )
            )
        }

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }

        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }

        val root = JSONObject().apply {
            put("remarks", "${node.remark} [Anti-DPI]")
            put(
                "log",
                JSONObject().put("loglevel", "warning")
            )
            put(
                "inbounds",
                JSONArray().put(
                    JSONObject().apply {
                        put("tag", "socks-in")
                        put("listen", "127.0.0.1")
                        put("port", localSocksInPort)
                        put("protocol", "socks")
                        put("settings", JSONObject().put("udp", true))
                        put(
                            "sniffing",
                            JSONObject().apply {
                                put("enabled", true)
                                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                            }
                        )
                    }
                )
            )
            put(
                "outbounds",
                JSONArray()
                    .put(proxyOutbound)
                    .put(antiDpiOutbound)
                    .put(directOutbound)
                    .put(blockOutbound)
            )
            put(
                "routing",
                JSONObject().apply {
                    put("domainStrategy", "IPIfNonMatch")
                    put(
                        "rules",
                        JSONArray().put(
                            JSONObject().apply {
                                put("type", "field")
                                put("ip", JSONArray().put("geoip:private"))
                                put("outboundTag", "direct")
                            }
                        )
                    )
                }
            )
        }

        return root.toString(2)
    }

    private fun buildStreamSettings(node: ParsedVlessNode): JSONObject {
        val networkNormalized = when (node.network) {
            "raw" -> "tcp"
            else -> node.network
        }

        return JSONObject().apply {
            put("network", networkNormalized)
            put("security", node.security)

            // Ключевая опция Proxy Chaining: направляем сокет VLESS через antidpi-out
            put(
                "sockopt",
                JSONObject().apply {
                    put("dialerProxy", ANTIDPI_OUTBOUND_TAG)
                    put("tcpNoDelay", true)
                }
            )

            when (node.security) {
                "reality" -> {
                    put(
                        "realitySettings",
                        JSONObject().apply {
                            put("show", false)
                            put("fingerprint", node.fingerprint ?: "chrome")
                            put("serverName", node.sni ?: node.host)
                            put("publicKey", node.publicKey.orEmpty())
                            put("shortId", node.shortId.orEmpty())
                            put("spiderX", node.spiderX ?: "/")
                        }
                    )
                }

                "tls" -> {
                    put(
                        "tlsSettings",
                        JSONObject().apply {
                            put("serverName", node.sni ?: node.host)
                            put("allowInsecure", node.allowInsecure)
                            if (!node.fingerprint.isNullOrBlank()) {
                                put("fingerprint", node.fingerprint)
                            }
                            if (node.alpn.isNotEmpty()) {
                                put("alpn", JSONArray(node.alpn))
                            }
                        }
                    )
                }
            }

            when (networkNormalized) {
                "ws" -> {
                    put(
                        "wsSettings",
                        JSONObject().apply {
                            put("path", node.path ?: "/")
                            if (!node.hostHeader.isNullOrBlank()) {
                                put("headers", JSONObject().put("Host", node.hostHeader))
                            }
                        }
                    )
                }

                "grpc" -> {
                    put(
                        "grpcSettings",
                        JSONObject().apply {
                            put("serviceName", node.serviceName.orEmpty())
                            if (node.grpcMode == "multi") {
                                put("multiMode", true)
                            }
                        }
                    )
                }

                "httpupgrade" -> {
                    put(
                        "httpupgradeSettings",
                        JSONObject().apply {
                            put("path", node.path ?: "/")
                            if (!node.hostHeader.isNullOrBlank()) {
                                put("host", node.hostHeader)
                            }
                        }
                    )
                }

                "xhttp", "splithttp" -> {
                    put(
                        "xhttpSettings",
                        JSONObject().apply {
                            put("path", node.path ?: "/")
                            if (!node.hostHeader.isNullOrBlank()) {
                                put("host", node.hostHeader)
                            }
                            if (!node.grpcMode.isNullOrBlank()) {
                                put("mode", node.grpcMode)
                            }
                        }
                    )
                }

                "tcp" -> {
                    if (node.headerType == "http") {
                        put(
                            "tcpSettings",
                            JSONObject().put(
                                "header",
                                JSONObject().apply {
                                    put("type", "http")
                                    put(
                                        "request",
                                        JSONObject().apply {
                                            put("path", JSONArray().put(node.path ?: "/"))
                                            if (!node.hostHeader.isNullOrBlank()) {
                                                put(
                                                    "headers",
                                                    JSONObject().put(
                                                        "Host",
                                                        JSONArray().put(node.hostHeader)
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (pair in rawQuery.split("&")) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            if (idx == -1) {
                result[decodeUrl(pair)] = ""
            } else {
                val key = decodeUrl(pair.substring(0, idx))
                val value = decodeUrl(pair.substring(idx + 1))
                result[key] = value
            }
        }
        return result
    }

    private fun decodeUrl(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

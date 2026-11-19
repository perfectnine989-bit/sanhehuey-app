package online.sanhehuey.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * CDN-ключ: VLESS + WebSocket + TLS через Cloudflare.
 * Сервер отдаёт один uuid и список адресов Cloudflare (домен + IP), приложение само
 * проверяет их и подключается к самому быстрому рабочему.
 */
object Cdn {
    data class Info(
        val uuid: String,
        val host: String,
        val sni: String,
        val port: Int,
        val path: String,
        val fp: String,
        val nodes: List<String>
    )

    fun parse(o: JSONObject?): Info? {
        if (o == null) return null
        val uuid = o.optString("uuid")
        val host = o.optString("host")
        val path = o.optString("path")
        if (uuid.isBlank() || host.isBlank() || path.isBlank()) return null
        val nodes = mutableListOf<String>()
        val arr: JSONArray? = o.optJSONArray("nodes")
        if (arr != null) for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { nodes.add(it) }
        if (host !in nodes) nodes.add(0, host)
        return Info(
            uuid = uuid,
            host = host,
            sni = o.optString("sni").ifBlank { host },
            port = o.optInt("port", 443),
            path = path,
            fp = o.optString("fp").ifBlank { "chrome" },
            nodes = nodes
        )
    }

    /** Проверка узла: TLS с нужным SNI и WebSocket-апгрейд по секретному пути. Возвращает мс или null. */
    private fun probe(info: Info, node: String): Int? = runCatching {
        val started = System.nanoTime()
        val raw = Socket()
        raw.connect(InetSocketAddress(node, info.port), 2500)
        raw.soTimeout = 2500
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(raw, info.sni, info.port, true) as SSLSocket
        val params = ssl.sslParameters
        params.serverNames = listOf(SNIHostName(info.sni))
        params.applicationProtocols = arrayOf("http/1.1")
        ssl.sslParameters = params
        ssl.startHandshake()
        val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        val req = "GET " + info.path + " HTTP/1.1\r\n" +
            "Host: " + info.host + "\r\n" +
            "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n"
        ssl.outputStream.write(req.toByteArray())
        ssl.outputStream.flush()
        val line = BufferedReader(InputStreamReader(ssl.inputStream)).readLine().orEmpty()
        runCatching { ssl.close() }
        if (line.contains(" 101")) ((System.nanoTime() - started) / 1_000_000L).toInt() else null
    }.getOrNull()

    /** Рабочие узлы от быстрого к медленному: узлы, которые работали в прошлый раз, проверяются тоже. */
    suspend fun rank(info: Info, preferred: List<String>): List<Pair<String, Int>> = coroutineScope {
        val all = (preferred + info.nodes).distinct()
        all.map { node -> async(Dispatchers.IO) { node to probe(info, node) } }
            .awaitAll()
            .mapNotNull { (node, ms) -> if (ms != null) node to ms else null }
            .sortedBy { it.second }
    }

    /** Конфиг Xray: TUN-вход (дескриптор отдаёт VpnService) и один выход VLESS+WS+TLS. */
    fun xrayConfig(info: Info, node: String): String {
        val tun = JSONObject()
            .put("tag", "tun-in")
            .put("protocol", "tun")
            .put(
                "settings",
                JSONObject().put("name", "xray0").put("mtu", 1500).put("userLevel", 0)
            )

        val user = JSONObject().put("id", info.uuid).put("encryption", "none").put("level", 0)
        val vnext = JSONObject()
            .put("address", node)
            .put("port", info.port)
            .put("users", JSONArray().put(user))
        val stream = JSONObject()
            .put("network", "ws")
            .put("security", "tls")
            .put(
                "tlsSettings",
                JSONObject()
                    .put("serverName", info.sni)
                    .put("fingerprint", info.fp)
                    .put("alpn", JSONArray().put("http/1.1"))
            )
            .put(
                "wsSettings",
                JSONObject()
                    .put("path", info.path)
                    .put("host", info.host)
                    .put("headers", JSONObject().put("Host", info.host))
            )
        val proxy = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
        val direct = JSONObject().put("tag", "direct").put("protocol", "freedom")

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(tun))
            .put("outbounds", JSONArray().put(proxy).put(direct))
            .toString()
    }
}

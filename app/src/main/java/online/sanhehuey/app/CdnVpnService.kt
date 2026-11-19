package online.sanhehuey.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * VPN-сервис для CDN-ключа. Создаёт TUN и отдаёт его дескриптор ядру Xray (libv2ray).
 * Само приложение исключено из VPN, поэтому соединение ядра до Cloudflare не зацикливается
 * в собственный туннель.
 */
class CdnVpnService : VpnService() {

    companion object {
        const val EXTRA_CONFIG = "xray_config"
        const val EXTRA_SMART = "smart"

        @Volatile var running = false
        @Volatile var lastError: String? = null
        @Volatile private var instance: CdnVpnService? = null

        fun stop() {
            instance?.stopAll()
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var controller: CoreController? = null

    private val handler = object : CoreCallbackHandler {
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
        override fun onEmitStatus(code: Long, message: String?): Long = 0L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val smart = intent.getBooleanExtra(EXTRA_SMART, true)
        instance = this
        Thread { startCore(config, smart) }.start()
        return START_NOT_STICKY
    }

    private fun xudpKey(id: String): String {
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        return android.util.Base64.encodeToString(
            h,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    private fun startCore(config: String, smart: Boolean) {
        try {
            lastError = null
            val b = Builder()
                .setSession("SanHeHuey")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            if (smart) {
                // Умный режим: в туннель идёт мир минус российские сети
                var added = 0
                for (cidr in Routes.cidrs(this)) {
                    val ip = cidr.substringBefore("/")
                    val prefix = cidr.substringAfter("/", "").toIntOrNull() ?: continue
                    if (runCatching { b.addRoute(ip, prefix) }.isSuccess) added++
                }
                if (added == 0) b.addRoute("0.0.0.0", 0)
            } else {
                b.addRoute("0.0.0.0", 0)
            }

            // Само приложение — мимо VPN (иначе ядро зациклится), плюс выбранные пользователем приложения
            runCatching { b.addDisallowedApplication(packageName) }
            if (smart) {
                for (pkg in Store.directApps(this)) runCatching { b.addDisallowedApplication(pkg) }
            }

            val pfd = b.establish() ?: throw IllegalStateException("Не удалось создать VPN-интерфейс")
            tun = pfd

            Libv2ray.initCoreEnv(filesDir.absolutePath, xudpKey(Store.deviceId(this)))
            val ctl = Libv2ray.newCoreController(handler)
            ctl.startLoop(config, pfd.fd)
            controller = ctl
            running = true
        } catch (e: Throwable) {
            android.util.Log.e("SanHeHuey", "cdn start failed", e)
            lastError = (e::class.java.simpleName + ": " + (e.message ?: "нет деталей")).take(300)
            stopAll()
        }
    }

    fun stopAll() {
        runCatching { controller?.stopLoop() }
        controller = null
        runCatching { tun?.close() }
        tun = null
        running = false
        if (instance === this) instance = null
        stopSelf()
    }

    override fun onRevoke() {
        stopAll()
    }

    override fun onDestroy() {
        runCatching { controller?.stopLoop() }
        runCatching { tun?.close() }
        running = false
        if (instance === this) instance = null
        super.onDestroy()
    }
}

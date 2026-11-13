package online.sanhehuey.app

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.Uri
import android.net.VpnService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

enum class Screen { Loading, Login, Gate, Main }

class AppModel(private val ctx: Context, private val scope: CoroutineScope) {
    var screen by mutableStateOf(Screen.Loading)
    var status by mutableStateOf(VpnStatus.Off)
    var state by mutableStateOf<JSONObject?>(null)
    var mode by mutableStateOf(Store.mode(ctx))
    var error by mutableStateOf<String?>(null)
    var loginWaiting by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var connectedAt by mutableStateOf(0L)
    var busy by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)

    var down by mutableStateOf(0.0)
    var up by mutableStateOf(0.0)
    var ping by mutableStateOf<Int?>(null)

    var requestVpnPermission: ((Intent) -> Unit)? = null
    private var pendingConfig: String? = null
    private var loginJob: Job? = null

    // ---------- запуск ----------

    fun start() {
        if (Vpn.isUp(ctx)) {
            status = VpnStatus.On
            connectedAt = System.currentTimeMillis()
        }
        if (Store.token(ctx).isBlank()) screen = Screen.Login else loadState()
        scope.launch { trafficLoop() }
    }

    fun refresh() {
        if (screen == Screen.Main || screen == Screen.Gate) loadState()
    }

    // ---------- вход через Telegram ----------

    fun login() {
        if (loginWaiting) return
        loginJob = scope.launch {
            loginError = null
            loginWaiting = true
            val res = withContext(Dispatchers.IO) { runCatching { Api.authStart() }.getOrNull() }
            val code = res?.optString("code").orEmpty()
            val url = res?.optString("bot_url").orEmpty()
            if (code.isBlank() || url.isBlank()) {
                loginWaiting = false
                loginError = "Нет связи с сервером"
                return@launch
            }
            open(url)
            var done = false
            var tries = 0
            while (!done && tries < 90) {
                tries++
                delay(2000)
                val st = withContext(Dispatchers.IO) {
                    runCatching { Api.authStatus(code, Store.deviceId(ctx)) }.getOrNull()
                } ?: continue
                when (st.optString("status")) {
                    "ok" -> {
                        Store.saveToken(ctx, st.optString("token"))
                        done = true
                        loginWaiting = false
                        loadState()
                    }
                    "limit" -> {
                        done = true
                        loginWaiting = false
                        loginError = st.optString("message").ifBlank { "Достигнут лимит устройств" }
                    }
                    "no_account" -> {
                        done = true
                        loginWaiting = false
                        loginError = "Аккаунт не найден. Оформи подписку на сайте или в боте, потом войди снова."
                    }
                }
            }
            if (!done) {
                loginWaiting = false
                loginError = "Время вышло. Попробуй ещё раз."
            }
        }
    }

    // ---------- состояние аккаунта и проверка подписки ----------

    private fun looksActive(s: JSONObject): Boolean {
        val days = s.optInt("days_left", 0)
        val exp = s.optString("expires_at")
        val lifetime = days > 3000 || exp.startsWith("01.01.2099")
        return s.optBoolean("active", lifetime || days > 0)
    }

    fun loadState() {
        scope.launch {
            val token = Store.token(ctx)
            val s = withContext(Dispatchers.IO) { runCatching { Api.state(token) }.getOrNull() }
            if (s == null) {
                // Сети нет: не выкидываем из аккаунта, показываем то, что уже было
                if (screen == Screen.Loading) {
                    notice = "Нет связи с сервером"
                    screen = if (state != null) Screen.Main else Screen.Gate
                }
                return@launch
            }
            if (!s.optBoolean("ok")) {
                Store.clearToken(ctx)
                state = null
                screen = Screen.Login
                return@launch
            }
            state = s
            notice = null
            var active = looksActive(s)
            if (!active) {
                // Последнее слово за сервером: если он выдаёт конфиг, подписка действует
                val c = withContext(Dispatchers.IO) { runCatching { Api.config(token) }.getOrNull() }
                active = c?.optBoolean("ok") == true
            }
            if (active) {
                screen = Screen.Main
            } else {
                if (status == VpnStatus.On) disconnectNow()
                screen = Screen.Gate
            }
        }
    }

    fun renew() {
        if (busy) return
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { runCatching { Api.renew(Store.token(ctx)) }.getOrNull() }
            busy = false
            if (r == null) {
                notice = "Нет связи с сервером"
            } else if (r.optBoolean("ok")) {
                notice = "Продлено до " + r.optString("expires_at")
                loadState()
            } else {
                notice = r.optString("message").ifBlank { "Не удалось продлить" }
            }
        }
    }

    fun logout() {
        scope.launch {
            val token = Store.token(ctx)
            if (status == VpnStatus.On) disconnectNow()
            withContext(Dispatchers.IO) { runCatching { Api.logout(token) } }
            Store.clearToken(ctx)
            state = null
            notice = null
            error = null
            loginError = null
            screen = Screen.Login
        }
    }

    fun open(url: String) {
        val target = url.ifBlank { "https://sanhehuey.online" }
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openFromState(key: String) = open(state?.optString(key).orEmpty())

    // ---------- туннель ----------

    fun toggle() {
        when (status) {
            VpnStatus.On -> scope.launch { disconnectNow() }
            VpnStatus.Off -> connect()
            else -> {}
        }
    }

    fun selectMode(m: Mode) {
        if (m == mode) return
        mode = m
        Store.saveMode(ctx, m)
        if (status == VpnStatus.On) {
            scope.launch {
                disconnectNow()
                connect()
            }
        }
    }

    private suspend fun disconnectNow() {
        status = VpnStatus.Disconnecting
        withContext(Dispatchers.IO) { runCatching { Vpn.disconnect(ctx) } }
        status = VpnStatus.Off
        connectedAt = 0L
    }

    private fun connect() {
        scope.launch {
            error = null
            status = VpnStatus.Connecting
            val c = withContext(Dispatchers.IO) { runCatching { Api.config(Store.token(ctx)) }.getOrNull() }
            if (c == null) {
                status = VpnStatus.Off
                error = "Нет связи с сервером"
                return@launch
            }
            if (!c.optBoolean("ok")) {
                status = VpnStatus.Off
                loadState()
                return@launch
            }
            pendingConfig = c.optString("config")
            val prepare = VpnService.prepare(ctx)
            if (prepare != null) {
                val ask = requestVpnPermission
                if (ask != null) ask(prepare) else status = VpnStatus.Off
            } else {
                startTunnel()
            }
        }
    }

    fun onVpnPermission(granted: Boolean) {
        if (granted) {
            startTunnel()
        } else {
            status = VpnStatus.Off
            error = "Нужно разрешение на VPN"
        }
    }

    private fun startTunnel() {
        val raw = pendingConfig
        if (raw == null) {
            status = VpnStatus.Off
            return
        }
        scope.launch {
            status = VpnStatus.Connecting
            val res = withContext(Dispatchers.IO) {
                runCatching { Vpn.connect(ctx, Routes.apply(ctx, raw, mode)) }
            }
            val failure = res.exceptionOrNull()
            if (failure == null) {
                status = VpnStatus.On
                connectedAt = System.currentTimeMillis()
            } else {
                android.util.Log.e("SanHeHuey", "connect failed", failure)
                status = VpnStatus.Off
                error = "Ошибка: " + failure::class.java.simpleName + " — " +
                    (failure.message ?: failure.cause?.message ?: "нет деталей")
            }
        }
    }

    // ---------- скорость и пинг ----------

    private suspend fun trafficLoop() {
        var rx = TrafficStats.getTotalRxBytes()
        var tx = TrafficStats.getTotalTxBytes()
        var tick = 0
        while (true) {
            delay(1000)
            val r = TrafficStats.getTotalRxBytes()
            val t = TrafficStats.getTotalTxBytes()
            down = ((r - rx).coerceAtLeast(0L) * 8).toDouble() / 1_000_000.0
            up = ((t - tx).coerceAtLeast(0L) * 8).toDouble() / 1_000_000.0
            rx = r
            tx = t
            if (status == VpnStatus.On) {
                if (tick % 5 == 0) ping = withContext(Dispatchers.IO) { measurePing() }
                tick++
            } else {
                ping = null
                tick = 0
            }
        }
    }

    private fun measurePing(): Int? = runCatching {
        val started = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 2500) }
        ((System.nanoTime() - started) / 1_000_000L).toInt()
    }.getOrNull()
}

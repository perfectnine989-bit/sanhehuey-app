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

enum class Screen { Loading, Login, Gate, Keys, Main }

/** Ключ аккаунта. inUse — занят каким-то устройством, mine — именно этим, by — название занявшего устройства. */
data class KeyItem(val id: String, val name: String, val inUse: Boolean, val mine: Boolean, val by: String)

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

    // выбор ключа
    var keys by mutableStateOf<List<KeyItem>>(emptyList())
    var selectedKey by mutableStateOf(Store.keyId(ctx))
    var keyBusy by mutableStateOf(false)
    var keyTakeover by mutableStateOf<KeyItem?>(null)
    var keysError by mutableStateOf<String?>(null)
    var keysFromProfile by mutableStateOf(false)
    private var keysSupported = false

    // приложения для «Умного» режима
    var appsPage by mutableStateOf(false)
    var directApps by mutableStateOf(Store.directApps(ctx))
    private var appliedApps: Set<String> = emptySet()

    /** Тип ключа аккаунта: "cdn" или "awg" (по ответу сервера). */
    val keyType: String get() = state?.optString("key_type").orEmpty().ifBlank { "awg" }

    var down by mutableStateOf(0.0)
    var up by mutableStateOf(0.0)
    var ping by mutableStateOf<Int?>(null)

    var requestVpnPermission: ((Intent) -> Unit)? = null
    private var pendingConfig: String? = null
    private var pendingCdn: JSONObject? = null
    private var cdnNode: String? = null
    private var loginJob: Job? = null

    // ---------- запуск ----------

    fun start() {
        if (Vpn.isUp(ctx)) {
            status = VpnStatus.On
            connectedAt = System.currentTimeMillis()
        }
        if (BuildConfig.TEAM) {
            screen = Screen.Main // без входа: сразу главный экран
        } else if (Store.token(ctx).isBlank()) {
            screen = Screen.Login
        } else {
            loadState()
        }
        scope.launch { trafficLoop() }
    }

    fun refresh() {
        if (BuildConfig.TEAM) return
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
                selectedKey = ""
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
                refreshKeys(token)
                screen = if (keysSupported && selectedKey.isBlank()) Screen.Keys else Screen.Main
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
            keys = emptyList()
            selectedKey = ""
            keysSupported = false
            keysFromProfile = false
            appsPage = false
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


    // ---------- приложения для «Умного» режима ----------

    fun toggleDirectApp(pkg: String) {
        val next = directApps.toMutableSet()
        if (!next.add(pkg)) next.remove(pkg)
        directApps = next
        Store.saveDirectApps(ctx, next)
    }

    fun clearDirectApps() {
        directApps = emptySet()
        Store.saveDirectApps(ctx, emptySet())
    }

    /** Закрыли список: если туннель уже поднят и список изменился — переподключаемся с новым. */
    fun closeApps() {
        appsPage = false
        if (status == VpnStatus.On && mode == Mode.Smart && directApps != appliedApps) {
            scope.launch {
                disconnectNow()
                connect()
            }
        }
    }

    // ---------- выбор ключа ----------

    /**
     * Сервер — источник правды: какой ключ закреплён за этим устройством и кем заняты остальные.
     * Если сервер не знает про ключи (старая версия API) — всё работает как раньше, без выбора.
     */
    private suspend fun refreshKeys(token: String) {
        val r = withContext(Dispatchers.IO) {
            runCatching { Api.keys(token, Store.deviceId(ctx)) }.getOrNull()
        } ?: return
        if (!r.optBoolean("ok")) {
            keysSupported = false
            return
        }
        val arr = r.optJSONArray("keys")
        val list = mutableListOf<KeyItem>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val k = arr.optJSONObject(i) ?: continue
                val id = k.optString("id")
                if (id.isBlank()) continue
                list.add(
                    KeyItem(
                        id = id,
                        name = k.optString("name").ifBlank { "Ключ " + (i + 1) },
                        inUse = k.optBoolean("in_use"),
                        mine = k.optBoolean("mine"),
                        by = k.optString("by")
                    )
                )
            }
        }
        keys = list
        keysSupported = list.isNotEmpty()
        if (!keysSupported) return

        var server = r.optString("selected").ifBlank { list.firstOrNull { it.mine }?.id.orEmpty() }

        // Единственный свободный ключ выбираем сами
        if (server.isBlank() && list.size == 1 && !list[0].inUse) {
            val sel = withContext(Dispatchers.IO) {
                runCatching {
                    Api.selectKey(token, Store.deviceId(ctx), deviceName(), list[0].id, false)
                }.getOrNull()
            }
            if (sel?.optBoolean("ok") == true) server = list[0].id
        }

        val local = Store.keyId(ctx)
        if (server != local) {
            Store.saveKeyId(ctx, server)
            selectedKey = server
            if (status == VpnStatus.On) {
                disconnectNow()
                if (server.isBlank()) {
                    notice = "Твой ключ заняли на другом устройстве. Выбери ключ заново."
                } else {
                    connect()
                }
            }
        }
    }

    private fun deviceName(): String =
        (android.os.Build.MANUFACTURER.orEmpty() + " " + android.os.Build.MODEL.orEmpty()).trim().ifBlank { "Android" }

    fun openKeys() {
        keysFromProfile = true
        keysError = null
        keyTakeover = null
        scope.launch {
            refreshKeys(Store.token(ctx))
            screen = Screen.Keys
        }
    }

    fun closeKeys() {
        keyTakeover = null
        keysFromProfile = false
        if (selectedKey.isNotBlank()) screen = Screen.Main
    }

    fun pickKey(item: KeyItem, force: Boolean = false) {
        if (keyBusy) return
        if (item.inUse && !item.mine && !force) {
            keyTakeover = item
            return
        }
        scope.launch {
            keyBusy = true
            keysError = null
            keyTakeover = null
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    Api.selectKey(Store.token(ctx), Store.deviceId(ctx), deviceName(), item.id, force)
                }.getOrNull()
            }
            keyBusy = false
            if (r == null) {
                keysError = "Нет связи с сервером"
                return@launch
            }
            if (!r.optBoolean("ok")) {
                if (r.optBoolean("busy")) {
                    keyTakeover = item.copy(inUse = true, by = r.optString("by").ifBlank { item.by })
                } else {
                    keysError = r.optString("message").ifBlank { "Не удалось выбрать ключ" }
                }
                return@launch
            }
            val changed = Store.keyId(ctx) != item.id
            Store.saveKeyId(ctx, item.id)
            selectedKey = item.id
            keysFromProfile = false
            screen = Screen.Main
            val wasOn = status == VpnStatus.On
            refreshKeys(Store.token(ctx))
            if (wasOn && changed) {
                disconnectNow()
                connect()
            }
        }
    }

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
            if (BuildConfig.TEAM) {
                connectTeam()
                return@launch
            }
            val key = if (keysSupported) Store.keyId(ctx) else ""
            val c = withContext(Dispatchers.IO) {
                runCatching {
                    if (key.isNotBlank()) Api.config(Store.token(ctx), Store.deviceId(ctx), key)
                    else Api.config(Store.token(ctx))
                }.getOrNull()
            }
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
            if (c.optString("type") == "cdn") {
                pendingCdn = c.optJSONObject("cdn")
                pendingConfig = null
            } else {
                pendingCdn = null
                pendingConfig = c.optString("config")
            }
            askVpnAndStart()
        }
    }

    private fun askVpnAndStart() {
        val prepare = VpnService.prepare(ctx)
        if (prepare != null) {
            val ask = requestVpnPermission
            if (ask != null) ask(prepare) else status = VpnStatus.Off
        } else {
            startTunnel()
        }
    }

    /**
     * Сборка для команды: устройство регистрируется на сервере по общему ключу приложения
     * (лимит устройств и отзыв доступа — на сервере). Если сервер недоступен, берём последний сохранённый ключ.
     */
    private suspend fun connectTeam() {
        val r = withContext(Dispatchers.IO) {
            runCatching { Api.teamConnect(BuildConfig.TEAM_SECRET, Store.deviceId(ctx), deviceName()) }.getOrNull()
        }
        var payload: JSONObject? = null
        if (r != null) {
            if (r.optBoolean("ok")) {
                payload = r.optJSONObject("cdn")
                Store.saveTeamCdn(ctx, payload?.toString().orEmpty())
            } else {
                val reason = r.optString("reason")
                if (reason == "limit" || reason == "disabled" || reason == "bad_secret") {
                    Store.saveTeamCdn(ctx, "")
                    status = VpnStatus.Off
                    error = when (reason) {
                        "limit" -> "Достигнут лимит устройств. Напиши администратору."
                        "disabled" -> "Доступ закрыт администратором."
                        else -> "Это приложение больше не действует. Получи новую версию."
                    }
                    return
                }
            }
        }
        if (payload == null) {
            val cached = Store.teamCdn(ctx)
            if (cached.isNotBlank()) payload = runCatching { JSONObject(cached) }.getOrNull()
        }
        if (payload == null) {
            status = VpnStatus.Off
            error = "Нет связи с сервером"
            return
        }
        pendingCdn = payload
        pendingConfig = null
        askVpnAndStart()
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
        val cdn = pendingCdn
        if (raw == null && cdn == null) {
            status = VpnStatus.Off
            return
        }
        scope.launch {
            status = VpnStatus.Connecting
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    if (cdn != null) {
                        val info = Cdn.parse(cdn) ?: throw IllegalStateException("Некорректный CDN-ключ с сервера")
                        val ranked = Cdn.rank(info, Store.cdnNodes(ctx))
                        if (ranked.isEmpty()) throw IllegalStateException("Ни один адрес Cloudflare не отвечает. Проверь интернет.")
                        var started: String? = null
                        var lastFail: Throwable? = null
                        for ((node, _) in ranked.take(3)) {
                            try {
                                Vpn.connectCdn(ctx, Cdn.xrayConfig(info, node), mode == Mode.Smart)
                                started = node
                                break
                            } catch (e: Throwable) {
                                lastFail = e
                            }
                        }
                        if (started == null) throw (lastFail ?: IllegalStateException("не удалось подключиться"))
                        cdnNode = started
                        Store.saveCdnNodes(ctx, ranked.take(3).map { it.first })
                    } else {
                        cdnNode = null
                        Vpn.connect(ctx, Routes.apply(ctx, raw!!, mode))
                    }
                }
            }
            val failure = res.exceptionOrNull()
            if (failure == null) {
                appliedApps = Store.directApps(ctx)
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
        // у CDN приложение вне туннеля, поэтому меряем до узла Cloudflare, через который идёт соединение
        val target = cdnNode ?: "1.1.1.1"
        Socket().use { it.connect(InetSocketAddress(target, 443), 2500) }
        ((System.nanoTime() - started) / 1_000_000L).toInt()
    }.getOrNull()
}

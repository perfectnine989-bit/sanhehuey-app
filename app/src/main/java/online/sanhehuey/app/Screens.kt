package online.sanhehuey.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun AppRoot(model: AppModel, version: String) {
    val time = rememberSeconds()
    val target = Palette.of(model.status)
    val main by animateColorAsState(target.main, tween(800), label = "main")
    val light by animateColorAsState(target.light, tween(800), label = "light")
    val glow by animateColorAsState(target.glow, tween(800), label = "glow")
    val palette = Palette(main, light, glow)
    var profileTab by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        SceneBackground(palette, model.status == VpnStatus.On, time)

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Header(palette, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (model.screen) {
                    Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Spinner(Th.goldLight, 28.dp)
                    }
                    Screen.Login -> LoginScreen(model)
                    Screen.Gate -> GateScreen(model, palette)
                    Screen.Keys -> KeysScreen(model, palette)
                    Screen.Main ->
                        if (model.appsPage) AppsScreen(model, palette)
                        else if (profileTab) ProfileScreen(model, palette, version)
                        else HomeScreen(model, palette, time)
                }
            }

            if (model.screen == Screen.Main) {
                GlassSegmented(
                    items = listOf(false, true),
                    selected = profileTab,
                    onSelect = {
                        if (model.appsPage) model.closeApps()
                        profileTab = it
                    },
                    tint = palette.main.copy(alpha = 0.14f),
                    height = 52.dp,
                    corner = 26.dp,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 6.dp)
                ) { isProfile, isSelected ->
                    val color = if (isSelected) palette.light else Th.muted
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TabIcon(home = !isProfile, color = color)
                        Spacer(Modifier.height(3.dp))
                        T(if (isProfile) "Профиль" else "Главная", 12, font = Th.title, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------- Главная ----------

private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

@Composable
private fun HomeScreen(model: AppModel, palette: Palette, time: State<Float>) {
    val status = model.status
    val statusText = when (status) {
        VpnStatus.On -> "ПОДКЛЮЧЕНО"
        VpnStatus.Connecting -> "ПОДКЛЮЧЕНИЕ"
        VpnStatus.Disconnecting -> "ОТКЛЮЧЕНИЕ"
        VpnStatus.Off -> "ОТКЛЮЧЕНО"
    }
    val statusColor = when (status) {
        VpnStatus.On -> Th.jade
        VpnStatus.Off -> Th.text
        else -> Color(0xFFFFC25A)
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Кнопка занимает всё свободное место и уменьшается на маленьких экранах
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val fit = minOf(1f, maxHeight / 300.dp, maxWidth / 300.dp)
            Box(
                Modifier
                    .requiredSize(300.dp)
                    .graphicsLayer {
                        scaleX = fit
                        scaleY = fit
                    }
            ) {
                PowerButton(status, palette, time) { model.toggle() }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.heightIn(min = 84.dp).padding(horizontal = 20.dp)
        ) {
            T(statusText, 21, font = Th.title, color = statusColor, tracking = 4f, shadow = statusShadow, maxLines = 1)
            NeonBanner(palette.glow, if (status == VpnStatus.On) model.connectedAt else 0L, time)

            if (status == VpnStatus.On && model.connectedAt > 0L) {
                val now by produceState(System.currentTimeMillis()) {
                    while (true) {
                        delay(1000)
                        value = System.currentTimeMillis()
                    }
                }
                T(clock(now - model.connectedAt), 16, color = Th.goldLight, shadow = statusShadow)
            } else {
                T("Нажми на кнопку, чтобы подключиться", 14, color = Th.muted, shadow = statusShadow, align = TextAlign.Center)
            }
            model.error?.let {
                T(it, 12, color = Th.crimson, align = TextAlign.Center, maxLines = 3)
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("РЕЖИМ РАБОТЫ", Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(10.dp))

        GlassSegmented(
            items = listOf(Mode.Smart, Mode.Global),
            selected = model.mode,
            onSelect = { model.selectMode(it) },
            tint = palette.main.copy(alpha = 0.14f),
            height = 62.dp,
            corner = 22.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) { mode, isSelected ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(mode.title, 14, font = Th.title, color = if (isSelected) Color.White else Th.text, maxLines = 1)
                T(
                    mode.caption, 11,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Th.muted, maxLines = 1
                )
            }
        }

        TrafficStrip(model, palette, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp))
    }
}

// ---------- Вход ----------

@Composable
private fun LoginScreen(model: AppModel) {
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(30.dp))
                .padding(vertical = 26.dp, horizontal = 18.dp)
        ) {
            Avatar(Th.telegram)
            Spacer(Modifier.height(14.dp))
            T("BossTriad", 28, font = Th.hero, brush = Th.goldBrush, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            T(
                "Войди через Telegram. Ключи подтянутся сами, ничего импортировать не нужно.",
                13, color = Th.muted, align = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            GlassButton(
                if (model.loginWaiting) "Жду подтверждения в Telegram…" else "Войти через Telegram",
                tint = Th.telegram.copy(alpha = 0.32f),
                loading = model.loginWaiting
            ) { model.login() }

            if (model.loginWaiting) {
                Spacer(Modifier.height(8.dp))
                T(
                    "Открой Telegram и нажми Start у бота, потом вернись сюда.",
                    12, color = Th.muted, align = TextAlign.Center
                )
            }
            model.loginError?.let {
                Spacer(Modifier.height(8.dp))
                T(it, 12, color = Th.crimson, align = TextAlign.Center)
            }
        }
    }
}

// ---------- Подписка не активна ----------

@Composable
private fun GateScreen(model: AppModel, palette: Palette) {
    val s = model.state
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(30.dp))
                .padding(vertical = 26.dp, horizontal = 18.dp)
        ) {
            T("ПОДПИСКА НЕ АКТИВНА", 19, font = Th.title, color = Th.crimson, tracking = 3f, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            T(
                "Доступ откроется, как только подписка будет оплачена. Баланс общий с сайтом и ботом.",
                13, color = Th.muted, align = TextAlign.Center
            )
            if (s != null) {
                Spacer(Modifier.height(14.dp))
                T("Баланс: " + s.optInt("balance") + " ₽", 16, color = Th.goldLight)
                Spacer(Modifier.height(14.dp))
                GlassButton(
                    "Продлить с баланса · " + s.optInt("renew_price") + " ₽",
                    tint = palette.main.copy(alpha = 0.30f),
                    loading = model.busy
                ) { model.renew() }
                Spacer(Modifier.height(10.dp))
                GlassButton("Пополнить на сайте", tint = Color.Black.copy(alpha = 0.22f)) {
                    model.openFromState("topup_url")
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassButton("Проверить снова", tint = Color.Black.copy(alpha = 0.22f)) { model.loadState() }

            model.notice?.let {
                Spacer(Modifier.height(10.dp))
                T(it, 12, color = Th.goldLight, align = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            T(
                "Выйти из аккаунта", 14, color = Th.muted,
                modifier = Modifier.clickable { model.logout() }.padding(8.dp)
            )
        }
    }
}

// ---------- Профиль ----------

@Composable
private fun Avatar(glow: Color) {
    Image(
        painter = painterResource(R.drawable.avatar_default),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(108.dp)
            .clip(CircleShape)
            .border(2.5.dp, Th.goldBrush, CircleShape)
    )
}

private fun accountRows(s: JSONObject): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    val days = s.optInt("days_left", 0)
    val exp = s.optString("expires_at")
    val lifetime = days > 3000 || exp.startsWith("01.01.2099")
    rows.add("Баланс" to (s.optInt("balance").toString() + " ₽"))
    rows.add("Подписка до" to (if (lifetime) "Бессрочно" else exp.ifBlank { "—" }))
    if (!lifetime && days > 0) rows.add("Осталось" to "$days дн.")
    rows.add("Устройства" to (s.optInt("slots_used").toString() + " из " + s.optInt("slots_total")))
    rows.add("Продление" to (s.optInt("renew_price").toString() + " ₽"))
    val tier = s.optJSONObject("loyalty")?.optString("tier").orEmpty()
    if (tier.isNotBlank()) rows.add("Статус" to tier)
    return rows
}

@Composable
private fun ProfileScreen(model: AppModel, palette: Palette, version: String) {
    val s = model.state
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(30.dp))
                .padding(vertical = 22.dp, horizontal = 12.dp)
        ) {
            Avatar(palette.glow)
            Spacer(Modifier.height(14.dp))
            T(
                s?.optString("client_name").orEmpty().ifBlank { if (BuildConfig.TEAM) "Команда" else "BossTriad" },
                26, font = Th.hero, brush = Th.goldBrush, maxLines = 1
            )
        }

        if (s != null) {
            InfoCard("АККАУНТ", accountRows(s))
            GlassButton(
                "Продлить с баланса",
                tint = palette.main.copy(alpha = 0.30f),
                loading = model.busy
            ) { model.renew() }
            GlassButton("Пополнить на сайте", tint = Color.Black.copy(alpha = 0.22f)) {
                model.openFromState("topup_url")
            }
            model.notice?.let {
                T(it, 13, color = Th.goldLight, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        val statusText = when (model.status) {
            VpnStatus.On -> "Подключено"
            VpnStatus.Connecting -> "Подключение"
            VpnStatus.Disconnecting -> "Отключение"
            VpnStatus.Off -> "Отключено"
        }
        val connection = mutableListOf("Протокол" to (if (BuildConfig.TEAM || model.keyType == "cdn") "VLESS · CDN" else "AmneziaWG"), "Режим" to model.mode.title, "Статус" to statusText)
        val keyName = model.keys.firstOrNull { it.id == model.selectedKey }?.name.orEmpty()
        if (keyName.isNotBlank()) connection.add("Ключ" to keyName)
        val location = s?.optString("location").orEmpty()
        if (location.isNotBlank()) connection.add("Локация" to location)
        InfoCard("СОЕДИНЕНИЕ", connection)
        if (model.keys.size > 1) {
            GlassButton("Сменить ключ", tint = palette.main.copy(alpha = 0.30f)) { model.openKeys() }
        }

        InfoCard(
            "УМНЫЙ РЕЖИМ",
            listOf("Напрямую (приложения)" to (if (model.directApps.isEmpty()) "нет" else model.directApps.size.toString()))
        )
        GlassButton("Приложения напрямую", tint = palette.main.copy(alpha = 0.30f)) { model.appsPage = true }
        T(
            "Выбранные приложения в «Умном» режиме обходят VPN целиком, как и российские сайты.",
            12, color = Th.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        InfoCard("ПРИЛОЖЕНИЕ", listOf("Версия" to version))

        GlassButton("Написать в поддержку", tint = Th.telegram.copy(alpha = 0.26f)) {
            model.open("https://t.me/S1llonGOD")
        }
        if (!BuildConfig.TEAM) {
            T(
                "Выйти из аккаунта", 14, color = Th.muted, align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { model.logout() }.padding(vertical = 10.dp)
            )
        }
    }
}

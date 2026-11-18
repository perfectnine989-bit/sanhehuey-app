package online.sanhehuey.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Выбор ключа. Показывается сразу после входа через Telegram и из профиля.
 * Один ключ AmneziaWG нельзя держать на двух устройствах сразу, поэтому занятые ключи
 * помечены, а чтобы взять такой — нужно подтвердить: другое устройство отключится.
 */
@Composable
fun KeysScreen(model: AppModel, palette: Palette) {
    val canLeave = model.selectedKey.isNotBlank()
    BackHandler(enabled = canLeave) { model.closeKeys() }
    val takeover = model.keyTakeover

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 20.dp)
    ) {
        if (canLeave) {
            T(
                "‹ Назад", 15, font = Th.title, color = palette.light,
                modifier = Modifier.clickable { model.closeKeys() }.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
        T("Выбери ключ", 26, font = Th.hero, brush = Th.goldBrush, maxLines = 1)
        T(
            "Один ключ работает только на одном устройстве. Если подключить его с двух сразу, " +
                "одно из соединений оборвётся. Выбор общий для всех твоих приложений.",
            13, color = Th.muted
        )

        model.notice?.let { T(it, 13, color = Th.goldLight) }

        model.keys.forEach { key ->
            val picked = key.id == model.selectedKey
            val sub = when {
                picked -> "Используется на этом устройстве"
                key.inUse -> "Занят" + if (key.by.isNotBlank()) ": " + key.by else " другим устройством"
                else -> "Свободен"
            }
            val subColor = when {
                picked -> Th.jade
                key.inUse -> Th.crimson
                else -> Th.muted
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(
                        RoundedCornerShape(24.dp),
                        if (picked) palette.main.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.22f)
                    )
                    .clickable(enabled = !model.keyBusy) { model.pickKey(key) }
                    .padding(18.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    T(key.name, 17, font = Th.title, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    T(sub, 13, color = subColor, maxLines = 2)
                }
                if (picked) T("✓", 20, color = Th.jade)
            }
        }

        if (model.keyBusy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Spinner(Th.goldLight, 24.dp) }
        }

        // подтверждение: ключ занят другим устройством
        if (takeover != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), Th.crimson.copy(alpha = 0.18f))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                T(
                    "«" + takeover.name + "» сейчас занят" +
                        (if (takeover.by.isNotBlank()) " устройством " + takeover.by else " другим устройством") +
                        ". Если взять его, там VPN отключится.",
                    14, align = TextAlign.Center
                )
                GlassButton("Взять этот ключ", tint = Th.crimson.copy(alpha = 0.34f)) {
                    model.pickKey(takeover, force = true)
                }
                GlassButton("Отмена", tint = Color.Black.copy(alpha = 0.22f)) { model.keyTakeover = null }
            }
        }

        model.keysError?.let { T(it, 13, color = Th.crimson, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

        if (!canLeave) {
            T(
                "Выйти из аккаунта", 14, color = Th.muted, align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { model.logout() }.padding(vertical = 10.dp)
            )
        }
    }
}

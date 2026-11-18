package online.sanhehuey.app

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val pkg: String, val label: String)

/** Все приложения с иконкой на экране (кроме нашего), по алфавиту. */
private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

/** Страница «Приложения напрямую» — открывается из профиля. */
@Composable
fun AppsScreen(model: AppModel, palette: Palette) {
    val ctx = LocalContext.current
    BackHandler { model.closeApps() }

    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { loadApps(ctx) }.getOrDefault(emptyList()) }
    }
    val selected = model.directApps

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(
                "‹ Назад", 15, font = Th.title, color = palette.light,
                modifier = Modifier.clickable { model.closeApps() }.padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                T(
                    "Сбросить", 14, color = Th.muted,
                    modifier = Modifier.clickable { model.clearDirectApps() }.padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        T("Приложения напрямую", 22, font = Th.hero, brush = Th.goldBrush, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        T(
            "В «Умном» режиме выбранные приложения всегда ходят в сеть напрямую, мимо VPN. " +
                "Выбрано: " + selected.size + ".",
            13, color = Th.muted
        )
        Spacer(Modifier.height(12.dp))

        // поиск
        Box(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            if (query.isEmpty()) T("Поиск приложения", 15, color = Th.muted)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Th.text, fontSize = 15.sp, fontFamily = Th.body),
                cursorBrush = SolidColor(Th.goldLight),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))

        val list = apps
        if (list == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Spinner(Th.goldLight, 28.dp)
            }
        } else {
            val shown = if (query.isBlank()) list
            else list.filter { it.label.contains(query.trim(), ignoreCase = true) }
            if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    T("Ничего не найдено", 14, color = Th.muted, align = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(shown, key = { it.pkg }) { app ->
                        AppRow(app, app.pkg in selected) { model.toggleDirectApp(app.pkg) }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, on: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(app.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) {
            icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(40.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(app.label, 15, maxLines = 1)
            T(if (on) "Напрямую" else "Через VPN", 12, color = if (on) Th.goldLight else Th.muted, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Switch(on)
    }
}

@Composable
private fun Switch(on: Boolean) {
    Box(
        Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(if (on) Th.gold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else Th.muted)
        )
    }
}

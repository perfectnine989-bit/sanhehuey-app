package online.sanhehuey.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val textShadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset.Zero, 12f)

@Composable
fun Header(palette: Palette, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            T("SanHeHuey", 32, font = Th.hero, brush = Th.goldBrush, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            T(
                "VPN", 17, font = Th.title, color = palette.main, tracking = 2f,
                shadow = Shadow(palette.glow.copy(alpha = 0.7f), Offset.Zero, 18f),
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp, 1.dp).background(Th.goldDim.copy(alpha = 0.5f)))
            T(
                "三和会", 13, font = FontFamily.Serif, color = Th.goldDim, tracking = 6f,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Box(Modifier.size(34.dp, 1.dp).background(Th.goldDim.copy(alpha = 0.5f)))
        }
    }
}

/** «Под защитой Триады»: выключенная неоновая трубка, вспыхивает при подключении. */
@Composable
fun NeonBanner(color: Color, since: Long, time: State<Float>) {
    Box(contentAlignment = Alignment.Center) {
        T("ПОД ЗАЩИТОЙ ТРИАДЫ", 18, font = Th.neon, color = Color.White.copy(alpha = 0.10f), tracking = 3f, maxLines = 1)
        T(
            "ПОД ЗАЩИТОЙ ТРИАДЫ", 18, font = Th.neon, color = Color.White.copy(alpha = 0.95f),
            tracking = 3f, maxLines = 1,
            shadow = Shadow(color, Offset.Zero, 26f),
            modifier = Modifier.graphicsLayer {
                val t = time.value
                alpha = if (since <= 0L) {
                    0f
                } else {
                    val age = (System.currentTimeMillis() - since) / 1000f
                    if (age < 1.6f) {
                        val steps = floatArrayOf(0f, 0.9f, 0.1f, 1f, 0.2f, 0.15f, 1f, 0.5f, 1f, 1f)
                        steps[(age / 0.16f).toInt().coerceIn(0, steps.size - 1)]
                    } else {
                        0.93f + 0.07f * sin(t * 5f)
                    }
                }
            }
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Th.goldDim.copy(alpha = 0.4f)))
        T(text, 12, font = Th.title, color = Th.goldDim, tracking = 5f, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Th.goldDim.copy(alpha = 0.4f)))
    }
}

/** Стеклянный переключатель с плавающей таблеткой. */
@Composable
fun <I> GlassSegmented(
    items: List<I>,
    selected: I,
    onSelect: (I) -> Unit,
    tint: Color,
    height: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (I, Boolean) -> Unit
) {
    val pad = 4.dp
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height + pad * 2).glass(RoundedCornerShape(corner + pad))
    ) {
        val segW = (maxWidth - pad * 2) / items.size
        val index = items.indexOf(selected).coerceAtLeast(0)
        val x by animateDpAsState(pad + segW * index, spring(dampingRatio = 0.8f, stiffness = 380f), label = "thumb")
        Box(
            Modifier
                .offset(x = x, y = pad)
                .size(segW, height)
                .glass(RoundedCornerShape(corner), tint)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Row(Modifier.padding(pad)) {
            for (item in items) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(segW)
                        .height(height)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(item)
                        }
                ) {
                    content(item, item == selected)
                }
            }
        }
    }
}

/** Скорость и пинг. */
@Composable
fun TrafficStrip(model: AppModel, palette: Palette, modifier: Modifier = Modifier) {
    val active = model.status == VpnStatus.On
    fun speed(v: Double): String =
        if (!active) "—" else if (v < 10) String.format("%.1f", v) else String.format("%.0f", v)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(vertical = 13.dp)
    ) {
        Stat("↓", speed(model.down), "Мбит/с", palette, Modifier.weight(1f))
        Box(Modifier.size(1.dp, 22.dp).background(Th.goldDim.copy(alpha = 0.25f)))
        Stat("↑", speed(model.up), "Мбит/с", palette, Modifier.weight(1f))
        Box(Modifier.size(1.dp, 22.dp).background(Th.goldDim.copy(alpha = 0.25f)))
        Stat("◔", if (active) model.ping?.toString() ?: "—" else "—", "мс", palette, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(icon: String, value: String, unit: String, palette: Palette, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        T(icon, 16, font = FontFamily.Default, color = palette.light)
        Spacer(Modifier.width(6.dp))
        T(value, 20, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        T(unit, 11, color = Th.muted, maxLines = 1)
    }
}

/** Стеклянная кнопка-капсула. */
@Composable
fun GlassButton(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(50), tint)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp)
    ) {
        if (loading) {
            Spinner(Th.text, 16.dp)
            Spacer(Modifier.width(10.dp))
        }
        T(text, 15, font = Th.title, align = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun Spinner(color: Color, size: Dp) {
    val angle by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle"
    )
    Canvas(Modifier.size(size)) {
        rotate(angle) {
            drawArc(
                color, 0f, 270f, false,
                topLeft = Offset(2f, 2f),
                size = Size(this.size.width - 4f, this.size.height - 4f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/** Иконки вкладок: домик и человек. */
@Composable
fun TabIcon(home: Boolean, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        if (home) {
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.08f)
                lineTo(w * 0.95f, h * 0.48f)
                lineTo(w * 0.82f, h * 0.48f)
                lineTo(w * 0.82f, h * 0.92f)
                lineTo(w * 0.18f, h * 0.92f)
                lineTo(w * 0.18f, h * 0.48f)
                lineTo(w * 0.05f, h * 0.48f)
                close()
            }
            drawPath(path, color)
        } else {
            drawCircle(color, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.28f))
            drawArc(
                color, 180f, 180f, true,
                topLeft = Offset(w * 0.12f, h * 0.56f),
                size = Size(w * 0.76f, h * 0.76f)
            )
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, corner: Dp = 24.dp, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(corner)).padding(18.dp)) {
        content()
    }
}

@Composable
fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card {
        T(title, 12, font = Th.title, color = Th.goldDim, tracking = 4f)
        rows.forEachIndexed { i, row ->
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(row.first, 15, color = Th.muted)
                Spacer(Modifier.weight(1f).width(12.dp))
                T(row.second, 15, align = TextAlign.End, maxLines = 2)
            }
            if (i < rows.size - 1) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Th.goldDim.copy(alpha = 0.18f)))
            }
        }
    }
}

val statusShadow: Shadow get() = textShadow

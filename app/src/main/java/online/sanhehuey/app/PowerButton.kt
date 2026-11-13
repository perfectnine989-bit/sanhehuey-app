package online.sanhehuey.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private fun DrawScope.comet(color: Color, length: Float, width: Float, diameter: Float, angle: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val topLeft = Offset(c.x - diameter / 2f, c.y - diameter / 2f)
    val arcSize = Size(diameter, diameter)
    val brush = Brush.sweepGradient(
        0f to color.copy(alpha = 0f),
        length * 0.55f to color.copy(alpha = 0.25f),
        length to color,
        1f to color.copy(alpha = 0f),
        center = c
    )
    rotate(angle, c) {
        // мягкое свечение
        drawArc(
            brush = brush, startAngle = 0f, sweepAngle = 360f * length, useCenter = false,
            topLeft = topLeft, size = arcSize, alpha = 0.30f,
            style = Stroke(width * 3.4f, cap = StrokeCap.Round), blendMode = BlendMode.Plus
        )
        drawArc(
            brush = brush, startAngle = 0f, sweepAngle = 360f * length, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width, cap = StrokeCap.Round)
        )
        // яркая головка
        val theta = length * 2f * Math.PI.toFloat()
        val head = Offset(c.x + cos(theta) * diameter / 2f, c.y + sin(theta) * diameter / 2f)
        drawCircle(color.copy(alpha = 0.45f), radius = width * 2.4f, center = head, blendMode = BlendMode.Plus)
        drawCircle(Color.White, radius = (width + 1.5f * density) / 2f, center = head)
    }
}

@Composable
fun PowerButton(status: VpnStatus, palette: Palette, time: State<Float>, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "press"
    )

    val infinite = rememberInfiniteTransition(label = "ring")
    val slow by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "slow"
    )
    val fast by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1150, easing = LinearEasing)), label = "fast"
    )
    val breathe by infinite.animateFloat(
        0.78f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "breathe"
    )

    val connected = status == VpnStatus.On
    val connecting = status == VpnStatus.Connecting
    val label = when (status) {
        VpnStatus.On -> "ОТКЛЮЧИТЬ"
        VpnStatus.Connecting -> "ПОДКЛЮЧЕНИЕ"
        VpnStatus.Disconnecting -> "ОТКЛЮЧЕНИЕ"
        VpnStatus.Off -> "ПОДКЛЮЧИТЬ"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(300.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val d = 258.dp.toPx()

            // расходящиеся волны, когда подключено
            if (connected) {
                val t = time.value
                for (i in 0 until 3) {
                    val p = fract(t / 3.6f + i / 3f)
                    drawCircle(
                        palette.glow.copy(alpha = (1f - p) * 0.30f),
                        radius = 133.dp.toPx() * (1f + p * 0.2f),
                        center = c,
                        style = Stroke(1.2.dp.toPx())
                    )
                }
            }

            // тонкая золотая рамка снаружи
            drawCircle(
                Brush.linearGradient(
                    listOf(
                        Th.goldLight.copy(alpha = 0.55f),
                        Th.gold.copy(alpha = 0.06f),
                        Th.gold.copy(alpha = 0.30f)
                    )
                ),
                radius = (d + 30.dp.toPx()) / 2f, center = c, style = Stroke(0.8.dp.toPx())
            )

            // стеклянная трубка
            drawCircle(Color.Black.copy(alpha = 0.38f), d / 2f, c, style = Stroke(11.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.06f), d / 2f, c, style = Stroke(11.dp.toPx()))
            drawCircle(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.03f),
                        Color.White.copy(alpha = 0.16f)
                    )
                ),
                radius = (d + 11.dp.toPx()) / 2f, center = c, style = Stroke(1.dp.toPx())
            )
            drawCircle(
                Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.White.copy(alpha = 0.22f))),
                radius = (d - 11.dp.toPx()) / 2f, center = c, style = Stroke(1.dp.toPx())
            )

            if (connected) {
                val lit = Brush.sweepGradient(
                    listOf(palette.main, palette.light, palette.main, palette.light, palette.main), c
                )
                drawCircle(lit, d / 2f, c, alpha = 0.30f * breathe, style = Stroke(16.dp.toPx()), blendMode = BlendMode.Plus)
                drawCircle(lit, d / 2f, c, alpha = breathe, style = Stroke(3.2.dp.toPx()))
            } else if (connecting) {
                comet(palette.light, 0.66f, 4.dp.toPx(), d, fast)
            } else {
                comet(Th.goldLight, 0.30f, 3.2.dp.toPx(), d, slow)
            }

            // свечение под центром
            drawCircle(
                Brush.radialGradient(
                    listOf(palette.main.copy(alpha = 0.60f), palette.main.copy(alpha = 0.18f), Color.Transparent),
                    center = c, radius = 113.dp.toPx()
                ),
                radius = 113.dp.toPx(), center = c
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(216.dp)
                .glass(CircleShape, palette.main.copy(alpha = 0.14f))
                .border(
                    1.1.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.5f), Color.Transparent, Color.White.copy(alpha = 0.14f))
                    ),
                    CircleShape
                )
        ) {
            Canvas(Modifier.size(52.dp).graphicsLayer {
                alpha = if (connecting) 0.6f + 0.4f * sin(time.value * 5f) else 1f
            }) {
                val w = 3.dp.toPx()
                val brush = Brush.verticalGradient(listOf(Color.White, palette.light))
                val inset = 6.dp.toPx()
                drawArc(
                    palette.glow.copy(alpha = 0.35f), -55f, 290f, false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(w * 3f, cap = StrokeCap.Round), blendMode = BlendMode.Plus
                )
                drawArc(
                    brush, -55f, 290f, false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(w, cap = StrokeCap.Round)
                )
                drawLine(
                    brush, Offset(size.width / 2f, 2.dp.toPx()), Offset(size.width / 2f, size.height * 0.5f),
                    strokeWidth = w, cap = StrokeCap.Round
                )
            }
            Box(Modifier.size(10.dp))
            T(label, 15, font = Th.title, tracking = 3f, maxLines = 1)
        }
    }
}

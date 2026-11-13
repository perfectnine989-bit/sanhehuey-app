package online.sanhehuey.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun SceneBackground(palette: Palette, isOn: Boolean, time: State<Float>) {
    Box(Modifier.fillMaxSize().background(Th.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(palette.main.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.05f),
                    radius = size.height * 0.55f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(palette.main.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = size.width * 0.95f
                )
            )
        }

        NeonSign(palette.glow, isOn, time)
        DragonAura(palette)

        Image(
            painter = painterResource(R.drawable.smoke),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val t = time.value
                scaleX = 1.3f
                scaleY = 1.3f
                translationX = sin(t * 0.15f) * 34f * density
                translationY = cos(t * 0.11f) * 24f * density
                alpha = 0.14f
            }
        )

        EmberField(palette.light, palette.glow, time)

        // Затемнение сверху (название) и снизу (карточки), плюс виньетка
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    startY = 0f, endY = size.height * 0.2f
                )
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                    startY = size.height * 0.5f, endY = size.height
                )
            )
            drawRect(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.75f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.height * 0.78f
                )
            )
        }
    }
}

@Composable
private fun DragonAura(palette: Palette) {
    val transition = rememberInfiniteTransition(label = "dragon")
    val breathe by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight * 0.92f
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = maxWidth * 0.02f, y = maxHeight * 0.06f)
                .size(h / 2, h)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Плавно гасим нижнюю часть
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White,
                            0.5f to Color.White,
                            1f to Color.White.copy(alpha = 0.18f)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            Image(
                painter = painterResource(R.drawable.dragon_aura),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(palette.glow),
                modifier = Modifier.fillMaxSize().blur(26.dp).graphicsLayer { alpha = breathe }
            )
            Image(
                painter = painterResource(R.drawable.dragon_aura),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = 0.40f
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    palette.light.copy(alpha = 0.9f),
                                    palette.main.copy(alpha = 0.6f),
                                    palette.main.copy(alpha = 0.28f)
                                )
                            ),
                            blendMode = BlendMode.SrcIn
                        )
                    }
            )
        }
    }
}

/** Неоновая вывеска 三和会 слева: тусклая, когда VPN выключен, и мерцает, когда включён. */
@Composable
private fun NeonSign(color: Color, lit: Boolean, time: State<Float>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(x = maxWidth * 0.10f - 20.dp, y = maxHeight * 0.30f - 52.dp)
                .graphicsLayer {
                    val t = time.value
                    val flicker = 0.84f + 0.16f * sin(t * 3.1f) * sin(t * 1.7f + 1.3f)
                    alpha = if (lit) flicker else 0.28f
                }
                .border(1.6.dp, color, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 9.dp)
        ) {
            for (glyph in listOf("三", "和", "会")) {
                T(
                    glyph, 22,
                    font = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.92f),
                    shadow = Shadow(color, Offset.Zero, 22f)
                )
            }
        }
    }
}

/** Искры: тонкие, неяркие, летят вверх. */
@Composable
private fun EmberField(color: Color, glow: Color, time: State<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        for (i in 0 until 28) {
            val seed = i * 12.9898f
            val speed = 0.045f + fract(sin(seed) * 43758.547f) * 0.07f
            val phase = fract(sin(seed * 1.7f) * 24634.635f)
            val x0 = fract(sin(seed * 3.1f) * 13758.545f)
            val sway = 0.03f + fract(sin(seed * 2.3f) * 7919.1f) * 0.05f
            val thick = (0.5f + fract(sin(seed * 5.3f) * 9758.5f)) * density
            val big = i % 12 == 0

            fun point(tt: Float): Offset {
                val p = fract(tt * speed + phase)
                val x = (x0 + sin(tt * 0.9f + seed) * sway) * size.width
                val y = size.height * (1.02f - p * 1.1f)
                return Offset(x, y)
            }

            val head = point(t)
            val tail = point(t - 0.4f)
            if (tail.y < head.y) continue // искра перескочила снизу вверх, хвост не рисуем

            val progress = fract(t * speed + phase)
            val fade = sin(progress * PI.toFloat()).coerceAtLeast(0f).pow(0.8f)
            val twinkle = 0.5f + 0.5f * sin(t * (5f + fract(seed) * 6f) + seed)
            val a = (fade * twinkle * 0.55f).coerceIn(0f, 1f)

            drawLine(
                brush = Brush.linearGradient(
                    listOf(glow.copy(alpha = 0f), color.copy(alpha = a * 0.8f)),
                    start = tail, end = head
                ),
                start = tail, end = head,
                strokeWidth = thick * 1.5f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus
            )
            drawLine(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = a)),
                    start = tail, end = head
                ),
                start = tail, end = head,
                strokeWidth = thick * 0.6f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus
            )
            if (big) {
                val r = 3.5f * density + thick
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = a), glow.copy(alpha = a * 0.4f), Color.Transparent),
                        center = head, radius = r
                    ),
                    radius = r, center = head,
                    blendMode = BlendMode.Plus
                )
            }
        }
    }
}

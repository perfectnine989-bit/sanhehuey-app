package online.sanhehuey.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class VpnStatus { Off, Connecting, On, Disconnecting }

enum class Mode(val title: String, val caption: String) {
    Smart("Умный", "РФ напрямую"),
    Global("Весь трафик", "Всё через VPN")
}

/** Цвета меняются от состояния: красный (выкл), янтарный (подключение), изумрудный (включено). */
data class Palette(val main: Color, val light: Color, val glow: Color) {
    companion object {
        val Idle = Palette(Color(0xFFD81E3C), Color(0xFFFF8A78), Color(0xFFFF2D4F))
        val Busy = Palette(Color(0xFFF2A21E), Color(0xFFFFD684), Color(0xFFFFB020))
        val On = Palette(Color(0xFF24C58E), Color(0xFFC4FFE8), Color(0xFF3FD9A0))

        fun of(status: VpnStatus): Palette = when (status) {
            VpnStatus.On -> On
            VpnStatus.Off -> Idle
            else -> Busy
        }
    }
}

object Th {
    val bg = Color(0xFF050506)
    val gold = Color(0xFFC9A15A)
    val goldLight = Color(0xFFE8CE94)
    val goldDim = Color(0xFF9C8455)
    val jade = Color(0xFF3FD9A0)
    val crimson = Color(0xFFD81E3C)
    val text = Color(0xFFEDE4D4)
    val muted = Color(0xFF948B7E)
    val telegram = Color(0xFF2AABEE)

    val goldBrush = Brush.verticalGradient(
        listOf(Color(0xFFFFF1CF), Color(0xFFE8CE94), Color(0xFFC9A15A))
    )

    val hero = FontFamily(Font(R.font.cinzel))
    val title = FontFamily(Font(R.font.prata))
    val neon = FontFamily(Font(R.font.yeseva))
    val body = FontFamily(Font(R.font.ledger))
}

/** Текст в стиле приложения. */
@Composable
fun T(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    font: FontFamily = Th.body,
    color: Color = Th.text,
    tracking: Float = 0f,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    brush: Brush? = null,
    shadow: Shadow? = null
) {
    var style = TextStyle(
        fontFamily = font,
        fontSize = size.sp,
        letterSpacing = tracking.sp,
        shadow = shadow
    )
    style = if (brush != null) style.copy(brush = brush) else style.copy(color = color)
    if (align != null) style = style.copy(textAlign = align)
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** Стекло: тёмная полупрозрачная подложка, лёгкий отлив и блик по краю. */
fun Modifier.glass(shape: Shape, tint: Color = Color.Black.copy(alpha = 0.22f)): Modifier =
    this
        .clip(shape)
        .background(Color(0xFF16161A).copy(alpha = 0.42f))
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))
            )
        )
        .background(tint)
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.40f),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = 0.18f)
                )
            ),
            shape
        )

/** Секунды с момента запуска, обновляются каждый кадр. Читать только внутри рисования. */
@Composable
fun rememberSeconds(): State<Float> = produceState(0f) {
    val start = withFrameNanos { it }
    while (true) {
        withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
}

fun fract(v: Float): Float = v - kotlin.math.floor(v)

package com.fujitake.cribbage.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fujitake.cribbage.engine.Grade
import kotlinx.coroutines.delay

object Ink {
    val Ground = Color(0xFF0C0E12)
    val Raised = Color(0xFF15181E)
    val Hairline = Color(0x24FFFFFF)
    val Text = Color(0xFFF1F2F4)
    val Muted = Color(0xFF8E95A3)
    val Accent = Color(0xFFC8F04B)
    val OnAccent = Color(0xFF12150A)
    val Amber = Color(0xFFF5B342)
    val Red = Color(0xFFFF6B5C)
}

val Mono = FontFamily(
    Font(R.font.jetbrainsmono_light, FontWeight.Light),
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
)

val Sans = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** False when the system has animations turned off. */
val LocalMotion = compositionLocalOf { true }

fun gradeColor(grade: Grade?): Color = when (grade) {
    Grade.BEST -> Ink.Accent
    Grade.GOOD, null -> Ink.Text
    Grade.INACCURACY -> Ink.Amber
    Grade.MISTAKE -> Ink.Red
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    color: Color = Ink.Text,
    family: FontFamily = Sans,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    lineHeight: Int? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontFamily = family,
            fontWeight = weight,
            fontSize = size.sp,
            textAlign = align,
            lineHeight = (lineHeight ?: (size * 1.4f).toInt()).sp,
        ),
    )
}

/** A small uppercase mono label above a section. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Ink.Muted) {
    BasicText(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontFamily = Mono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            letterSpacing = 0.12.em,
        ),
    )
}

@Composable
fun ActionButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (enabled) Ink.Accent else Ink.Raised, RoundedCornerShape(14.dp))
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, color = if (enabled) Ink.OnAccent else Ink.Muted, weight = FontWeight.SemiBold, size = 16)
    }
}

/** Rises and settles into place, offset by [index] so groups arrive in a stagger. */
@Composable
fun Modifier.riseIn(index: Int = 0): Modifier {
    val motion = LocalMotion.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    val lift = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = lift * (1f - progress.value)
    }
}

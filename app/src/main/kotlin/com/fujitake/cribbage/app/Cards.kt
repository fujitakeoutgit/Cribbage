package com.fujitake.cribbage.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fujitake.cribbage.engine.Card
import kotlinx.coroutines.delay

private const val CARD_ASPECT = 1.42f

fun cardName(card: Card): String {
    val rank = listOf(
        "", "Ace", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Jack", "Queen", "King",
    )[card.rank]
    val suit = listOf("clubs", "diamonds", "hearts", "spades")[card.suit]
    return "$rank of $suit"
}

/** Suits are drawn, not typed, because some fonts render them as emoji. */
@Composable
fun SuitGlyph(suit: Int, size: Dp, modifier: Modifier = Modifier, color: Color? = null) {
    val ink = color ?: LocalFaceStyle.current.ink(suit)
    Canvas(modifier.size(size)) { drawSuit(suit, ink) }
}

private fun DrawScope.drawSuit(suit: Int, color: Color) {
    val w = size.width
    val h = size.height
    when (suit) {
        0 -> {
            val r = w * 0.21f
            drawCircle(color, r, Offset(w * 0.5f, h * 0.27f))
            drawCircle(color, r, Offset(w * 0.27f, h * 0.58f))
            drawCircle(color, r, Offset(w * 0.73f, h * 0.58f))
            drawCircle(color, r * 0.6f, Offset(w * 0.5f, h * 0.5f))
            drawPath(stem(w, h), color)
        }
        1 -> drawPath(
            Path().apply {
                moveTo(w * 0.5f, h * 0.02f)
                lineTo(w * 0.86f, h * 0.5f)
                lineTo(w * 0.5f, h * 0.98f)
                lineTo(w * 0.14f, h * 0.5f)
                close()
            },
            color,
        )
        2 -> drawPath(heart(w, h, flip = false), color)
        else -> {
            drawPath(heart(w, h * 0.84f, flip = true), color)
            drawPath(stem(w, h), color)
        }
    }
}

private fun heart(w: Float, h: Float, flip: Boolean): Path {
    fun y(fraction: Float) = if (flip) h * (1f - fraction) else h * fraction
    return Path().apply {
        moveTo(w * 0.5f, y(0.96f))
        cubicTo(w * 0.1f, y(0.66f), w * 0.0f, y(0.42f), w * 0.04f, y(0.28f))
        cubicTo(w * 0.1f, y(0.04f), w * 0.42f, y(0.02f), w * 0.5f, y(0.26f))
        cubicTo(w * 0.58f, y(0.02f), w * 0.9f, y(0.04f), w * 0.96f, y(0.28f))
        cubicTo(w * 1.0f, y(0.42f), w * 0.9f, y(0.66f), w * 0.5f, y(0.96f))
        close()
    }
}

private fun stem(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.46f, h * 0.5f)
    lineTo(w * 0.54f, h * 0.5f)
    lineTo(w * 0.66f, h)
    lineTo(w * 0.34f, h)
    close()
}

@Composable
fun CardFace(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(width * 0.1f)
    val lift by animateDpAsState(
        targetValue = if (selected) (-10).dp else 0.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "lift",
    )
    val fade by animateFloatAsState(if (dimmed) 0.38f else 1f, tween(200), label = "dim")
    val style = LocalFaceStyle.current
    val ink = style.ink(card.suit)
    Box(
        modifier
            .offset(y = lift)
            .graphicsLayer { alpha = fade }
            .width(width)
            .height(width * CARD_ASPECT)
            .background(style.face, shape)
            .then(if (style.outlined) Modifier.border(1.dp, Ink.Hairline, shape) else Modifier)
            .then(if (selected) Modifier.border(2.5.dp, Ink.Accent, shape) else Modifier)
            .clearAndSetSemantics {
                contentDescription = cardName(card) + if (selected) ", selected" else ""
                if (onClick != null) {
                    semanticClick {
                        onClick()
                        true
                    }
                }
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(start = width * 0.09f, top = width * 0.06f)) {
            Txt(
                card.rankLabel,
                size = (width.value * 0.27f).toInt(),
                family = Mono,
                weight = FontWeight.Bold,
                color = ink,
                lineHeight = (width.value * 0.31f).toInt(),
            )
            SuitGlyph(card.suit, width * 0.16f, Modifier.padding(top = width * 0.015f))
        }
        SuitGlyph(
            card.suit,
            width * 0.4f,
            Modifier.align(Alignment.BottomEnd).padding(end = width * 0.09f, bottom = width * 0.09f),
        )
    }
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.1f)
    val style = LocalBackStyle.current
    Canvas(
        modifier
            .width(width)
            .height(width * CARD_ASPECT)
            .background(style.color, shape)
            .border(1.dp, Ink.Hairline, shape),
    ) {
        val inset = size.width * 0.14f
        val step = size.width * 0.12f
        val top = Offset(inset, inset)
        val area = Size(size.width - inset * 2, size.height - inset * 2)
        var x = 0f
        while (x <= area.width) {
            drawLine(style.line, Offset(top.x + x, top.y), Offset(top.x + x, top.y + area.height), 1.5f)
            x += step
        }
    }
}

/** A card that turns over after [delayMs]. */
@Composable
fun FlipCard(card: Card, width: Dp, delayMs: Long = 0, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val turn = remember(card) { Animatable(if (motion) 0f else 180f) }
    LaunchedEffect(card) {
        delay(delayMs)
        turn.animateTo(180f, tween(520, easing = FastOutSlowInEasing))
    }
    Box(
        modifier.graphicsLayer {
            rotationY = turn.value
            cameraDistance = 14f * density
        },
    ) {
        if (turn.value < 90f) {
            CardBack(width)
        } else {
            CardFace(card, width, Modifier.graphicsLayer { rotationY = 180f })
        }
    }
}

/** Deals a card in with a slight turn, staggered by [index]. */
@Composable
fun Modifier.dealIn(index: Int): Modifier {
    val motion = LocalMotion.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = 120f * (1f - p)
        rotationZ = -7f * (1f - p)
        scaleX = 0.9f + 0.1f * p
        scaleY = 0.9f + 0.1f * p
    }
}

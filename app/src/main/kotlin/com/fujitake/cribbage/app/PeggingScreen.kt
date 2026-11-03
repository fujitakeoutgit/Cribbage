package com.fujitake.cribbage.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.fujitake.cribbage.engine.COMPUTER
import com.fujitake.cribbage.engine.Card
import com.fujitake.cribbage.engine.Coaching
import com.fujitake.cribbage.engine.PegEvent
import com.fujitake.cribbage.engine.PeggingState
import com.fujitake.cribbage.engine.PlayReport
import com.fujitake.cribbage.engine.YOU
import kotlin.math.roundToInt

@Composable
fun PeggingScreen(ui: UiState, onPlay: (Card) -> Unit, onContinue: () -> Unit) {
    val peg = requireNotNull(ui.peg)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            TopLine(ui, peg)
            CountBlock(peg)
            Pile(peg, Modifier.padding(top = 18.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Computer")
                Spacer(Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    repeat(peg.hands[COMPUTER].size) { CardBack(34.dp) }
                }
            }
            Txt(
                latestEvent(ui, peg),
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp),
                size = 15,
                weight = FontWeight.SemiBold,
                color = Ink.Accent,
            )
            YourHand(ui, peg, onPlay)
        }

        AnimatedVisibility(
            visible = ui.verdict != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { it } +
                fadeIn(tween(160)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            ui.plays.lastOrNull()?.let { VerdictSheet(it, onContinue) }
        }
    }
}

@Composable
private fun TopLine(ui: UiState, peg: PeggingState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Tally("You", ui.scores[YOU])
        Spacer(Modifier.width(24.dp))
        Tally("Computer", ui.scores[COMPUTER])
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Eyebrow("Starter")
            ui.starter?.let { FlipCard(it, 44.dp, delayMs = 150, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun Tally(label: String, value: Int) {
    val shown by animateIntAsState(value, tween(300), label = "tally")
    Column {
        Txt("$shown", size = 26, family = Mono, weight = FontWeight.Light)
        Eyebrow(label)
    }
}

@Composable
private fun CountBlock(peg: PeggingState) {
    val shown by animateIntAsState(peg.count, tween(260), label = "count")
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("$shown", size = 76, family = Mono, weight = FontWeight.Light, lineHeight = 82)
            Eyebrow("/ 31", Modifier.padding(start = 8.dp, bottom = 14.dp))
        }
        CountRail(peg.count)
    }
}

/** Thirty-one ticks, with the fifteen and thirty-one marks standing taller. */
@Composable
private fun CountRail(count: Int) {
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val step = size.width / 30f
        for (i in 1..31) {
            val tall = i == 15 || i == 31
            val x = step * (i - 1)
            val top = if (tall) 0f else size.height * 0.45f
            val color = if (i <= count) Ink.Accent else Ink.Hairline
            drawLine(
                color = color,
                start = Offset(x, top),
                end = Offset(x, size.height),
                strokeWidth = if (tall) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun Pile(peg: PeggingState, modifier: Modifier = Modifier) {
    val seats = peg.events.filterIsInstance<PegEvent.Played>().associate { it.card to it.seat }
    BoxWithConstraints(modifier.fillMaxWidth().height(100.dp)) {
        val cardWidth = 58.dp
        val slots = (peg.series.size - 1).coerceAtLeast(1)
        // Cards sit apart while they fit, and only overlap once a long series needs the room.
        val step = min(cardWidth + 6.dp, (maxWidth - cardWidth) / slots)
        peg.series.forEachIndexed { i, card ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(x = step * i).dealIn(0),
            ) {
                CardFace(card, cardWidth, Modifier.border(1.dp, Ink.Ground, RoundedCornerShape(6.dp)))
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .width(20.dp)
                        .height(2.dp)
                        .background(if (seats[card] == YOU) Ink.Accent else Ink.Muted),
                )
            }
        }
    }
}

private fun latestEvent(ui: UiState, peg: PeggingState): String {
    val seatName = { seat: Int -> if (seat == YOU) "You" else "Computer" }
    val recent = peg.events.takeLast(4).filter { it !is PegEvent.Reset }
    val last = recent.lastOrNull()
    if (last == null) {
        val heels = if (ui.starter?.rank == 11) {
            "His heels: 2 for ${if (ui.youDeal) "you" else "the computer"}. "
        } else {
            ""
        }
        return heels + if (peg.toMove == YOU) "Your lead." else "Computer leads."
    }
    return when (last) {
        is PegEvent.Played ->
            if (last.points > 0) "${seatName(last.seat)}: ${last.labels.joinToString(", ")} for ${last.points}"
            else if (peg.toMove == YOU && !peg.finished) "Your play." else ""
        is PegEvent.Go -> "${seatName(last.seat)} can't go."
        is PegEvent.GoPoint -> "${seatName(last.seat)} takes 1 for the go."
        is PegEvent.LastCard -> "${seatName(last.seat)} takes 1 for last card."
        PegEvent.Reset -> ""
    }
}

@Composable
private fun YourHand(ui: UiState, peg: PeggingState, onPlay: (Card) -> Unit) {
    val hand = peg.hands[YOU]
    val yourTurn = peg.toMove == YOU && !peg.finished && !ui.busy && ui.verdict == null
    BoxWithConstraints(Modifier.fillMaxWidth().height(142.dp)) {
        val width = min((maxWidth - 30.dp) / 4, 96.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            hand.forEachIndexed { i, card ->
                val legal = yourTurn && card in peg.legal
                CardFace(
                    card = card,
                    width = width,
                    modifier = Modifier.dealIn(i),
                    dimmed = yourTurn && !legal,
                    onClick = if (legal) ({ onPlay(card) }) else null,
                )
            }
        }
    }
}

/** Asks for the plain text form of suit symbols, which would otherwise fall back to emoji. */
private fun textSuits(text: String) = text.replace(Regex("[♣♦♥♠]")) { it.value + "︎" }

@Composable
private fun VerdictSheet(report: PlayReport, onContinue: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val isBest = report.pointsLost == 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.Raised, shape)
            .border(1.dp, Ink.Hairline, shape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(
                report.grade?.label.orEmpty(),
                size = 26,
                family = Mono,
                weight = FontWeight.Light,
                color = gradeColor(report.grade),
            )
            Spacer(Modifier.weight(1f))
            Txt(
                if (isBest) "top pick" else "−${points(report.pointsLost)} pts",
                size = 14,
                family = Mono,
                color = Ink.Muted,
            )
        }
        Txt(
            textSuits(Coaching.explain(report)),
            Modifier.padding(top = 6.dp),
            size = 15,
            lineHeight = 21,
        )
        Gap(14)
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Eyebrow("Card")
            Spacer(Modifier.weight(1f))
            Eyebrow("Pegs vs computer")
        }
        Hairline()
        report.options.forEach { option ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                val mine = option.card == report.played.card
                MiniCard(option.card, if (mine) Ink.Accent else Ink.Text)
                if (mine) Eyebrow("played", Modifier.padding(start = 8.dp), color = Ink.Accent)
                Spacer(Modifier.weight(1f))
                Txt(
                    "${(option.replyChance * 100).roundToInt()}% reply risk",
                    size = 12,
                    family = Mono,
                    color = Ink.Muted,
                )
                Txt(
                    signed(option.value),
                    Modifier.width(64.dp),
                    size = 13,
                    family = Mono,
                    weight = FontWeight.Bold,
                    align = TextAlign.End,
                )
            }
        }
        Gap(12)
        ActionButton("Continue", onClick = onContinue)
    }
}

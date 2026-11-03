package com.fujitake.cribbage.app

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fujitake.cribbage.engine.COMPUTER
import com.fujitake.cribbage.engine.Card
import com.fujitake.cribbage.engine.GAME_TARGET
import com.fujitake.cribbage.engine.GameScore
import com.fujitake.cribbage.engine.HandCount
import com.fujitake.cribbage.engine.HandResult
import com.fujitake.cribbage.engine.YOU
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val COUNT_BEAT_MS = 800L

private fun countTitle(count: HandCount): String = when {
    count.crib && count.seat == YOU -> "Your crib"
    count.crib -> "Computer's crib"
    count.seat == YOU -> "Your hand"
    else -> "Computer's hand"
}

/**
 * Counts each hand by itself, one combination at a time, non-dealer first. You press to move on
 * once a hand's count has finished.
 */
@Composable
fun ShowScreen(ui: UiState, onDone: () -> Unit) {
    val result = requireNotNull(ui.result)
    val starter = requireNotNull(ui.starter)
    var handIndex by remember { mutableIntStateOf(0) }
    var itemIndex by remember { mutableIntStateOf(-1) }
    val count = result.counts[handIndex]
    val item = count.items.getOrNull(itemIndex)
    val soFar = count.items.take(itemIndex + 1).sumOf { it.points }
    val shown by animateIntAsState(soFar, tween(240), label = "count total")
    val finished = itemIndex >= count.items.size
    val lastHand = handIndex == result.counts.size - 1
    val running = listOf(YOU, COMPUTER).map { seat ->
        val earlier = result.counts.take(handIndex).filter { it.seat == seat }.sumOf { it.points }
        val current = if (count.seat == seat) soFar else 0
        minOf(ui.scores[seat] + earlier + current, GAME_TARGET)
    }

    LaunchedEffect(handIndex) {
        repeat(count.items.size + 1) {
            delay(COUNT_BEAT_MS)
            itemIndex++
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Eyebrow("The show · ${handIndex + 1} of 3")
        Txt(countTitle(count), Modifier.padding(top = 8.dp), size = 32, family = Mono, weight = FontWeight.Light)
        if (ui.fullGame) ScoreStrip(running, Modifier.padding(top = 14.dp))
        Gap(22)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = (maxWidth - 8.dp * 3 - 16.dp) / 5
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                count.cards.forEach { card ->
                    CardFace(card, width, dimmed = item != null && card !in item.cards)
                }
                Spacer(Modifier.width(0.dp))
                CardFace(starter, width, dimmed = item != null && starter !in item.cards)
            }
        }
        Eyebrow("Starter on the right", Modifier.padding(top = 10.dp))

        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt("$shown", size = 76, family = Mono, weight = FontWeight.Light, lineHeight = 82)
            Txt(
                when {
                    item != null -> "${item.label} for ${item.points}"
                    !finished -> "Counting…"
                    count.points == 0 -> "Nothing to count"
                    count.points == 1 -> "1 point"
                    else -> "${count.points} points"
                },
                size = 17,
                weight = FontWeight.SemiBold,
                color = if (item != null) Ink.Accent else if (finished) Ink.Text else Ink.Muted,
            )
        }
        Spacer(Modifier.weight(1f))

        ActionButton(
            label = when {
                !finished -> "Counting…"
                !lastHand -> "See ${countTitle(result.counts[handIndex + 1]).replaceFirstChar(Char::lowercase)}"
                else -> "See the stats"
            },
            enabled = finished,
            onClick = {
                if (lastHand) {
                    onDone()
                } else {
                    handIndex++
                    itemIndex = -1
                }
            },
        )
    }
}

@Composable
private fun MenuLink(onMenu: () -> Unit) {
    Eyebrow(
        "Back to menu",
        Modifier.clickable(onClick = onMenu).padding(top = 14.dp, bottom = 6.dp),
        color = Ink.Accent,
    )
}

@Composable
fun SummaryScreen(ui: UiState, onNext: () -> Unit, onMenu: () -> Unit) {
    val result = requireNotNull(ui.result)
    val report = requireNotNull(ui.report)
    val graded = ui.plays.filter { !it.forced }
    val givenUp = report.pointsLost + graded.sumOf { it.pointsLost }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Eyebrow("Hand ${ui.handNumber} · Summary")
            if (ui.fullGame) ScoreStrip(result.scores, Modifier.padding(top = 14.dp))
            Row(Modifier.padding(top = 14.dp).fillMaxWidth()) {
                BigTotal("You", result.totals[YOU], Modifier.weight(0.3f))
                BigTotal("Computer", result.totals[COMPUTER], Modifier.weight(0.7f))
            }
            Gap(18)
            Breakdown(result, ui.youDeal)

            SectionTitle("Your decisions")
            Txt(points(givenUp), size = 40, family = Mono, weight = FontWeight.Light)
            Eyebrow("Expected points given up")
            Gap(14)
            Hairline()
            DecisionRow("Discard", report.chosen.discard, report.grade.label, report.pointsLost, gradeColor(report.grade))
            ui.plays.forEachIndexed { i, play ->
                DecisionRow(
                    label = "Card ${i + 1}",
                    cards = listOf(play.played.card),
                    grade = play.grade?.label ?: "Forced",
                    lost = play.pointsLost,
                    color = if (play.forced) Ink.Muted else gradeColor(play.grade),
                )
            }
            MenuLink(onMenu)
        }
        ActionButton(
            if (ui.gameOver) "See the final result" else "Next hand",
            Modifier.padding(20.dp),
            onClick = onNext,
        )
    }
}

private fun perfectLine(actual: GameScore, perfect: GameScore): String {
    val by = abs(perfect.margin)
    val before = abs(actual.margin)
    return when {
        actual.youWon && perfect.youWon -> "You'd still win, by $by instead of $before."
        perfect.youWon -> "Perfect play turns this loss into a win by $by."
        actual.youWon -> "Playing the odds would have lost this one by $by. Luck was on your side."
        else -> "Even perfect play loses this one, by $by instead of $before."
    }
}

@Composable
fun FinalScreen(ui: UiState, onNewGame: () -> Unit, onMenu: () -> Unit) {
    val actual = requireNotNull(ui.finalScore)
    val perfect = ui.perfect
    val discards = ui.history.sumOf { it.discard }
    val pegging = ui.history.sumOf { it.pegging }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Eyebrow("Game over · ${actual.hands} hands")
            Txt(
                if (actual.youWon) "You win" else "Computer wins",
                Modifier.padding(top = 8.dp),
                size = 32,
                family = Mono,
                weight = FontWeight.Light,
                color = if (actual.youWon) Ink.Accent else Ink.Text,
            )
            Row(Modifier.padding(top = 14.dp).fillMaxWidth()) {
                BigTotal("You", actual.you, Modifier.weight(0.5f))
                BigTotal("Computer", actual.computer, Modifier.weight(0.5f))
            }

            SectionTitle("With perfect play", "The same deals, taking the top-graded choice every time. The computer still responds to each card.")
            if (perfect == null) {
                Txt("Replaying your game…", color = Ink.Muted)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    BigTotal("You", perfect.you, Modifier.weight(0.5f))
                    BigTotal("Computer", perfect.computer, Modifier.weight(0.5f))
                }
                Txt(perfectLine(actual, perfect), Modifier.padding(top = 12.dp), size = 16, weight = FontWeight.SemiBold)
            }

            SectionTitle("Points given up", "Expected points lost against the top choice, across the game")
            Row(Modifier.fillMaxWidth()) {
                LossStat("Total", discards + pegging, Modifier.weight(0.34f))
                LossStat("Discards", discards, Modifier.weight(0.33f))
                LossStat("Pegging", pegging, Modifier.weight(0.33f))
            }
            Gap(14)
            Hairline()
            ui.history.forEachIndexed { i, loss ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                    Txt("Hand ${i + 1}", Modifier.weight(1f), size = 14, color = Ink.Muted)
                    Txt(points(loss.total), size = 14, family = Mono)
                }
                Hairline()
            }
            MenuLink(onMenu)
        }
        ActionButton("New game", Modifier.padding(20.dp), onClick = onNewGame)
    }
}

@Composable
private fun BigTotal(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt("$value", size = 56, family = Mono, weight = FontWeight.Light, lineHeight = 62)
        Eyebrow(label)
    }
}

@Composable
private fun LossStat(label: String, value: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(points(value), size = 26, family = Mono, weight = FontWeight.Light)
        Eyebrow(label, Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Breakdown(result: HandResult, youDeal: Boolean) {
    val dealer = if (youDeal) YOU else COMPUTER
    val hand = { seat: Int -> result.counts.firstOrNull { it.seat == seat && !it.crib }?.points ?: 0 }
    val crib = result.counts.firstOrNull { it.crib }?.points ?: 0
    val rows = buildList {
        add(Triple("Pegging", result.pegged[YOU], result.pegged[COMPUTER]))
        add(Triple("Hand", hand(YOU), hand(COMPUTER)))
        add(Triple("Crib", if (dealer == YOU) crib else 0, if (dealer == COMPUTER) crib else 0))
        if (result.heels > 0) {
            add(Triple("His heels", if (dealer == YOU) 2 else 0, if (dealer == COMPUTER) 2 else 0))
        }
    }
    Hairline()
    rows.forEach { (label, you, computer) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Txt("$you", Modifier.weight(0.3f), size = 14, family = Mono)
            Txt("$computer", Modifier.weight(0.3f), size = 14, family = Mono)
            Txt(label, Modifier.weight(0.4f), size = 14, color = Ink.Muted)
        }
        Hairline()
    }
}

@Composable
private fun DecisionRow(
    label: String,
    cards: List<Card>,
    grade: String,
    lost: Double,
    color: androidx.compose.ui.graphics.Color,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, Modifier.padding(end = 12.dp), size = 14, color = Ink.Muted)
            cards.forEach {
                MiniCard(it)
                Spacer(Modifier.padding(horizontal = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            Txt(grade, size = 14, weight = FontWeight.SemiBold, color = color)
            Txt(
                if (lost > 0) "  −${points(lost)}" else "",
                size = 13,
                family = Mono,
                color = Ink.Muted,
            )
        }
        Hairline()
    }
}

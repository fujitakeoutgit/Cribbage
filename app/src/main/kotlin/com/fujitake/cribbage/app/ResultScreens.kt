package com.fujitake.cribbage.app

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import com.fujitake.cribbage.engine.HandCount
import com.fujitake.cribbage.engine.HandResult
import com.fujitake.cribbage.engine.YOU

private fun countTitle(count: HandCount): String = when {
    count.crib && count.seat == YOU -> "Your crib"
    count.crib -> "Computer's crib"
    count.seat == YOU -> "Your hand"
    else -> "Computer's hand"
}

/**
 * Walks the show one combination at a time, non-dealer first. A step is either a hand's opening
 * view or one scoring item, and each hand always shows at least its opening view.
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
    val lastItem = itemIndex >= count.items.size - 1
    val lastHand = handIndex == result.counts.size - 1

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Eyebrow("The show · ${handIndex + 1} of ${result.counts.size}")
        Txt(countTitle(count), Modifier.padding(top = 8.dp), size = 32, family = Mono, weight = FontWeight.Light)
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
                    count.items.isEmpty() -> "Nothing to count"
                    item == null -> "Tap to count"
                    else -> "${item.label} for ${item.points}"
                },
                size = 17,
                weight = FontWeight.SemiBold,
                color = if (item != null) Ink.Accent else Ink.Muted,
            )
        }
        Spacer(Modifier.weight(1f))

        ActionButton(
            label = when {
                !lastItem -> "Next"
                !lastHand -> "Count ${countTitle(result.counts[handIndex + 1]).replaceFirstChar(Char::lowercase)}"
                else -> "See the summary"
            },
            onClick = {
                when {
                    !lastItem -> itemIndex++
                    !lastHand -> {
                        handIndex++
                        itemIndex = -1
                    }
                    else -> onDone()
                }
            },
        )
    }
}

@Composable
fun SummaryScreen(ui: UiState, onNext: () -> Unit) {
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
        }
        ActionButton("Next hand", Modifier.padding(20.dp), onClick = onNext)
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
private fun Breakdown(result: HandResult, youDeal: Boolean) {
    val dealer = if (youDeal) YOU else COMPUTER
    val hand = { seat: Int -> result.counts.first { it.seat == seat && !it.crib }.points }
    val crib = result.counts.first { it.crib }.points
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

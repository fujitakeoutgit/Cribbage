package com.fujitake.cribbage.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fujitake.cribbage.engine.Card
import com.fujitake.cribbage.engine.DiscardEval
import com.fujitake.cribbage.engine.DiscardReport
import com.fujitake.cribbage.engine.Grade
import com.fujitake.cribbage.engine.KeepChances
import com.fujitake.cribbage.engine.ScoreItem
import com.fujitake.cribbage.engine.StarterOutcome
import java.util.Locale
import kotlin.math.roundToInt

private const val TOP_ROWS = 5

fun points(value: Double) = String.format(Locale.ROOT, "%.2f", value)
fun signed(value: Double) = (if (value < 0) "−" else "+") + points(kotlin.math.abs(value))
fun percent(chance: Double) = String.format(Locale.ROOT, "%.1f%%", chance * 100)

@Composable
fun ReviewScreen(ui: UiState, onContinue: () -> Unit) {
    val report = requireNotNull(ui.report)
    var showAll by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            item { Verdict(ui, report) }
            item {
                SectionTitle("Discards ranked", "Expected points for this hand: hand, crib, pegging")
                HeaderRow()
            }
            val rows = report.options.withIndex().toList().let { all ->
                if (showAll) all
                else all.take(TOP_ROWS) + all.drop(TOP_ROWS).filter { it.value == report.chosen }
            }
            items(rows.size) { i ->
                val (index, option) = rows[i]
                OptionRow(
                    rank = index + 1,
                    option = option,
                    dealer = ui.youDeal,
                    best = option.total(ui.youDeal),
                    mine = option == report.chosen,
                    modifier = Modifier.riseIn(i),
                )
            }
            item {
                Eyebrow(
                    if (showAll) "Show top $TOP_ROWS" else "Show all 15",
                    Modifier.clickable { showAll = !showAll }.padding(vertical = 14.dp),
                    color = Ink.Accent,
                )
            }
            item { Chances(report.chances) }
        }
        ActionButton("Cut the starter", Modifier.padding(20.dp), onClick = onContinue)
    }
}

@Composable
private fun Verdict(ui: UiState, report: DiscardReport) {
    val best = report.options.first()
    val top = report.grade == Grade.BEST
    Column {
        Eyebrow("Hand ${ui.handNumber} · ${cribOwner(ui)}")
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Txt(
                report.grade.label,
                size = 32,
                family = Mono,
                weight = FontWeight.Light,
                color = gradeColor(report.grade),
            )
            Spacer(Modifier.weight(1f))
            Txt("#${report.rank} of 15", size = 15, family = Mono, color = Ink.Muted)
        }
        Txt(
            if (report.rank == 1) "You found the top discard."
            else if (top) "Tied with the top discard, within ${points(0.05)} points."
            else "You gave up ${points(report.pointsLost)} expected points.",
            Modifier.padding(top = 4.dp),
            color = Ink.Muted,
        )
        Gap(18)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThrowCard("You threw", report.chosen.discard, Modifier.weight(1f))
            if (report.rank != 1) ThrowCard("Best throw", best.discard, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThrowCard(label: String, cards: List<Card>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Eyebrow(label)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEach { CardFace(it, 54.dp) }
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 30.dp, bottom = 10.dp)) {
        Txt(title, size = 19, weight = FontWeight.SemiBold)
        if (subtitle != null) Txt(subtitle, Modifier.padding(top = 2.dp), size = 13, color = Ink.Muted)
    }
}

private val NumberColumn = 48.dp

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Eyebrow("Throw", Modifier.weight(1f))
        listOf("Hand", "Crib", "Peg", "Net").forEach {
            Box(Modifier.width(NumberColumn), contentAlignment = Alignment.CenterEnd) { Eyebrow(it) }
        }
    }
    Hairline()
}

@Composable
fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))

@Composable
private fun OptionRow(
    rank: Int,
    option: DiscardEval,
    dealer: Boolean,
    best: Double,
    mine: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (mine) Ink.Accent else Ink.Text
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("$rank", Modifier.width(26.dp), size = 13, family = Mono, color = Ink.Muted)
            Row(Modifier.weight(1f)) {
                option.discard.forEachIndexed { i, card ->
                    if (i > 0) Spacer(Modifier.width(8.dp))
                    MiniCard(card, tint)
                }
            }
            Num(points(option.handAverage), Ink.Text)
            Num(signed(if (dealer) option.cribAverage else -option.cribAverage).replace("+", ""), Ink.Text)
            Num("≈" + signed(option.peggingAverage).replace("+", ""), Ink.Muted)
            Num(points(best), tint, FontWeight.Bold)
        }
        Hairline()
    }
}

@Composable
private fun Num(text: String, color: Color, weight: FontWeight = FontWeight.Normal) {
    Txt(text, Modifier.width(NumberColumn), size = 12, family = Mono, color = color, weight = weight, align = TextAlign.End)
}

/** A card written inline as rank and suit. */
@Composable
fun MiniCard(card: Card, color: Color = Ink.Text, size: Int = 14) {
    val red = card.suit == 1 || card.suit == 2
    Row(verticalAlignment = Alignment.CenterVertically) {
        Txt(card.rankLabel, size = size, family = Mono, weight = FontWeight.Bold, color = color)
        SuitGlyph(card.suit, size.dp - 2.dp, Modifier.padding(start = 2.dp), color = if (red) Ink.Red else Ink.Text)
    }
}

@Composable
private fun Chances(chances: KeepChances) {
    SectionTitle("Your chances", "The four you kept, over all 46 possible starters")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chances.keep.forEach { CardFace(it, 54.dp) }
    }
    Gap(16)
    Row(Modifier.fillMaxWidth()) {
        Stat("Guaranteed", "${chances.guaranteed}", Modifier.weight(1f))
        Stat("Average", points(chances.average), Modifier.weight(1f))
        Stat("Best case", "${chances.best}", Modifier.weight(1f))
    }
    Gap(20)
    Eyebrow("Final hand score, chance of each")
    Histogram(chances)
    Eyebrow("Chance of reaching", Modifier.padding(top = 22.dp, bottom = 8.dp))
    AtLeast(chances)
    Eyebrow("Starter by starter", Modifier.padding(top = 22.dp, bottom = 6.dp))
    Hairline()
    chances.outcomes.forEach { StarterRow(it) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(value, size = 26, family = Mono, weight = FontWeight.Light)
        Eyebrow(label, Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Histogram(chances: KeepChances) {
    val bars = chances.distribution.withIndex().filter { it.value > 0 }
    val tallest = bars.maxOf { it.value }
    val motion = LocalMotion.current
    val grow = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { (score, count) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Txt("${(count * 100f / 46).roundToInt()}%", size = 10, family = Mono, color = Ink.Muted)
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .fillMaxWidth()
                        .height(112.dp * (count.toFloat() / tallest) * grow.value)
                        .background(Ink.Accent, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                )
                Txt("$score", Modifier.padding(top = 5.dp), size = 12, family = Mono)
            }
        }
    }
}

@Composable
private fun AtLeast(chances: KeepChances) {
    val targets = listOf(4, 6, 8, 10, 12, 16, 20, 24).filter { it <= chances.best && it > chances.guaranteed }
    if (targets.isEmpty()) {
        Txt("This hand scores the same whatever the starter.", size = 14, color = Ink.Muted)
        return
    }
    Row(Modifier.fillMaxWidth()) {
        targets.take(4).forEach { score ->
            Column(Modifier.weight(1f)) {
                Txt(percent(chances.chanceOfAtLeast(score)), size = 16, family = Mono)
                Eyebrow("$score or more", Modifier.padding(top = 2.dp))
            }
        }
    }
}

private fun gainsText(gains: List<ScoreItem>): String =
    if (gains.isEmpty()) "Adds nothing"
    else gains.groupBy { it.label }.entries.joinToString(", ") { (label, items) ->
        if (items.size > 1) "$label ×${items.size}" else label
    }

@Composable
private fun StarterRow(outcome: StarterOutcome) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (outcome.cards.size == 1) {
                    MiniCard(outcome.cards.single())
                } else {
                    Txt(
                        "Any ${outcome.cards.first().rankLabel}" +
                            if (outcome.cards.size in 2..3) " (${outcome.cards.size})" else "",
                        size = 14,
                        family = Mono,
                        weight = FontWeight.Bold,
                    )
                }
                Txt(gainsText(outcome.gains), Modifier.padding(top = 1.dp), size = 12, color = Ink.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Txt("${outcome.score} pts", size = 15, family = Mono, weight = FontWeight.Bold)
                Txt(
                    "${outcome.cards.size} of 46 · ${percent(outcome.chance)}",
                    size = 11,
                    family = Mono,
                    color = Ink.Muted,
                )
            }
        }
        Hairline()
    }
}

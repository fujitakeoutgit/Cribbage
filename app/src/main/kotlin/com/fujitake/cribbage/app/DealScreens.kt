package com.fujitake.cribbage.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.fujitake.cribbage.engine.COMPUTER
import com.fujitake.cribbage.engine.Card
import com.fujitake.cribbage.engine.GAME_TARGET
import com.fujitake.cribbage.engine.YOU

fun cribOwner(ui: UiState) = if (ui.youDeal) "Your crib" else "Computer's crib"

@Composable
private fun ScoreLine(label: String, score: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(label, Modifier.width(84.dp))
        Box(Modifier.weight(1f).height(4.dp).background(Ink.Hairline, RoundedCornerShape(2.dp))) {
            if (score > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(score.toFloat() / GAME_TARGET)
                        .height(4.dp)
                        .background(color, RoundedCornerShape(2.dp)),
                )
            }
        }
        Txt("$score", Modifier.width(40.dp), size = 14, family = Mono, weight = FontWeight.Bold, align = TextAlign.End)
    }
}

/** The running game score, racing to the target. */
@Composable
fun ScoreStrip(scores: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScoreLine("You", scores[YOU], Ink.Accent)
        ScoreLine("Computer", scores[COMPUTER], Ink.Muted)
    }
}

@Composable
fun CutScreen(ui: UiState, onDeal: () -> Unit) {
    val cut = requireNotNull(ui.cut)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Eyebrow("New game")
        Txt("Cut for deal", Modifier.padding(top = 8.dp), size = 32, family = Mono, weight = FontWeight.Light)
        Txt("The lower card deals first, then the deal alternates.", Modifier.padding(top = 6.dp), color = Ink.Muted)

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlipCard(cut.yours, 118.dp, delayMs = 250)
                Eyebrow("You", Modifier.padding(top = 12.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlipCard(cut.computers, 118.dp, delayMs = 500)
                Eyebrow("Computer", Modifier.padding(top = 12.dp))
            }
        }
        Spacer(Modifier.weight(1f))

        Column(Modifier.riseIn(10)) {
            Txt(if (ui.youDeal) "You deal." else "The computer deals.", size = 20, weight = FontWeight.SemiBold)
            Txt(
                if (ui.youDeal) "The crib is yours. The computer leads the play."
                else "The crib is the computer's. You lead the play.",
                Modifier.padding(top = 4.dp, bottom = 20.dp),
                color = Ink.Muted,
            )
            ActionButton("Deal six cards", onClick = onDeal)
        }
    }
}

@Composable
fun DiscardScreen(ui: UiState, onToggle: (Card) -> Unit, onThrow: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Eyebrow("Hand ${ui.handNumber} · ${cribOwner(ui)}")
        Txt(
            "Throw two",
            Modifier.padding(top = 8.dp),
            size = 32,
            family = Mono,
            weight = FontWeight.Light,
        )
        Txt(
            if (ui.youDeal) "They go to your crib, so they score for you."
            else "They go to the computer's crib, so they score against you.",
            Modifier.padding(top = 6.dp),
            color = Ink.Muted,
        )
        if (ui.fullGame) ScoreStrip(ui.scores, Modifier.padding(top = 16.dp))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gap = 12.dp
            val fitWide = (maxWidth - gap * 2) / 3
            val fitTall = (maxHeight - gap - 24.dp) / 2 / 1.42f
            val width = min(min(fitWide, fitTall), 120.dp)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                ui.six.chunked(3).forEachIndexed { row, cards ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        cards.forEachIndexed { column, card ->
                            CardFace(
                                card = card,
                                width = width,
                                modifier = Modifier.dealIn(row * 3 + column),
                                selected = card in ui.selected,
                                onClick = { onToggle(card) },
                            )
                        }
                    }
                }
            }
        }

        ActionButton(
            label = when {
                ui.analysing -> "Weighing all 15 discards…"
                ui.selected.size == 2 -> "Throw to ${cribOwner(ui).lowercase()}"
                else -> "Pick ${2 - ui.selected.size} more"
            },
            enabled = ui.selected.size == 2 && !ui.analysing,
            onClick = onThrow,
        )
    }
}

/** A flat Spacer helper for vertical rhythm in scrolling screens. */
@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))

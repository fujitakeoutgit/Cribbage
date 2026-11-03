package com.fujitake.cribbage.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.fujitake.cribbage.engine.Card

fun cribOwner(ui: UiState) = if (ui.youDeal) "Your crib" else "Computer's crib"

@Composable
fun CutScreen(ui: UiState, onDeal: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Eyebrow("Hand ${ui.handNumber}")
        Txt("Cut for deal", Modifier.padding(top = 8.dp), size = 32, family = Mono, weight = FontWeight.Light)
        Txt("The lower card deals and owns the crib.", Modifier.padding(top = 6.dp), color = Ink.Muted)

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlipCard(ui.cut.yours, 118.dp, delayMs = 250)
                Eyebrow("You", Modifier.padding(top = 12.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlipCard(ui.cut.computers, 118.dp, delayMs = 500)
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

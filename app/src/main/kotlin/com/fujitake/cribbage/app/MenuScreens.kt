package com.fujitake.cribbage.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fujitake.cribbage.engine.COMPUTER
import com.fujitake.cribbage.engine.GAME_TARGET
import com.fujitake.cribbage.engine.YOU
import com.fujitake.cribbage.engine.cards

@Composable
private fun MenuRow(
    index: Int,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(Modifier.riseIn(index).alpha(if (enabled) 1f else 0.35f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("%02d".format(index), Modifier.width(38.dp), size = 12, family = Mono, color = Ink.Muted)
            Column(Modifier.weight(1f)) {
                Txt(title, size = 24, family = Mono, weight = FontWeight.Light)
                Txt(subtitle, Modifier.padding(top = 2.dp), size = 14, color = Ink.Muted)
            }
            Txt("→", size = 20, family = Mono, color = if (enabled) Ink.Accent else Ink.Muted)
        }
        Hairline()
    }
}

@Composable
fun MenuScreen(
    onYourCrib: () -> Unit,
    onTheirCrib: () -> Unit,
    onFullGame: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.weight(1f))
        Eyebrow("Coach", color = Ink.Accent)
        Txt("Cribbage", Modifier.padding(top = 6.dp), size = 52, family = Mono, weight = FontWeight.Light)
        Txt("Learn the best play on every hand.", Modifier.padding(top = 4.dp, bottom = 28.dp), color = Ink.Muted)
        Hairline()
        MenuRow(1, "Your crib", "Practice a hand as the dealer", onClick = onYourCrib)
        MenuRow(2, "Their crib", "Practice a hand as the non-dealer", onClick = onTheirCrib)
        MenuRow(3, "Full game", "Play the computer to $GAME_TARGET", onClick = onFullGame)
        MenuRow(4, "Profile", "Your rating, accuracy and progress", onClick = onProfile)
        MenuRow(5, "Settings", "Card colors, styles and stats reset", onClick = onSettings)
        Spacer(Modifier.weight(0.4f))
    }
}

@Composable
private fun BackLink(onBack: () -> Unit) {
    Eyebrow("← Menu", Modifier.clickable(onClick = onBack).padding(vertical = 8.dp), color = Ink.Accent)
}

@Composable
fun GameMenuScreen(ui: UiState, onNew: () -> Unit, onContinue: () -> Unit, onBack: () -> Unit) {
    val saved = ui.saved
    var confirming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackLink(onBack)
        Txt("Full game", Modifier.padding(top = 8.dp, bottom = 22.dp), size = 32, family = Mono, weight = FontWeight.Light)
        Hairline()
        MenuRow(
            index = 1,
            title = "New game",
            subtitle = if (confirming) "Tap again to replace your saved game" else "First to $GAME_TARGET",
            onClick = {
                if (saved == null || confirming) onNew() else confirming = true
            },
        )
        MenuRow(
            index = 2,
            title = "Continue",
            subtitle = if (saved != null) {
                "Hand ${saved.handNumber} · You ${saved.scores[YOU]}, Computer ${saved.scores[COMPUTER]}"
            } else {
                "No saved game"
            },
            enabled = saved != null && !ui.busy,
            onClick = onContinue,
        )
    }
}

@Composable
private fun StyleChip(label: String, swatch: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) Ink.Accent else Ink.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).background(swatch, RoundedCornerShape(4.dp)).border(1.dp, Ink.Hairline, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Txt(label, size = 14, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun <T> ChipGrid(options: List<T>, render: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option -> render(option, Modifier.weight(1f)) }
            }
        }
    }
}

/** Erasing stats takes two taps, so a stray tap can't wipe your history. */
@Composable
private fun ResetStats(hands: Int, onReset: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val enabled = hands > 0
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .border(1.dp, if (confirming) Ink.Red else Ink.Hairline, shape)
            .clickable(enabled = enabled) {
                if (confirming) {
                    confirming = false
                    onReset()
                } else {
                    confirming = true
                }
            }
            .padding(16.dp),
    ) {
        Txt(
            if (confirming) "Tap again to erase" else "Reset stats",
            size = 16,
            weight = FontWeight.SemiBold,
            color = if (enabled) Ink.Red else Ink.Muted,
        )
        Txt(
            when {
                !enabled -> "No stats yet"
                confirming -> "This erases $hands ${if (hands == 1) "hand" else "hands"} and resets your rating to 1000."
                else -> "Erase your rating and hand history. Your saved game and card styles stay."
            },
            Modifier.padding(top = 2.dp),
            size = 13,
            color = Ink.Muted,
        )
    }
}

@Composable
fun SettingsScreen(
    ui: UiState,
    onFace: (FaceStyle) -> Unit,
    onBack: (BackStyle) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackLink(onDone)
        Txt("Settings", Modifier.padding(top = 8.dp, bottom = 20.dp), size = 32, family = Mono, weight = FontWeight.Light)

        CompositionLocalProvider(LocalFaceStyle provides ui.face, LocalBackStyle provides ui.back) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cards("AS KH 9D 5C").forEach { CardFace(it, 62.dp) }
                CardBack(62.dp)
            }
        }

        Eyebrow("Card face", Modifier.padding(top = 26.dp, bottom = 10.dp))
        ChipGrid(FaceStyle.entries) { face, modifier ->
            StyleChip(face.label, face.face, face == ui.face, modifier) { onFace(face) }
        }

        Eyebrow("Card back", Modifier.padding(top = 26.dp, bottom = 10.dp))
        ChipGrid(BackStyle.entries) { back, modifier ->
            StyleChip(back.label, back.color, back == ui.back, modifier) { onBack(back) }
        }

        Eyebrow("Stats", Modifier.padding(top = 26.dp, bottom = 10.dp))
        ResetStats(ui.performance.records.size, onReset)
    }
}

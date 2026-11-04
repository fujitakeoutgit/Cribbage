package com.fujitake.cribbage.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CoachApp(model: CoachViewModel = viewModel()) {
    val ui by model.ui.collectAsState()
    val motion = LocalMotion.current
    BackHandler(enabled = ui.phase != Phase.MENU, onBack = model::toMenu)
    CompositionLocalProvider(LocalFaceStyle provides ui.face, LocalBackStyle provides ui.back) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.Ground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AnimatedContent(
                // The whole state is the target so an outgoing screen keeps the state it was showing.
                targetState = ui,
                contentKey = { it.handNumber to it.phase },
                transitionSpec = {
                    val time = if (motion) 320 else 0
                    (fadeIn(tween(time, delayMillis = time / 3)) + scaleIn(tween(time), initialScale = 0.98f))
                        .togetherWith(fadeOut(tween(time / 2)))
                },
                label = "phase",
            ) { state ->
                when (state.phase) {
                    Phase.MENU -> MenuScreen(
                        onYourCrib = { model.startDrill(youDeal = true) },
                        onTheirCrib = { model.startDrill(youDeal = false) },
                        onFullGame = model::showGameMenu,
                        onProfile = model::showProfile,
                        onSettings = model::showSettings,
                    )
                    Phase.GAME_MENU -> GameMenuScreen(state, model::newGame, model::continueGame, model::toMenu)
                    Phase.PROFILE -> ProfileScreen(state, model::toMenu)
                    Phase.SETTINGS -> SettingsScreen(state, model::setFace, model::setBack, model::resetStats, model::toMenu)
                    Phase.CUT -> CutScreen(state, model::deal)
                    Phase.DISCARD -> DiscardScreen(state, model::toggle, model::throwCards)
                    Phase.REVIEW -> ReviewScreen(state, model::cutStarter)
                    Phase.PEGGING -> PeggingScreen(state, model::play, model::dismissVerdict)
                    Phase.SHOW -> ShowScreen(state, model::toSummary)
                    Phase.SUMMARY -> SummaryScreen(state, model::nextHand, model::toMenu)
                    Phase.FINAL -> FinalScreen(state, model::newGame, model::toMenu)
                }
            }
        }
    }
}

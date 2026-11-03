package com.fujitake.cribbage.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fujitake.cribbage.engine.COMPUTER
import com.fujitake.cribbage.engine.Card
import com.fujitake.cribbage.engine.DealCut
import com.fujitake.cribbage.engine.DiscardReport
import com.fujitake.cribbage.engine.HandGame
import com.fujitake.cribbage.engine.HandResult
import com.fujitake.cribbage.engine.PeggingState
import com.fujitake.cribbage.engine.PlayReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Phase { CUT, DISCARD, REVIEW, PEGGING, SHOW, SUMMARY }

data class UiState(
    val phase: Phase = Phase.CUT,
    val handNumber: Int = 1,
    val cut: DealCut,
    val six: List<Card> = emptyList(),
    val selected: List<Card> = emptyList(),
    val analysing: Boolean = false,
    val report: DiscardReport? = null,
    val starter: Card? = null,
    val peg: PeggingState? = null,
    val verdict: PlayReport? = null,
    val plays: List<PlayReport> = emptyList(),
    val busy: Boolean = false,
    val result: HandResult? = null,
) {
    val youDeal: Boolean get() = cut.youDeal
}

class CoachViewModel : ViewModel() {
    private val random = Random.Default
    private val firstCut = DealCut.draw(random)
    private var game = HandGame(random, firstCut)

    private val _ui = MutableStateFlow(UiState(cut = firstCut, six = game.yourSix))
    val ui: StateFlow<UiState> = _ui

    fun deal() = _ui.update { it.copy(phase = Phase.DISCARD) }

    fun toggle(card: Card) = _ui.update { state ->
        val selected = when {
            card in state.selected -> state.selected - card
            state.selected.size == 2 -> state.selected.drop(1) + card
            else -> state.selected + card
        }
        state.copy(selected = selected)
    }

    fun throwCards() {
        val two = _ui.value.selected
        if (two.size != 2 || _ui.value.analysing) return
        _ui.update { it.copy(analysing = true) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val report = withContext(Dispatchers.Default) { game.discard(two) }
            Log.i(TAG, "Discard analysis took ${System.currentTimeMillis() - started} ms")
            _ui.update { it.copy(analysing = false, report = report, phase = Phase.REVIEW) }
        }
    }

    fun cutStarter() {
        game.cutStarter()
        _ui.update {
            it.copy(starter = game.starter, peg = game.pegging, phase = Phase.PEGGING, busy = true)
        }
        viewModelScope.launch {
            delay(STARTER_BEAT_MS)
            runComputer()
        }
    }

    fun play(card: Card) {
        val state = _ui.value
        if (state.busy || state.verdict != null) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val report = withContext(Dispatchers.Default) { game.playYourCard(card) }
            Log.i(TAG, "Pegging verdict took ${System.currentTimeMillis() - started} ms")
            _ui.update { it.copy(peg = game.pegging, plays = it.plays + report) }
            if (report.forced) {
                delay(COMPUTER_BEAT_MS)
                runComputer()
            } else {
                _ui.update { it.copy(verdict = report, busy = false) }
            }
        }
    }

    fun dismissVerdict() {
        _ui.update { it.copy(verdict = null, busy = true) }
        viewModelScope.launch { runComputer() }
    }

    /** Plays computer cards until it's your turn or the hand is over. */
    private suspend fun runComputer() {
        while (!game.pegging.finished && game.pegging.toMove == COMPUTER) {
            delay(COMPUTER_BEAT_MS)
            withContext(Dispatchers.Default) { game.playComputerCard() }
            _ui.update { it.copy(peg = game.pegging) }
        }
        if (game.pegging.finished) {
            delay(END_BEAT_MS)
            _ui.update { it.copy(result = game.result(), phase = Phase.SHOW, busy = false) }
        } else {
            _ui.update { it.copy(busy = false) }
        }
    }

    fun toSummary() = _ui.update { it.copy(phase = Phase.SUMMARY) }

    fun nextHand() {
        val cut = DealCut.draw(random)
        game = HandGame(random, cut)
        _ui.update { UiState(cut = cut, six = game.yourSix, handNumber = it.handNumber + 1) }
    }

    private companion object {
        const val TAG = "CribbageCoach"
        const val COMPUTER_BEAT_MS = 750L
        const val STARTER_BEAT_MS = 1100L
        const val END_BEAT_MS = 1200L
    }
}

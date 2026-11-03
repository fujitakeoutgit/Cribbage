package com.fujitake.cribbage.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fujitake.cribbage.engine.COMPUTER
import com.fujitake.cribbage.engine.Card
import com.fujitake.cribbage.engine.DealCut
import com.fujitake.cribbage.engine.DiscardMemo
import com.fujitake.cribbage.engine.DiscardReport
import com.fujitake.cribbage.engine.DiscardTables
import com.fujitake.cribbage.engine.GameScore
import com.fujitake.cribbage.engine.HandLoss
import com.fujitake.cribbage.engine.HandResult
import com.fujitake.cribbage.engine.Match
import com.fujitake.cribbage.engine.PeggingState
import com.fujitake.cribbage.engine.PerfectPlay
import com.fujitake.cribbage.engine.PlayReport
import com.fujitake.cribbage.engine.SavedGame
import com.fujitake.cribbage.engine.YOU
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Phase { MENU, GAME_MENU, SETTINGS, CUT, DISCARD, REVIEW, PEGGING, SHOW, SUMMARY, FINAL }

enum class Mode { DRILL, FULL_GAME }

data class UiState(
    val phase: Phase = Phase.MENU,
    val mode: Mode = Mode.FULL_GAME,
    val handNumber: Int = 1,
    val cut: DealCut? = null,
    val youDeal: Boolean = false,
    val scores: List<Int> = listOf(0, 0),
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
    val history: List<HandLoss> = emptyList(),
    val finalScore: GameScore? = null,
    val perfect: GameScore? = null,
    val saved: SavedGame? = null,
    val face: FaceStyle = FaceStyle.IVORY,
    val back: BackStyle = BackStyle.GRAPHITE,
) {
    val fullGame: Boolean get() = mode == Mode.FULL_GAME
    val gameOver: Boolean get() = fullGame && result?.scores?.any { it >= com.fujitake.cribbage.engine.GAME_TARGET } == true
}

class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CoachStore(app)
    private val memo = DiscardMemo(DiscardTables.bundled)
    private lateinit var match: Match
    private val game get() = match.hand

    /** The running task for the current hand, stopped if you leave for the menu. */
    private var work: Job? = null

    private val _ui = MutableStateFlow(menuState())
    val ui: StateFlow<UiState> = _ui

    private fun menuState() = UiState(saved = store.savedGame, face = store.face, back = store.back)

    private fun launchWork(block: suspend CoroutineScope.() -> Unit) {
        work = viewModelScope.launch(block = block)
    }

    fun toMenu() {
        work?.cancel()
        _ui.value = menuState()
    }

    fun showGameMenu() = _ui.update { it.copy(phase = Phase.GAME_MENU, saved = store.savedGame) }

    fun showSettings() = _ui.update { it.copy(phase = Phase.SETTINGS) }

    fun setFace(face: FaceStyle) {
        store.face = face
        _ui.update { it.copy(face = face) }
    }

    fun setBack(back: BackStyle) {
        store.back = back
        _ui.update { it.copy(back = back) }
    }

    /** The state for the start of the current hand, keeping styles and game-long fields. */
    private fun dealtState(phase: Phase, mode: Mode) = _ui.value.copy(
        phase = phase,
        mode = mode,
        handNumber = match.handNumber,
        cut = match.cut,
        youDeal = game.youDeal,
        scores = match.scores,
        six = game.yourSix,
        selected = emptyList(),
        analysing = false,
        report = null,
        starter = null,
        peg = null,
        verdict = null,
        plays = emptyList(),
        busy = false,
        result = null,
    )

    fun startDrill(youDeal: Boolean) {
        match = Match(Random.nextLong(), memo, fixedYouDeal = youDeal)
        _ui.value = dealtState(Phase.DISCARD, Mode.DRILL).copy(history = emptyList(), finalScore = null, perfect = null)
    }

    fun newGame() {
        match = Match(Random.nextLong(), memo)
        _ui.value = dealtState(Phase.CUT, Mode.FULL_GAME).copy(history = emptyList(), finalScore = null, perfect = null)
        save()
    }

    fun continueGame() {
        val saved = store.savedGame ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        launchWork {
            val restored = withContext(Dispatchers.Default) { saved.restore(memo) }
            match = restored.match
            val base = dealtState(Phase.DISCARD, Mode.FULL_GAME)
                .copy(history = restored.history, finalScore = null, perfect = null)
            when {
                restored.report == null -> _ui.value = base
                !restored.starterCut -> _ui.value = base.copy(phase = Phase.REVIEW, report = restored.report)
                else -> {
                    _ui.value = base.copy(
                        phase = Phase.PEGGING,
                        report = restored.report,
                        starter = game.starter,
                        peg = game.pegging,
                        plays = restored.plays,
                        scores = game.scores,
                        busy = true,
                    )
                    runComputer()
                }
            }
        }
    }

    /** Records your decisions so far this hand. Drills aren't saved. */
    private fun save() {
        val state = _ui.value
        if (!state.fullGame) return
        store.savedGame = SavedGame(
            seed = match.seed,
            handNumber = match.handNumber,
            scores = match.scores,
            history = state.history,
            discard = state.report?.chosen?.discard.orEmpty(),
            starterCut = state.starter != null,
            plays = state.plays.map { it.played.card },
        )
    }

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
        launchWork {
            val started = System.currentTimeMillis()
            val report = withContext(Dispatchers.Default) { game.discard(two) }
            Log.i(TAG, "Discard analysis took ${System.currentTimeMillis() - started} ms")
            _ui.update { it.copy(analysing = false, report = report, phase = Phase.REVIEW) }
            save()
        }
    }

    fun cutStarter() {
        game.cutStarter()
        _ui.update {
            it.copy(
                starter = game.starter,
                peg = game.pegging,
                scores = game.scores,
                phase = Phase.PEGGING,
                busy = true,
            )
        }
        save()
        launchWork {
            delay(STARTER_BEAT_MS)
            runComputer()
        }
    }

    fun play(card: Card) {
        val state = _ui.value
        if (state.busy || state.verdict != null) return
        _ui.update { it.copy(busy = true) }
        launchWork {
            val started = System.currentTimeMillis()
            val report = withContext(Dispatchers.Default) { game.playYourCard(card) }
            Log.i(TAG, "Pegging verdict took ${System.currentTimeMillis() - started} ms")
            _ui.update { it.copy(peg = game.pegging, scores = game.scores, plays = it.plays + report) }
            save()
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
        launchWork { runComputer() }
    }

    /** Plays computer cards until it's your turn or the hand is over. */
    private suspend fun runComputer() {
        val hand = game
        while (hand.inPlay && hand.pegging.toMove == COMPUTER) {
            delay(COMPUTER_BEAT_MS)
            withContext(Dispatchers.Default) { hand.playComputerCard() }
            _ui.update { it.copy(peg = hand.pegging, scores = hand.scores) }
        }
        if (hand.inPlay) {
            _ui.update { it.copy(busy = false) }
        } else {
            delay(END_BEAT_MS)
            finishHand()
        }
    }

    private fun finishHand() {
        val result = game.result()
        match.finishHand()
        val state = _ui.value
        val loss = HandLoss(state.report?.pointsLost ?: 0.0, state.plays.sumOf { it.pointsLost })
        val history = state.history + loss
        if (state.fullGame) {
            store.savedGame = if (match.winner != null) {
                null
            } else {
                SavedGame(match.seed, match.handNumber + 1, match.scores, history, emptyList(), false, emptyList())
            }
        }
        _ui.update {
            it.copy(
                result = result,
                history = history,
                phase = if (result.counts.isEmpty()) Phase.SUMMARY else Phase.SHOW,
                busy = false,
            )
        }
    }

    fun toSummary() = _ui.update { it.copy(phase = Phase.SUMMARY) }

    fun nextHand() {
        if (_ui.value.gameOver) {
            showFinal()
            return
        }
        match.nextHand()
        _ui.value = dealtState(Phase.DISCARD, _ui.value.mode)
    }

    private fun showFinal() {
        val score = GameScore(match.scores[YOU], match.scores[COMPUTER], match.handNumber)
        _ui.update { it.copy(phase = Phase.FINAL, finalScore = score, perfect = null) }
        val seed = match.seed
        launchWork {
            val started = System.currentTimeMillis()
            val perfect = withContext(Dispatchers.Default) { PerfectPlay.replay(seed, memo) }
            Log.i(TAG, "Perfect replay took ${System.currentTimeMillis() - started} ms")
            _ui.update { it.copy(perfect = perfect) }
        }
    }

    private companion object {
        const val TAG = "CribbageCoach"
        const val COMPUTER_BEAT_MS = 750L
        const val STARTER_BEAT_MS = 1100L
        const val END_BEAT_MS = 1200L
    }
}

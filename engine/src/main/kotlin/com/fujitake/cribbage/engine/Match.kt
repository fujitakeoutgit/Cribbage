package com.fujitake.cribbage.engine

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

const val GAME_TARGET = 121

data class GameScore(val you: Int, val computer: Int, val hands: Int) {
    val margin: Int get() = you - computer
    val youWon: Boolean get() = you >= GAME_TARGET
}

/** Remembers discard rankings, so a replay of the same deals needn't work them out again. */
class DiscardMemo(val tables: DiscardTables) {
    private val rankings = ConcurrentHashMap<Pair<List<Card>, Boolean>, List<DiscardEval>>()

    fun rank(six: List<Card>, dealer: Boolean): List<DiscardEval> {
        val sorted = six.sorted()
        return rankings.getOrPut(sorted to dealer) { DiscardAnalysis.rank(sorted, dealer, tables) }
    }
}

/**
 * A game to [GAME_TARGET]. Every deal comes from [seed] and the hand number, so the same seed
 * always gives the same cards, which is what lets [PerfectPlay] replay a game.
 *
 * Set [fixedYouDeal] for a drill: the dealer never changes and scores never build up.
 */
class Match(
    val seed: Long,
    private val memo: DiscardMemo,
    private val fixedYouDeal: Boolean? = null,
    startHand: Int = 1,
    startScores: List<Int> = listOf(0, 0),
) {
    val cut: DealCut = DealCut.draw(Random(seed))

    var handNumber = startHand
        private set
    var scores: List<Int> = startScores
        private set
    var hand: HandGame = deal()
        private set

    /** The seat that has reached the target, once [finishHand] has been called. */
    val winner: Int? get() = scores.indexOfFirst { it >= GAME_TARGET }.takeIf { it >= 0 }

    private fun deal(): HandGame {
        val youDeal = fixedYouDeal ?: (cut.youDeal == (handNumber % 2 == 1))
        return HandGame(Random(seed + handNumber * DEAL_STRIDE), youDeal, scores, memo)
    }

    /** Banks the current hand's points, including any count at the show. */
    fun finishHand() {
        if (fixedYouDeal == null) scores = hand.result().scores
    }

    fun nextHand() {
        handNumber++
        hand = deal()
    }

    private companion object {
        const val DEAL_STRIDE = 1_000_003L
    }
}

object PerfectPlay {
    /**
     * Replays a game from its seed, taking the top-graded discard and card every time. The deals
     * are the same, and the computer responds to the new plays. The game can run longer or shorter
     * than the original, so this plays on through further seeded deals if it needs them.
     */
    fun replay(seed: Long, memo: DiscardMemo): GameScore {
        val match = Match(seed, memo)
        while (true) {
            val hand = match.hand
            hand.discard(hand.bestDiscard())
            hand.cutStarter()
            while (hand.inPlay) {
                if (hand.pegging.toMove == YOU) hand.playYourCard(hand.bestPlay()) else hand.playComputerCard()
            }
            match.finishHand()
            if (match.winner != null) {
                return GameScore(match.scores[YOU], match.scores[COMPUTER], match.handNumber)
            }
            match.nextHand()
        }
    }
}

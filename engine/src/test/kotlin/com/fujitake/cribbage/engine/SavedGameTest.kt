package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SavedGameTest {
    private val memo = DiscardMemo(DiscardTables.bundled)

    @Test fun `a save survives text`() {
        val saved = SavedGame(
            seed = -1234567890123L,
            handNumber = 7,
            scores = listOf(88, 102),
            history = listOf(HandLoss(0.25, 1.5), HandLoss(0.0, 0.0)),
            discard = cards("5H KD"),
            starterCut = true,
            plays = cards("7C 2S"),
        )
        assertEquals(saved, SavedGame.parse(saved.serialize()))
    }

    @Test fun `a save at the start of a hand survives text`() {
        val saved = SavedGame(9, 1, listOf(0, 0), emptyList(), emptyList(), false, emptyList())
        assertEquals(saved, SavedGame.parse(saved.serialize()))
    }

    @Test fun `unreadable saves are treated as no save`() {
        assertNull(SavedGame.parse(null))
        assertNull(SavedGame.parse(""))
        assertNull(SavedGame.parse("nonsense"))
        assertNull(SavedGame.parse("1;x;2;3,4;;;0;"))
        assertNull(SavedGame.parse("9;1;1;0,0;;;0;"))
    }

    @Test fun `resuming after any decision rebuilds the exact position`() {
        val rng = Random(71)
        repeat(8) { round ->
            val seed = 700L + round
            val match = Match(seed, memo)
            var history = emptyList<HandLoss>()
            repeat(3) {
                val hand = match.hand
                fun save(discard: List<Card>, cut: Boolean, plays: List<Card>) = SavedGame.parse(
                    SavedGame(seed, match.handNumber, match.scores, history, discard, cut, plays).serialize(),
                )!!.restore(memo)

                assertEquals(hand.yourSix, save(emptyList(), false, emptyList()).match.hand.yourSix)

                val two = hand.yourSix.shuffled(rng).take(2)
                val report = hand.discard(two)
                val afterDiscard = save(two, false, emptyList())
                assertEquals(report.rank, afterDiscard.report?.rank)

                hand.cutStarter()
                val played = mutableListOf<Card>()
                val reports = mutableListOf<PlayReport>()
                while (hand.inPlay) {
                    if (hand.pegging.toMove == COMPUTER) {
                        hand.playComputerCard()
                        continue
                    }
                    val card = hand.pegging.legal.random(rng)
                    reports += hand.playYourCard(card)
                    played += card

                    val resumed = save(two, true, played)
                    assertEquals(hand.pegging, resumed.match.hand.pegging)
                    assertEquals(hand.starter, resumed.match.hand.starter)
                    assertEquals(hand.scores, resumed.match.hand.scores)
                    assertEquals(reports, resumed.plays)
                }
                match.finishHand()
                history = history + HandLoss(report.pointsLost, reports.sumOf { r -> r.pointsLost })
                match.nextHand()
            }
        }
    }

    @Test fun `resuming mid-game finishes on the same score as playing straight through`() {
        val seed = 800L
        val straight = PerfectPlay.replay(seed, memo)

        val first = Match(seed, memo)
        first.hand.discard(first.hand.bestDiscard())
        first.hand.cutStarter()
        while (first.hand.inPlay) {
            val hand = first.hand
            if (hand.pegging.toMove == YOU) hand.playYourCard(hand.bestPlay()) else hand.playComputerCard()
        }
        first.finishHand()

        var match = SavedGame(seed, 2, first.scores, emptyList(), emptyList(), false, emptyList()).restore(memo).match
        while (match.winner == null) {
            val hand = match.hand
            hand.discard(hand.bestDiscard())
            hand.cutStarter()
            while (hand.inPlay) {
                if (hand.pegging.toMove == YOU) hand.playYourCard(hand.bestPlay()) else hand.playComputerCard()
            }
            match.finishHand()
            if (match.winner == null) match.nextHand()
        }
        assertEquals(straight.you, match.scores[YOU])
        assertEquals(straight.computer, match.scores[COMPUTER])
    }

    @Test fun `drills keep the dealer and never build a score`() {
        for (youDeal in listOf(true, false)) {
            val drill = Match(900, memo, fixedYouDeal = youDeal)
            repeat(4) {
                val hand = drill.hand
                assertEquals(youDeal, hand.youDeal)
                hand.discard(hand.bestDiscard())
                hand.cutStarter()
                while (hand.inPlay) {
                    if (hand.pegging.toMove == YOU) hand.playYourCard(hand.bestPlay()) else hand.playComputerCard()
                }
                drill.finishHand()
                assertEquals(listOf(0, 0), drill.scores)
                assertNull(drill.winner)
                drill.nextHand()
            }
        }
    }
}

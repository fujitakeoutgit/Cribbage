package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnalysisTest {
    private val tables = DiscardTables.bundled

    @Test fun `every split of six cards is ranked once, best first`() {
        val options = DiscardAnalysis.rank(cards("5H 5D JC 9S 2D 7C"), dealer = true, tables)
        assertEquals(15, options.size)
        assertEquals(15, options.map { it.discard }.distinct().size)
        options.zipWithNext().forEach { (a, b) -> assertTrue(a.total(true) >= b.total(true)) }
    }

    @Test fun `hand average matches a brute force over the 46 starters`() {
        val rng = Random(21)
        repeat(30) {
            val six = FULL_DECK.shuffled(rng).take(6).sorted()
            val eval = DiscardAnalysis.rank(six, dealer = false, tables, includePegging = false).random(rng)
            val starters = FULL_DECK.filter { c -> c !in six }
            assertEquals(46, starters.size)
            val expected = starters.sumOf { s ->
                Scoring.breakdown(eval.keep, s, false).sumOf { item -> item.points }
            } / 46.0
            assertEquals(expected, eval.handAverage, 1e-9)
        }
    }

    @Test fun `chances agree with the hand average and sum to one`() {
        val six = cards("5H 5D JC 9S 2D 7C")
        val keep = cards("5H 5D JC 9S")
        val chances = DiscardAnalysis.chances(keep, six)
        assertEquals(46, chances.distribution.sum())
        assertEquals(46, chances.outcomes.sumOf { it.cards.size })
        assertEquals(1.0, chances.chanceOfAtLeast(0), 1e-9)
        val eval = DiscardAnalysis.rank(six, true, tables, includePegging = false)
            .first { it.keep == keep.sorted() }
        assertEquals(eval.handAverage, chances.average, 1e-9)
    }

    @Test fun `a pair of fives is the strongest crib throw and sits in the published range`() {
        for (dealer in listOf(true, false)) {
            val options = DiscardAnalysis.rank(cards("5H 5D KC 9S 2D 7C"), dealer, tables, includePegging = false)
            val fives = options.first { it.discard == cards("5D 5H").sorted() }
            assertEquals(fives.cribAverage, options.maxOf { it.cribAverage }, 1e-9)
            assertTrue("5-5 crib ${fives.cribAverage}", fives.cribAverage in 7.5..10.0)
        }
    }

    @Test fun `the non-dealer avoids giving away fives`() {
        assertTrue(tables.throwWeight(true, 5, 5) > tables.throwWeight(false, 5, 5))
        assertTrue(tables.keepWeights(false)[5] > tables.keepWeights(false)[13])
    }

    @Test fun `opponent hand weights sum to one and respect a go`() {
        val unseen = PeggingSearch.unseenRanks(cards("5H 5D JC 9S 2D 7C"))
        val hands = opponentHands(unseen, 3, goLimit = 4, tables.keepWeights(true))
        assertEquals(1.0, hands.sumOf { it.weight }, 1e-9)
        assertTrue(hands.all { h -> h.ranks.all { rankValue(it) > 4 } })
    }

    @Test fun `a uniform opponent matches combinatorial card counts`() {
        val unseen = PeggingSearch.unseenRanks(cards("5H 5D 5S 9S 2D 7C"))
        val hands = opponentHands(unseen, 2, goLimit = -1, DoubleArray(14) { 1.0 })
        val total = 46.0 * 45 / 2
        assertTrue("only one five is left", hands.none { it.ranks.contentEquals(intArrayOf(5, 5)) })
        val tens = hands.single { it.ranks.contentEquals(intArrayOf(10, 10)) }
        assertEquals(6.0 / total, tens.weight, 1e-9)
    }

    @Test fun `search agrees with a naive game of the real rules`() {
        val rng = Random(22)
        repeat(300) {
            val deal = FULL_DECK.shuffled(rng).take(8).chunked(4)
            val leader = it % 2
            val solver = PegSolver(IntArray(0))
            solver.setHand(0, deal[0].map(Card::rank).toIntArray())
            solver.setHand(1, deal[1].map(Card::rank).toIntArray())
            val fast = solver.evaluate(leader, 15, 15, 0, 1 - leader)
            assertEquals("$deal", naive(PeggingState.start(deal, leader)), fast)
        }
    }

    private fun naive(state: PeggingState): Int {
        if (state.finished) return state.pegged[0] - state.pegged[1]
        val values = state.legal.map { naive(state.play(it)) }
        return if (state.toMove == 0) values.max() else values.min()
    }

    @Test fun `a thirty-one beats everything else`() {
        val view = SeatView(
            hand = cards("6H KD 2C"),
            dealer = false,
            seen = cards("6H KD 2C AD 3D KC JC QH"),
            series = cards("KC JC 5H"),
            count = 25,
            opponentCardsLeft = 2,
            opponentGoLimit = -1,
        )
        val options = PeggingSearch.evaluate(view, tables)
        assertEquals(card("6H"), options.first().card)
        assertEquals(2, options.first().pointsNow)
    }

    @Test fun `only legal cards are considered`() {
        val view = SeatView(cards("KH 2D"), true, cards("KH 2D KC JC QH 5H"), cards("KC JC QH"), 30, 2, -1)
        assertTrue(PeggingSearch.evaluate(view, tables).isEmpty())
    }

    @Test fun `grades follow the bands`() {
        assertEquals(Grade.BEST, Verdict.grade(0.04))
        assertEquals(Grade.GOOD, Verdict.grade(0.05))
        assertEquals(Grade.INACCURACY, Verdict.grade(0.25))
        assertEquals(Grade.MISTAKE, Verdict.grade(0.75))
    }

    @Test fun `desktop timings for the two worst cases`() {
        val rng = Random(23)
        var discardMs = 0L
        var leadMs = 0L
        repeat(10) {
            val six = FULL_DECK.shuffled(rng).take(6)
            var t = System.nanoTime()
            val best = DiscardAnalysis.rank(six, dealer = true, tables).first()
            discardMs += (System.nanoTime() - t) / 1_000_000
            val view = SeatView(best.keep, false, six, emptyList(), 0, 4, -1)
            t = System.nanoTime()
            PeggingSearch.evaluate(view, tables)
            leadMs += (System.nanoTime() - t) / 1_000_000
        }
        println("TIMING discard ${discardMs / 10} ms, opening lead ${leadMs / 10} ms")
    }
}

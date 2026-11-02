package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimulationTest {
    private val tables = DiscardTables.bundled

    @Test fun `random pegging keeps every rule across many hands`() {
        val rng = Random(31)
        repeat(100_000) { hand ->
            val deal = FULL_DECK.shuffled(rng).take(8).chunked(4)
            var state = PeggingState.start(deal, firstToMove = hand % 2)
            var moves = 0
            while (!state.finished) {
                assertTrue(state.legal.isNotEmpty())
                state = state.play(state.legal.random(rng))
                assertTrue(state.count <= 30)
                assertEquals(8, state.played.size + state.hands.sumOf { it.size })
                assertEquals(state.played.size, state.played.distinct().size)
                assertTrue(++moves <= 8)
            }
            assertEquals(8, moves)
            val earned = state.events.sumOf { e ->
                when (e) {
                    is PegEvent.Played -> e.points
                    is PegEvent.GoPoint, is PegEvent.LastCard -> 1
                    else -> 0
                }
            }
            assertEquals(earned, state.pegged.sum())
        }
    }

    @Test fun `full hands run from deal to count with real evaluators`() {
        val rng = Random(32)
        repeat(300) {
            val game = HandGame(rng, DealCut.draw(rng), tables)
            val report = game.discard(game.yourSix.shuffled(rng).take(2))
            assertEquals(15, report.options.size)
            assertTrue(report.rank in 1..15)
            assertTrue(report.pointsLost >= 0.0)
            assertEquals(4, game.crib.size)
            game.cutStarter()
            while (!game.pegging.finished) {
                if (game.pegging.toMove == YOU) {
                    val played = game.playYourCard(game.pegging.legal.random(rng))
                    assertTrue(played.pointsLost >= 0.0)
                } else {
                    game.playComputerCard()
                }
            }
            val result = game.result()
            assertEquals(3, result.counts.size)
            val counted = result.counts.flatMap { it.cards } + requireNotNull(game.starter)
            assertEquals(13, counted.distinct().size)
        }
    }

    private enum class Style { STRONG, GREEDY, RANDOM }

    /** Pegging margin per hand for STRONG against [rival], with seats swapped every hand. */
    private fun margin(rival: Style, hands: Int, seed: Int): Double {
        val rng = Random(seed)
        var total = 0L
        repeat(hands) { hand ->
            val strongSeat = hand % 2
            val deck = FULL_DECK.shuffled(rng)
            val dealerSeat = (hand / 2) % 2
            val sixes = listOf(deck.take(6), deck.drop(6).take(6))
            val evals = (0..1).map { seat ->
                DiscardAnalysis.rank(sixes[seat], seat == dealerSeat, tables, includePegging = false).first()
            }
            val starter = deck[12]
            var state = PeggingState.start(evals.map { it.keep }, firstToMove = 1 - dealerSeat)
            while (!state.finished) {
                val seat = state.toMove
                val style = if (seat == strongSeat) Style.STRONG else rival
                val chosen = when (style) {
                    Style.RANDOM -> state.legal.random(rng)
                    Style.GREEDY -> greedy(state)
                    Style.STRONG -> {
                        val view = SeatView.of(state, seat, seat == dealerSeat, evals[seat].discard, starter)
                        Computer.play(view, tables)
                    }
                }
                state = state.play(chosen)
            }
            total += state.pegged[strongSeat] - state.pegged[1 - strongSeat]
        }
        return total.toDouble() / hands
    }

    /** Takes the most points now, breaking ties on the highest card. */
    private fun greedy(state: PeggingState): Card = state.legal.maxWith(
        compareBy<Card> { candidate ->
            val series = state.series + candidate
            PegScore.points(IntArray(series.size) { series[it].rank }, 0, series.size, state.count + candidate.value)
        }.thenBy { it.value },
    )

    @Test fun `the computer clearly outpegs a random player`() {
        val margin = margin(Style.RANDOM, 2_000, 33)
        println("STRENGTH margin vs random $margin")
        assertTrue("margin $margin", margin > 1.0)
    }

    @Test fun `the computer clearly outpegs a greedy player`() {
        val margin = margin(Style.GREEDY, 2_000, 34)
        println("STRENGTH margin vs greedy $margin")
        assertTrue("margin $margin", margin > 0.3)
    }

    @Test fun `the computer's choice ignores your hidden cards`() {
        val computer = cards("7C 8D 9S 2H")
        val computerDiscard = cards("KC QC")
        val starter = card("5C")
        val startWith = { yours: String -> PeggingState.start(listOf(cards(yours), computer), firstToMove = 0) }

        // You lead the same card, but your three unplayed cards differ completely.
        val a = startWith("6H AD JD 4S").play(card("6H"))
        val b = startWith("6H KH 3D 9D").play(card("6H"))
        assertNotEquals(a.hands[YOU], b.hands[YOU])

        val viewA = SeatView.of(a, COMPUTER, true, computerDiscard, starter)
        val viewB = SeatView.of(b, COMPUTER, true, computerDiscard, starter)
        assertEquals(viewA, viewB)
        assertEquals(Computer.play(viewA, tables), Computer.play(viewB, tables))
    }

    @Test fun `grading your card ignores the computer's hidden cards`() {
        val yours = cards("6H AD JD 4S")
        val discards = cards("KC QC")
        val starter = card("5C")
        val a = PeggingState.start(listOf(yours, cards("7C 8D 9S 2H")), firstToMove = 0)
        val b = PeggingState.start(listOf(yours, cards("TC TD 3S AH")), firstToMove = 0)
        val options = listOf(a, b).map { state ->
            PeggingSearch.evaluate(SeatView.of(state, YOU, false, discards, starter), tables)
        }
        assertEquals(options[0], options[1])
    }
}

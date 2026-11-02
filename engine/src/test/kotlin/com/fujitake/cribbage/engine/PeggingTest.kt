package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PeggingTest {
    /** Seat 0 leads. Cards are played strictly in the order given by [plays]. */
    private fun run(seat0: String, seat1: String, plays: String): PeggingState {
        var state = PeggingState.start(listOf(cards(seat0), cards(seat1)), firstToMove = 0)
        for (c in cards(plays)) state = state.play(c)
        return state
    }

    private fun lastPoints(state: PeggingState) =
        state.events.filterIsInstance<PegEvent.Played>().last().points

    @Test fun `fifteen scores two`() =
        assertEquals(2, lastPoints(run("7H AS AC AD", "8D KS KC KD", "7H 8D")))

    @Test fun `a pair scores two`() =
        assertEquals(2, lastPoints(run("7H AS AC AD", "7D KS KC KD", "7H 7D")))

    @Test fun `pair royal scores six`() =
        assertEquals(6, lastPoints(run("4H 4D AC AD", "4C KS KC KD", "4H 4C 4D")))

    @Test fun `double pair royal scores twelve`() =
        assertEquals(12, lastPoints(run("2H 2D AC AD", "2C 2S KC KD", "2H 2C 2D 2S")))

    @Test fun `run counts in any order`() =
        assertEquals(3, lastPoints(run("4H 3D AC AD", "2C KS KC KD", "4H 2C 3D")))

    @Test fun `a run grows when extended out of order`() =
        assertEquals(4, lastPoints(run("4H 3D AC 9D", "2C 5S KC KD", "4H 2C 3D 5S")))

    @Test fun `a run must be the most recent cards`() =
        assertEquals(0, lastPoints(run("4H 2D AC AD", "2C 3S KC KD", "4H 2C 2D 3S")))

    @Test fun `a different rank ends a pair chain`() =
        assertEquals(0, lastPoints(run("4H 5D AC AD", "4C KS KC KD", "4H 4C 5D")))

    @Test fun `thirty one scores two and resets the count`() {
        val state = run("KH KD 2C 3C", "KS AS 4C 5C", "KH KS KD AS")
        assertEquals(2, lastPoints(state))
        assertEquals(0, state.count)
        assertTrue(state.series.isEmpty())
        assertEquals(0, state.toMove)
    }

    @Test fun `go gives one point to the last player`() {
        val state = run("KH TD 5C 6C", "QS 9S 4C 2C", "KH QS TD")
        assertEquals(1, state.pegged[0])
        assertEquals(0, state.count)
        assertEquals(1, state.toMove)
    }

    @Test fun `the other player keeps going after a go`() {
        val state = run("KH 5D 8C 6C", "KS 9S 4C AC", "KH KS 5D 4C")
        assertEquals(1, state.toMove)
        assertTrue(state.events.any { it is PegEvent.Go && it.seat == 0 })
        assertEquals(2, state.goLimit[0])
    }

    @Test fun `last card scores one`() {
        val state = run("KH", "9S", "KH 9S")
        assertTrue(state.finished)
        assertEquals(1, state.pegged[1])
    }

    @Test fun `no last card point after thirty one`() {
        val state = run("KH TD", "QS AS", "KH QS TD AS")
        assertTrue(state.finished)
        assertEquals(2, state.pegged[1])
        assertEquals(0, state.pegged[0])
    }

    @Test fun `only cards that fit under thirty one are legal`() {
        val state = run("KH KD 2C 3C", "KS AS 9C QD", "KH KS KD")
        assertEquals(listOf(card("AS")), state.legal)
        assertThrows(IllegalArgumentException::class.java) { state.play(card("9C")) }
    }
}

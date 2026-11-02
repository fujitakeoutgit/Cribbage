package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingTest {
    private fun eval(card: String, value: Double, now: Int = 0, risk: Double = 0.0) =
        CardEval(card(card), value, now, risk, 2.0)

    private fun report(vararg options: CardEval, played: Int = 0): PlayReport {
        val lost = options.first().value - options[played].value
        return PlayReport(options[played], options.toList(), lost, Verdict.grade(lost))
    }

    @Test fun `a missed score names both cards`() {
        val text = Coaching.explain(report(eval("7H", 2.0, now = 2), eval("3D", 1.0), played = 1))
        assertEquals("7♥ scores 2 now, and 3♦ scores 0.", text)
    }

    @Test fun `a risky card quotes both reply chances`() {
        val text = Coaching.explain(report(eval("4H", 1.0, risk = 0.1), eval("KD", 0.0, risk = 0.5), played = 1))
        assertEquals("K♦ allows a scoring reply 50% of the time, and 4♥ only 10%.", text)
    }

    @Test fun `no difference in now or risk points to the later sequence`() {
        val text = Coaching.explain(report(eval("4H", 1.0, risk = 0.2), eval("KD", 0.0, risk = 0.2), played = 1))
        assertTrue(text, text.endsWith("rest of the sequence."))
    }

    @Test fun `the best card that scores says so`() {
        val text = Coaching.explain(report(eval("7H", 2.0, now = 2), eval("3D", 1.0)))
        assertTrue(text, text.startsWith("Scores 2 now"))
    }

    @Test fun `the safest best card compares against the runner up`() {
        val text = Coaching.explain(report(eval("4H", 1.0, risk = 0.1), eval("KD", 0.5, risk = 0.4)))
        assertEquals("Safest card: a 10% chance of a scoring reply, against 40% for K♦.", text)
    }
}

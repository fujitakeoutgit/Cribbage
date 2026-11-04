package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PerformanceTest {
    private val memo = DiscardMemo(DiscardTables.bundled)

    private fun record(lost: Double, discardBest: Boolean = true, choices: Int = 2, best: Int = 2) =
        HandRecord(1_700_000_000_000, fullGame = false, youDealt = true, discardBest, choices, best, lost, 0.0)

    @Test fun `a record survives text`() {
        val r = HandRecord(1_762_000_000_123, true, false, false, 3, 1, 1.25, 0.5)
        assertEquals(r, HandRecord.parse(r.serialize()))
    }

    @Test fun `unreadable lines are skipped`() {
        assertNull(HandRecord.parse(""))
        assertNull(HandRecord.parse("garbage"))
        assertNull(HandRecord.parse("1,2,3"))
        val good = record(0.5).serialize()
        assertEquals(1, HandRecord.parseAll("junk\n$good\n\n1,x,0,0,0,0,0,0").size)
    }

    @Test fun `accuracy counts the discard and every real pegging choice`() {
        val r = record(lost = 1.0, discardBest = false, choices = 3, best = 2)
        assertEquals(4, r.decisions)
        assertEquals(2, r.best)
        val summary = Performance(listOf(r, record(0.0, discardBest = true, choices = 0, best = 0)))
        assertEquals(3.0 / 5, summary.accuracy, 1e-9)
        assertEquals(1.0 / 2, summary.discardAccuracy, 1e-9)
        assertEquals(2.0 / 3, summary.peggingAccuracy!!, 1e-9)
    }

    @Test fun `pegging accuracy is unknown until a real pegging choice`() {
        assertNull(Performance(listOf(record(0.0, choices = 0, best = 0))).peggingAccuracy)
    }

    @Test fun `a new player starts at the base rating`() {
        val empty = Performance(emptyList())
        assertEquals(Rating.START, empty.rating, 1e-9)
        assertTrue(empty.ratings.isEmpty())
        assertEquals(0.0, empty.accuracy, 1e-9)
    }

    @Test fun `clean hands raise the rating and costly ones lower it`() {
        assertTrue(Rating.next(Rating.START, 0.0) > Rating.START)
        assertTrue(Rating.next(Rating.START, 4.0) < Rating.START)
        assertEquals(Rating.START, Rating.next(Rating.START, 2.0), 1e-9)
    }

    @Test fun `gains shrink as the rating nears the level of play`() {
        val first = Rating.next(Rating.START, 0.4) - Rating.START
        val later = Performance(List(60) { record(0.4) })
        val lastStep = later.ratings.last() - later.ratings[later.ratings.size - 2]
        assertTrue(lastStep in 0.0..first)
        assertTrue("settles near 1400, got ${later.rating}", later.rating in 1350.0..1400.0)
    }

    @Test fun `one disaster can't sink the rating below zero`() {
        assertTrue(Performance(List(500) { record(40.0) }).rating >= 0.0)
    }

    @Test fun `rolling accuracy uses the most recent hands`() {
        val records = List(10) { record(0.0, discardBest = false, choices = 0, best = 0) } +
            List(10) { record(0.0, discardBest = true, choices = 0, best = 0) }
        val rolling = Performance(records).rollingAccuracy(window = 10)
        assertEquals(20, rolling.size)
        assertEquals(0.0, rolling[9], 1e-9)
        assertEquals(1.0, rolling.last(), 1e-9)
        assertEquals(0.5, rolling[14], 1e-9)
    }

    @Test fun `a record from a played hand matches its reports`() {
        val rng = Random(91)
        repeat(20) { i ->
            val hand = HandGame(Random(950L + i), youDeal = i % 2 == 0, startScores = listOf(0, 0), memo)
            val report = hand.discard(hand.yourSix.shuffled(rng).take(2))
            hand.cutStarter()
            val plays = mutableListOf<PlayReport>()
            while (hand.inPlay) {
                if (hand.pegging.toMove == YOU) plays += hand.playYourCard(hand.pegging.legal.random(rng))
                else hand.playComputerCard()
            }
            val r = HandRecord.from(report, plays, fullGame = false, youDealt = hand.youDeal, time = 5)
            val graded = plays.filter { !it.forced }
            assertEquals(1 + graded.size, r.decisions)
            assertEquals(
                (if (report.grade == Grade.BEST) 1 else 0) + graded.count { it.grade == Grade.BEST },
                r.best,
            )
            assertEquals(report.pointsLost + graded.sumOf { it.pointsLost }, r.pointsLost, 1e-9)
        }
    }
}

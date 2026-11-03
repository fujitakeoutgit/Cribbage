package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchTest {
    private val memo = DiscardMemo(DiscardTables.bundled)

    private fun playPegging(hand: HandGame, yourCard: (HandGame) -> Card) {
        hand.cutStarter()
        while (hand.inPlay) {
            if (hand.pegging.toMove == YOU) hand.playYourCard(yourCard(hand)) else hand.playComputerCard()
        }
    }

    /** Plays a whole game, returning the final score. */
    private fun playGame(
        seed: Long,
        yourDiscard: (HandGame) -> List<Card>,
        yourCard: (HandGame) -> Card,
    ): GameScore {
        val match = Match(seed, memo)
        while (true) {
            val hand = match.hand
            hand.discard(yourDiscard(hand))
            playPegging(hand, yourCard)
            match.finishHand()
            if (match.winner != null) return GameScore(match.scores[YOU], match.scores[COMPUTER], match.handNumber)
            match.nextHand()
        }
    }

    private fun randomGame(seed: Long): GameScore {
        val rng = Random(seed)
        return playGame(seed, { it.yourSix.shuffled(rng).take(2) }, { it.pegging.legal.random(rng) })
    }

    @Test fun `the deal alternates every hand`() {
        val match = Match(41, memo)
        val first = match.hand.youDeal
        assertEquals(match.cut.youDeal, first)
        repeat(6) { i ->
            assertEquals(if (i % 2 == 0) first else !first, match.hand.youDeal)
            match.nextHand()
        }
    }

    @Test fun `a seed always gives the same deals`() {
        val a = Match(42, memo)
        val b = Match(42, memo)
        assertEquals(a.hand.yourSix, b.hand.yourSix)
        a.nextHand()
        b.nextHand()
        assertEquals(a.hand.yourSix, b.hand.yourSix)
        assertNotEquals(Match(42, memo).hand.yourSix, a.hand.yourSix)
    }

    @Test fun `every game ends with one player on exactly the target`() {
        repeat(30) { i ->
            val score = randomGame(100L + i)
            assertEquals(GAME_TARGET, maxOf(score.you, score.computer))
            assertTrue(minOf(score.you, score.computer) < GAME_TARGET)
        }
    }

    @Test fun `pegging stops the moment someone reaches the target`() {
        repeat(40) { i ->
            val hand = HandGame(Random(200L + i), youDeal = i % 2 == 0, startScores = listOf(120, 120), memo)
            hand.discard(hand.yourSix.take(2))
            playPegging(hand) { it.pegging.legal.first() }
            assertTrue(hand.over)
            assertEquals(GAME_TARGET, hand.scores.max())
            assertEquals(120, hand.scores.min())
            assertTrue(hand.pegging.played.size <= 8)
            assertTrue(hand.result().counts.isEmpty())
        }
    }

    @Test fun `a jack starter can end the game for the dealer`() {
        val hand = generateSequence(300L) { it + 1 }
            .map { HandGame(Random(it), youDeal = true, startScores = listOf(119, 119), memo) }
            .first { it.nextStarterRank == 11 }
        hand.discard(hand.yourSix.take(2))
        hand.cutStarter()
        assertFalse(hand.inPlay)
        val result = hand.result()
        assertEquals(listOf(GAME_TARGET, 119), result.scores)
        assertTrue(result.counts.isEmpty())
    }

    @Test fun `the show stops at the combination that wins`() {
        var stoppedEarly = 0
        repeat(60) { i ->
            val start = listOf(108, 108)
            val hand = HandGame(Random(400L + i), youDeal = i % 2 == 0, startScores = start, memo)
            hand.discard(hand.yourSix.take(2))
            playPegging(hand) { it.pegging.legal.first() }
            if (!hand.over) {
                val result = hand.result()
                val running = hand.scores.toMutableList()
                var reachedAt = -1
                val items = result.counts.flatMap { c -> c.items.map { c.seat to it } }
                items.forEachIndexed { index, (seat, item) ->
                    assertEquals("nothing is counted after the win", -1, reachedAt)
                    running[seat] += item.points
                    if (running[seat] >= GAME_TARGET) reachedAt = index
                }
                if (reachedAt >= 0) {
                    stoppedEarly++
                    assertEquals(GAME_TARGET, result.scores.max())
                }
            }
        }
        assertTrue("expected some games to end in the show", stoppedEarly > 0)
    }

    @Test fun `the non-dealer counts first`() {
        val hand = HandGame(Random(5), youDeal = true, startScores = listOf(0, 0), memo)
        hand.discard(hand.yourSix.take(2))
        playPegging(hand) { it.pegging.legal.first() }
        val counts = hand.result().counts
        assertEquals(listOf(COMPUTER, YOU, YOU), counts.map { it.seat })
        assertEquals(listOf(false, false, true), counts.map { it.crib })
    }

    @Test fun `replay matches a game played on every top choice`() {
        repeat(3) { i ->
            val seed = 500L + i
            val played = playGame(seed, { it.bestDiscard() }, { it.bestPlay() })
            assertEquals(played, PerfectPlay.replay(seed, memo))
        }
    }

    @Test fun `replay is repeatable and leaves no shared state`() {
        val first = PerfectPlay.replay(77, memo)
        assertNotNull(first)
        assertEquals(first, PerfectPlay.replay(77, DiscardMemo(DiscardTables.bundled)))
    }

    @Test fun `perfect play outscores random play from the same deals`() {
        val seeds = 600L until 615L
        val random = seeds.sumOf { randomGame(it).margin }
        val perfect = seeds.sumOf { PerfectPlay.replay(it, memo).margin }
        println("GAMES margin random ${random / 15.0}, perfect ${perfect / 15.0}")
        assertTrue("random $random perfect $perfect", perfect > random + 15 * 20)
    }
}

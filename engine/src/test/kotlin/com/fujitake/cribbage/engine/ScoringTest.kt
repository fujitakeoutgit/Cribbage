package com.fujitake.cribbage.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ScoringTest {
    private fun score(hand: String, starter: String, crib: Boolean = false) =
        Scoring.score(cards(hand), card(starter), crib)

    @Test fun `the 29 hand`() = assertEquals(29, score("5H 5D 5C JS", "5S"))

    @Test fun `fifteens and pairs`() = assertEquals(12, score("7H 8D 7C 8S", "2H"))

    @Test fun `double double run`() = assertEquals(24, score("4H 4D 5C 6S", "6H"))

    @Test fun `double run with pairs and fifteens`() = assertEquals(24, score("7H 7D 8C 8S", "9H"))

    @Test fun `four of a kind with fifteens`() = assertEquals(24, score("3H 3D 3C 3S", "9H"))

    @Test fun `five card run that adds to fifteen`() = assertEquals(7, score("AH 2D 3C 4S", "5H"))

    @Test fun `all even cards with no pairs or runs score zero`() =
        assertEquals(0, score("2H 4D 6C 8S", "KH"))

    @Test fun `four card flush scores in a hand`() = assertEquals(4, score("2H 4H 6H 8H", "KS"))

    @Test fun `five card flush scores five in a hand`() = assertEquals(5, score("2H 4H 6H 8H", "KH"))

    @Test fun `four card flush does not score in a crib`() =
        assertEquals(0, score("2H 4H 6H 8H", "KS", crib = true))

    @Test fun `five card flush scores in a crib`() =
        assertEquals(5, score("2H 4H 6H 8H", "KH", crib = true))

    @Test fun `nobs needs the jack to match the starter suit`() {
        assertEquals(1, score("JH 2D 4C 8S", "KH"))
        assertEquals(0, score("JD 2D 4C 8S", "KH"))
    }

    @Test fun `a jack starter is not nobs`() = assertEquals(0, score("KH 2D 4C 8S", "JD"))

    @Test fun `breakdown matches the fast score on random hands`() {
        val rng = Random(11)
        repeat(50_000) {
            val deal = FULL_DECK.shuffled(rng).take(5)
            val hand = deal.take(4)
            val crib = it % 2 == 0
            val fast = Scoring.score(hand, deal[4], crib)
            val items = Scoring.breakdown(hand, deal[4], crib)
            assertEquals("$hand ${deal[4]}", fast, items.sumOf { item -> item.points })
        }
    }

    @Test fun `fast score matches an independent brute force count`() {
        val rng = Random(12)
        repeat(50_000) {
            val deal = FULL_DECK.shuffled(rng).take(5)
            assertEquals("$deal", bruteForce(deal), Scoring.score(deal.take(4), deal[4], false))
        }
    }

    /** Deliberately naive scorer, written separately from the engine. */
    private fun bruteForce(five: List<Card>): Int {
        val hand = five.take(4)
        val starter = five[4]
        var points = 0
        val subsets = (1 until 32).map { mask -> five.filterIndexed { i, _ -> mask shr i and 1 == 1 } }
        points += subsets.count { s -> s.sumOf { it.value } == 15 } * 2
        points += subsets.count { s -> s.size == 2 && s[0].rank == s[1].rank } * 2
        fun isRun(s: List<Card>): Boolean {
            val ranks = s.map { it.rank }.sorted()
            return ranks.zipWithNext().all { (a, b) -> b == a + 1 }
        }
        for (length in 5 downTo 3) {
            val runs = subsets.count { it.size == length && isRun(it) }
            if (runs > 0) {
                points += runs * length
                break
            }
        }
        if (hand.all { it.suit == hand[0].suit }) points += if (starter.suit == hand[0].suit) 5 else 4
        if (hand.any { it.rank == 11 && it.suit == starter.suit }) points += 1
        return points
    }
}

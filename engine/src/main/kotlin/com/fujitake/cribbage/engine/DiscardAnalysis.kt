package com.fujitake.cribbage.engine

import java.util.stream.IntStream

/** One way of playing a six-card hand, valued in expected points for the seat discarding. */
data class DiscardEval(
    val keep: List<Card>,
    val discard: List<Card>,
    val handAverage: Double,
    /** Expected crib score, always positive. It counts for you as dealer and against you if not. */
    val cribAverage: Double,
    val peggingAverage: Double,
) {
    fun total(dealer: Boolean): Double =
        handAverage + (if (dealer) cribAverage else -cribAverage) + peggingAverage
}

/** What a particular starter does for a kept hand. */
data class StarterOutcome(
    val rank: Int,
    /** Suits that give this score, as cards, for example all four fives. */
    val cards: List<Card>,
    val score: Int,
    /** Scoring combinations this starter is part of. */
    val gains: List<ScoreItem>,
) {
    val chance: Double get() = cards.size / 46.0
}

/** The chances for four kept cards over all 46 possible starters. */
data class KeepChances(
    val keep: List<Card>,
    val guaranteed: Int,
    val average: Double,
    val best: Int,
    /** Number of starters giving each score, indexed by score. */
    val distribution: IntArray,
    val outcomes: List<StarterOutcome>,
) {
    /** Chance of finishing with at least [score] points in hand. */
    fun chanceOfAtLeast(score: Int): Double =
        distribution.drop(score).sum() / 46.0

    override fun equals(other: Any?) = other is KeepChances && keep == other.keep
    override fun hashCode() = keep.hashCode()
}

object DiscardAnalysis {
    private val SPLITS: List<Pair<IntArray, IntArray>> = buildList {
        for (a in 0 until 6) for (b in a + 1 until 6) {
            val keep = (0 until 6).filter { it != a && it != b }.toIntArray()
            add(keep to intArrayOf(a, b))
        }
    }

    /** All 15 discards, best first. Set [includePegging] false for hand and crib only. */
    fun rank(
        six: List<Card>,
        dealer: Boolean,
        tables: DiscardTables,
        includePegging: Boolean = true,
    ): List<DiscardEval> {
        require(six.size == 6)
        val pegging = if (includePegging) PeggingEstimator(six, dealer, tables) else null
        val evals = IntStream.range(0, SPLITS.size).parallel().mapToObj { i ->
            val (keepSlots, discardSlots) = SPLITS[i]
            val keep = keepSlots.map { six[it] }
            val discard = discardSlots.map { six[it] }
            DiscardEval(
                keep = keep.sorted(),
                discard = discard.sorted(),
                handAverage = handAverage(keep, six),
                cribAverage = cribAverage(discard, six, dealer, tables),
                peggingAverage = pegging?.estimate(keep) ?: 0.0,
            )
        }.toList()
        return evals.sortedWith(
            compareByDescending<DiscardEval> { it.total(dealer) }.thenBy { it.discard.first() },
        )
    }

    private fun handAverage(keep: List<Card>, six: List<Card>): Double {
        val (a, b, c, d) = keep.map(Card::id)
        var total = 0
        var starters = 0
        for (s in 0 until 52) {
            if (six.any { it.id == s }) continue
            total += Scoring.score(a, b, c, d, s, false)
            starters++
        }
        return total.toDouble() / starters
    }

    /** Exact over every starter and opponent discard, weighted by how the opponent's seat throws. */
    private fun cribAverage(discard: List<Card>, six: List<Card>, dealer: Boolean, tables: DiscardTables): Double {
        val rest = (0 until 52).filter { id -> six.none { it.id == id } }.toIntArray()
        val a = discard[0].id
        val b = discard[1].id
        val opponentIsDealer = !dealer
        var total = 0.0
        var weightSum = 0.0
        for (s in rest.indices) {
            val starter = rest[s]
            for (i in rest.indices) {
                if (i == s) continue
                val x = rest[i]
                for (j in i + 1 until rest.size) {
                    if (j == s) continue
                    val y = rest[j]
                    val weight = tables.throwWeight(opponentIsDealer, rankOf(x), rankOf(y))
                    total += weight * Scoring.score(a, b, x, y, starter, true)
                    weightSum += weight
                }
            }
        }
        return total / weightSum
    }

    /** Starter-by-starter chances for four kept cards. [six] rules out the cards you've seen. */
    fun chances(keep: List<Card>, six: List<Card>): KeepChances {
        val starters = FULL_DECK.filter { it !in six }
        val scores = starters.associateWith { Scoring.score(keep, it, false) }
        val distribution = IntArray(30)
        scores.values.forEach { distribution[it]++ }
        val outcomes = starters
            .groupBy { it.rank to scores.getValue(it) }
            .map { (key, group) ->
                val (rank, score) = key
                val gains = Scoring.breakdown(keep, group.first(), false)
                    .filter { item -> group.first() in item.cards }
                StarterOutcome(rank, group, score, gains)
            }
            .sortedWith(compareByDescending<StarterOutcome> { it.score }.thenBy { it.rank })
        return KeepChances(
            keep = keep.sorted(),
            guaranteed = scores.values.min(),
            average = scores.values.average(),
            best = scores.values.max(),
            distribution = distribution,
            outcomes = outcomes,
        )
    }
}

/** Shares the opponent-hand list across all 15 options, since it depends only on the six cards. */
private class PeggingEstimator(six: List<Card>, private val dealer: Boolean, tables: DiscardTables) {
    private val candidates = opponentHands(
        unseen = PeggingSearch.unseenRanks(six),
        size = 4,
        goLimit = -1,
        keepWeights = tables.keepWeights(!dealer),
    )

    fun estimate(keep: List<Card>): Double = PeggingSearch.expectedDifferential(keep, candidates, dealer)
}

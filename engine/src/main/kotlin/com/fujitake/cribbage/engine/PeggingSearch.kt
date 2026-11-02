package com.fujitake.cribbage.engine

import java.util.stream.IntStream

/** One possible set of hidden opponent cards. Suits never matter while pegging, so only ranks. */
internal class OpponentHand(val ranks: IntArray, val weight: Double)

/** Every rank combination the opponent could hold, weighted by likelihood, summing to 1. */
internal fun opponentHands(
    unseen: IntArray,
    size: Int,
    goLimit: Int,
    keepWeights: DoubleArray,
): List<OpponentHand> {
    val found = mutableListOf<OpponentHand>()
    val chosen = IntArray(size)

    fun choose(rank: Int, filled: Int, weight: Double) {
        if (filled == size) {
            found += OpponentHand(chosen.copyOf(), weight)
            return
        }
        if (rank > 13) return
        choose(rank + 1, filled, weight)
        if (rankValue(rank) <= goLimit) return
        var ways = weight
        var taken = 0
        while (taken < unseen[rank] && filled + taken < size) {
            chosen[filled + taken] = rank
            taken++
            ways *= (unseen[rank] - taken + 1).toDouble() / taken * keepWeights[rank]
            choose(rank + 1, filled + taken, ways)
        }
    }

    choose(1, 0, 1.0)
    val total = found.sumOf { it.weight }
    return found.map { OpponentHand(it.ranks, it.weight / total) }
}

/**
 * Exhaustive search of the rest of a pegging sequence with both hands face up. Seat 0 maximises
 * seat 0's pegs minus seat 1's. Instances hold scratch state, so use one per thread.
 */
internal class PegSolver(pileRanks: IntArray) {
    private val hands = arrayOf(IntArray(4), IntArray(4))
    private val pile = IntArray(9)
    private val baseLength = pileRanks.size

    init {
        pileRanks.copyInto(pile)
    }

    fun setHand(seat: Int, ranks: IntArray) {
        ranks.copyInto(hands[seat])
    }

    /** Value of playing nothing further: `mover` acts next on the series in the pile. */
    fun evaluate(mover: Int, mask0: Int, mask1: Int, count: Int, lastPlayer: Int): Int =
        search(mover, mask0, mask1, count, 0, baseLength, lastPlayer)

    /** Value for seat 0 after `seat` plays the card in `slot`, including the points it scores. */
    fun afterPlay(seat: Int, slot: Int, mask0: Int, mask1: Int, count: Int): Int =
        play(seat, slot, mask0, mask1, count, 0, baseLength)

    private fun play(seat: Int, slot: Int, mask0: Int, mask1: Int, count: Int, start: Int, len: Int): Int {
        val rank = hands[seat][slot]
        val newCount = count + rankValue(rank)
        pile[len] = rank
        val points = PegScore.points(pile, start, len + 1, newCount)
        val signed = if (seat == 0) points else -points
        val bit = 1 shl slot
        val next0 = if (seat == 0) mask0 and bit.inv() else mask0
        val next1 = if (seat == 1) mask1 and bit.inv() else mask1
        return signed + if (newCount == 31) {
            search(1 - seat, next0, next1, 0, len + 1, len + 1, seat)
        } else {
            search(1 - seat, next0, next1, newCount, start, len + 1, seat)
        }
    }

    private fun canPlay(seat: Int, mask: Int, count: Int): Boolean {
        val hand = hands[seat]
        for (slot in 0 until 4) {
            if (mask ushr slot and 1 == 1 && count + rankValue(hand[slot]) <= 31) return true
        }
        return false
    }

    private fun search(mover: Int, mask0: Int, mask1: Int, count: Int, start: Int, len: Int, last: Int): Int {
        if (mask0 == 0 && mask1 == 0) {
            if (count == 0) return 0
            return if (last == 0) 1 else -1
        }
        val mask = if (mover == 0) mask0 else mask1
        val hand = hands[mover]
        var best = Int.MIN_VALUE
        var worst = Int.MAX_VALUE
        var triedRanks = 0
        for (slot in 0 until 4) {
            if (mask ushr slot and 1 == 0) continue
            val rank = hand[slot]
            if (count + rankValue(rank) > 31) continue
            if (triedRanks ushr rank and 1 == 1) continue
            triedRanks = triedRanks or (1 shl rank)
            val value = play(mover, slot, mask0, mask1, count, start, len)
            if (value > best) best = value
            if (value < worst) worst = value
        }
        if (triedRanks != 0) return if (mover == 0) best else worst

        val other = 1 - mover
        val otherMask = if (other == 0) mask0 else mask1
        if (canPlay(other, otherMask, count)) return search(other, mask0, mask1, count, start, len, last)
        val goPoint = if (last == 0) 1 else -1
        return goPoint + search(1 - last, mask0, mask1, 0, len, len, last)
    }
}

/** What one seat can see at the moment it must play. Nothing here reveals the opponent's cards. */
data class SeatView(
    val hand: List<Card>,
    val dealer: Boolean,
    /** Every card this seat has seen: hand, own discards, starter and all cards played. */
    val seen: List<Card>,
    val series: List<Card>,
    val count: Int,
    val opponentCardsLeft: Int,
    val opponentGoLimit: Int,
) {
    companion object {
        /** Builds a seat's view. [state]'s other hand is used only for its size. */
        fun of(state: PeggingState, seat: Int, dealer: Boolean, ownDiscards: List<Card>, starter: Card) = SeatView(
            hand = state.hands[seat],
            dealer = dealer,
            seen = (state.hands[seat] + ownDiscards + starter + state.played).distinct(),
            series = state.series,
            count = state.count,
            opponentCardsLeft = state.hands[1 - seat].size,
            opponentGoLimit = state.goLimit[1 - seat],
        )
    }
}

/** The value of one legal card, in expected pegging points versus the opponent. */
data class CardEval(
    val card: Card,
    val value: Double,
    val pointsNow: Int,
    /** Chance the opponent holds a reply that scores, then the points of the best such reply. */
    val replyChance: Double,
    val replyPoints: Double,
)

object PeggingSearch {
    /** Evaluates every legal card, best first. Equal cards keep a stable, lowest-id-first order. */
    fun evaluate(view: SeatView, tables: DiscardTables): List<CardEval> {
        val legal = view.hand.filter { view.count + it.value <= 31 }
        val unseen = unseenRanks(view.seen)
        val pile = IntArray(view.series.size) { view.series[it].rank }
        val keep = tables.keepWeights(!view.dealer)
        val candidates = opponentHands(unseen, view.opponentCardsLeft, view.opponentGoLimit, keep)
        val mine = view.hand.sorted().map(Card::rank).toIntArray()
        val cardsInOrder = view.hand.sorted()

        return legal.sorted().map { card ->
            val slot = cardsInOrder.indexOf(card)
            val value = expected(candidates, pile) { opp, solver ->
                solver.setHand(0, mine.paddedTo4())
                solver.setHand(1, opp.ranks.paddedTo4())
                solver.afterPlay(0, slot, (1 shl mine.size) - 1, (1 shl opp.ranks.size) - 1, view.count)
            }

            val nextCount = view.count + card.value
            val pilePlus = pile + card.rank
            val pointsNow = PegScore.points(pilePlus, 0, pilePlus.size, nextCount)
            val reply = if (nextCount == 31) ReplyRisk(0.0, 0.0) else replyRisk(candidates, pilePlus, nextCount)
            CardEval(card, value, pointsNow, reply.chance, reply.points)
        }.sortedWith(compareByDescending<CardEval> { it.value }.thenBy { it.card })
    }

    /** Expected pegging difference for a seat, if it keeps [keep], before any card is played. */
    internal fun expectedDifferential(keep: List<Card>, candidates: List<OpponentHand>, dealer: Boolean): Double {
        val mine = keep.map(Card::rank).toIntArray()
        // The non-dealer leads. "last" only matters once a card is down, so its start value is unused.
        val leader = if (dealer) 1 else 0
        return expected(candidates, IntArray(0)) { opp, solver ->
            solver.setHand(0, mine)
            solver.setHand(1, opp.ranks)
            solver.evaluate(leader, 15, 15, 0, 1 - leader)
        }
    }

    private fun expected(
        candidates: List<OpponentHand>,
        pile: IntArray,
        score: (OpponentHand, PegSolver) -> Int,
    ): Double = IntStream.range(0, candidates.size).parallel().mapToDouble { i ->
        val opp = candidates[i]
        opp.weight * score(opp, PegSolver(pile))
    }.sum()

    private class ReplyRisk(val chance: Double, val points: Double)

    private fun replyRisk(candidates: List<OpponentHand>, pile: IntArray, count: Int): ReplyRisk {
        val pointsByRank = IntArray(14)
        val scratch = pile + 0
        for (rank in 1..13) {
            val nextCount = count + rankValue(rank)
            if (nextCount > 31) continue
            scratch[scratch.size - 1] = rank
            pointsByRank[rank] = PegScore.points(scratch, 0, scratch.size, nextCount)
        }
        var chance = 0.0
        var points = 0.0
        for (opp in candidates) {
            val best = opp.ranks.maxOfOrNull { pointsByRank[it] } ?: 0
            if (best > 0) {
                chance += opp.weight
                points += opp.weight * best
            }
        }
        return ReplyRisk(chance, if (chance > 0) points / chance else 0.0)
    }

    private fun IntArray.paddedTo4() = IntArray(4) { i -> getOrElse(i) { 0 } }

    internal fun unseenRanks(seen: List<Card>): IntArray {
        val counts = IntArray(14) { if (it == 0) 0 else 4 }
        seen.forEach { counts[it.rank]-- }
        return counts
    }
}

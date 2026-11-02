package com.fujitake.cribbage.engine

/** Points for the card just played, shared by the game state and the search. */
internal object PegScore {
    /** Scores the card at `pile[len - 1]`. The current series is `pile[start until len]`, as ranks. */
    fun points(pile: IntArray, start: Int, len: Int, count: Int): Int =
        (if (count == 15 || count == 31) 2 else 0) + pairPoints(pile, start, len) + runPoints(pile, start, len)

    fun pairPoints(pile: IntArray, start: Int, len: Int): Int {
        val rank = pile[len - 1]
        var matches = 1
        var i = len - 2
        while (i >= start && pile[i] == rank) {
            matches++
            i--
        }
        return matches * (matches - 1)
    }

    fun runPoints(pile: IntArray, start: Int, len: Int): Int {
        var size = len - start
        while (size >= 3) {
            var mask = 0
            var low = 14
            var high = 0
            var distinct = true
            for (i in len - size until len) {
                val bit = 1 shl pile[i]
                if (mask and bit != 0) {
                    distinct = false
                    break
                }
                mask = mask or bit
                if (pile[i] < low) low = pile[i]
                if (pile[i] > high) high = pile[i]
            }
            if (distinct && high - low == size - 1) return size
            size--
        }
        return 0
    }
}

sealed interface PegEvent {
    /** A card went down. [points] covers fifteens, thirty-ones, pairs and runs. */
    data class Played(
        val seat: Int,
        val card: Card,
        val count: Int,
        val points: Int,
        val labels: List<String>,
    ) : PegEvent

    /** [seat] holds cards but none fit under thirty-one. */
    data class Go(val seat: Int, val count: Int) : PegEvent

    /** One point for the last card before a reset. */
    data class GoPoint(val seat: Int) : PegEvent

    /** One point for the final card of the hand. */
    data class LastCard(val seat: Int) : PegEvent

    /** The count went back to zero. */
    data object Reset : PegEvent
}

/**
 * The pegging phase as an immutable state. [play] resolves any goes and resets that follow, so
 * [toMove] is always a seat that has a legal card, unless [finished].
 */
data class PeggingState(
    val hands: List<List<Card>>,
    val series: List<Card>,
    val played: List<Card>,
    val count: Int,
    val toMove: Int,
    val lastPlayer: Int,
    val pegged: List<Int>,
    /** Per seat, the highest value that seat is known not to hold, or -1. */
    val goLimit: List<Int>,
    val events: List<PegEvent>,
) {
    val finished: Boolean get() = hands.all { it.isEmpty() }

    val legal: List<Card>
        get() = if (finished) emptyList() else hands[toMove].filter { count + it.value <= 31 }

    fun play(card: Card): PeggingState {
        require(card in legal) { "$card is not legal: $legal" }
        val seat = toMove
        val newSeries = series + card
        val newCount = count + card.value
        val ranks = IntArray(newSeries.size) { newSeries[it].rank }
        val pairs = PegScore.pairPoints(ranks, 0, ranks.size)
        val run = PegScore.runPoints(ranks, 0, ranks.size)
        val labels = buildList {
            if (newCount == 15) add("Fifteen")
            if (newCount == 31) add("Thirty-one")
            when (pairs) {
                2 -> add("Pair")
                6 -> add("Three of a kind")
                12 -> add("Four of a kind")
            }
            if (run > 0) add("Run of $run")
        }
        val points = PegScore.points(ranks, 0, ranks.size, newCount)
        val after = copy(
            hands = hands.mapIndexed { i, h -> if (i == seat) h - card else h },
            series = newSeries,
            played = played + card,
            count = newCount,
            lastPlayer = seat,
            pegged = pegged.mapIndexed { i, p -> if (i == seat) p + points else p },
            events = events + PegEvent.Played(seat, card, newCount, points, labels),
        )
        return if (newCount == 31) {
            after.copy(count = 0, series = emptyList(), events = after.events + PegEvent.Reset)
                .advance(1 - seat)
        } else {
            after.advance(1 - seat)
        }
    }

    private fun canPlay(seat: Int) = hands[seat].any { count + it.value <= 31 }

    private fun award(seat: Int, event: PegEvent) = copy(
        pegged = pegged.mapIndexed { i, p -> if (i == seat) p + 1 else p },
        events = events + event,
    )

    private fun noteGo(seat: Int): PeggingState {
        if (hands[seat].isEmpty()) return this
        return copy(
            goLimit = goLimit.mapIndexed { i, l -> if (i == seat) maxOf(l, 31 - count) else l },
            events = events + PegEvent.Go(seat, count),
        )
    }

    private fun advance(next: Int): PeggingState {
        if (finished) {
            return if (count == 0) copy(toMove = next) else award(lastPlayer, PegEvent.LastCard(lastPlayer))
        }
        if (canPlay(next)) return copy(toMove = next)
        val other = 1 - next
        if (canPlay(other)) return noteGo(next).copy(toMove = other)
        val noted = noteGo(next).noteGo(other)
        val scored = noted.award(lastPlayer, PegEvent.GoPoint(lastPlayer))
        return scored.copy(count = 0, series = emptyList(), events = scored.events + PegEvent.Reset)
            .advance(1 - lastPlayer)
    }

    companion object {
        fun start(hands: List<List<Card>>, firstToMove: Int) = PeggingState(
            hands = hands,
            series = emptyList(),
            played = emptyList(),
            count = 0,
            toMove = firstToMove,
            lastPlayer = 1 - firstToMove,
            pegged = listOf(0, 0),
            goLimit = listOf(-1, -1),
            events = emptyList(),
        )
    }
}

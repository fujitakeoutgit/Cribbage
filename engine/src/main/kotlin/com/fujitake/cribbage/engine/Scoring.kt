package com.fujitake.cribbage.engine

enum class ScoreKind { FIFTEEN, PAIR, RUN, FLUSH, NOBS }

/** One scoring combination, for showing a count step by step. */
data class ScoreItem(val kind: ScoreKind, val cards: List<Card>, val points: Int) {
    val label: String
        get() = when (kind) {
            ScoreKind.FIFTEEN -> "Fifteen"
            ScoreKind.PAIR -> "Pair"
            ScoreKind.RUN -> "Run of ${cards.size}"
            ScoreKind.FLUSH -> "Flush"
            ScoreKind.NOBS -> "Nobs"
        }
}

object Scoring {
    fun score(hand: List<Card>, starter: Card, crib: Boolean): Int =
        score(hand[0].id, hand[1].id, hand[2].id, hand[3].id, starter.id, crib)

    /** Allocation-free score of four cards plus a starter. Crib analysis calls this constantly. */
    fun score(a: Int, b: Int, c: Int, d: Int, s: Int, crib: Boolean): Int {
        val ra = rankOf(a); val rb = rankOf(b); val rc = rankOf(c); val rd = rankOf(d); val rs = rankOf(s)
        val va = rankValue(ra); val vb = rankValue(rb); val vc = rankValue(rc)
        val vd = rankValue(rd); val vs = rankValue(rs)
        val total = va + vb + vc + vd + vs
        var points = 0

        // A two-card subset is fifteen, or its three-card complement is.
        points += fifteen(va + vb, total) + fifteen(va + vc, total) + fifteen(va + vd, total)
        points += fifteen(va + vs, total) + fifteen(vb + vc, total) + fifteen(vb + vd, total)
        points += fifteen(vb + vs, total) + fifteen(vc + vd, total) + fifteen(vc + vs, total)
        points += fifteen(vd + vs, total)
        if (total - va == 15) points += 2
        if (total - vb == 15) points += 2
        if (total - vc == 15) points += 2
        if (total - vd == 15) points += 2
        if (total - vs == 15) points += 2
        if (total == 15) points += 2

        if (ra == rb) points += 2
        if (ra == rc) points += 2
        if (ra == rd) points += 2
        if (ra == rs) points += 2
        if (rb == rc) points += 2
        if (rb == rd) points += 2
        if (rb == rs) points += 2
        if (rc == rd) points += 2
        if (rc == rs) points += 2
        if (rd == rs) points += 2

        val mask = (1 shl ra) or (1 shl rb) or (1 shl rc) or (1 shl rd) or (1 shl rs)
        if (mask and (mask ushr 1) and (mask ushr 2) != 0) {
            // Rank counts packed into nibbles, so a run's multiplicity is a product of nibbles.
            val counts = (1L shl (ra * 4)) + (1L shl (rb * 4)) + (1L shl (rc * 4)) +
                (1L shl (rd * 4)) + (1L shl (rs * 4))
            points += runPoints(mask, counts)
        }

        val suit = suitOf(a)
        if (suitOf(b) == suit && suitOf(c) == suit && suitOf(d) == suit) {
            if (suitOf(s) == suit) points += 5 else if (!crib) points += 4
        }

        val starterSuit = suitOf(s)
        if ((ra == 11 && suitOf(a) == starterSuit) || (rb == 11 && suitOf(b) == starterSuit) ||
            (rc == 11 && suitOf(c) == starterSuit) || (rd == 11 && suitOf(d) == starterSuit)
        ) points += 1

        return points
    }

    private fun fifteen(pair: Int, total: Int): Int =
        (if (pair == 15) 2 else 0) + (if (total - pair == 15) 2 else 0)

    private fun runPoints(mask: Int, counts: Long): Int {
        var rank = 1
        while (rank <= 13) {
            if (mask ushr rank and 1 == 0) {
                rank++
                continue
            }
            var length = 0
            var ways = 1
            while (rank + length <= 13 && mask ushr (rank + length) and 1 == 1) {
                ways *= ((counts ushr ((rank + length) * 4)) and 15).toInt()
                length++
            }
            if (length >= 3) return length * ways
            rank += length
        }
        return 0
    }

    /** Every scoring combination in a hand. Slower than [score], for display. */
    fun breakdown(hand: List<Card>, starter: Card, crib: Boolean): List<ScoreItem> {
        val five = hand + starter
        val subsets = (1 until 32).map { mask -> five.filterIndexed { i, _ -> mask ushr i and 1 == 1 } }
        val items = mutableListOf<ScoreItem>()

        subsets.filter { s -> s.sumOf { it.value } == 15 }
            .forEach { items += ScoreItem(ScoreKind.FIFTEEN, it, 2) }

        subsets.filter { it.size == 2 && it[0].rank == it[1].rank }
            .forEach { items += ScoreItem(ScoreKind.PAIR, it, 2) }

        for (length in 5 downTo 3) {
            val runs = subsets.filter { it.size == length && isRun(it) }
            if (runs.isNotEmpty()) {
                runs.forEach { items += ScoreItem(ScoreKind.RUN, it.sortedBy(Card::rank), length) }
                break
            }
        }

        if (hand.all { it.suit == hand[0].suit }) {
            if (starter.suit == hand[0].suit) items += ScoreItem(ScoreKind.FLUSH, five, 5)
            else if (!crib) items += ScoreItem(ScoreKind.FLUSH, hand, 4)
        }

        hand.firstOrNull { it.rank == 11 && it.suit == starter.suit }
            ?.let { items += ScoreItem(ScoreKind.NOBS, listOf(it, starter), 1) }

        return items
    }

    private fun isRun(cards: List<Card>): Boolean =
        cards.map(Card::rank).sorted().zipWithNext().all { (a, b) -> b == a + 1 }
}

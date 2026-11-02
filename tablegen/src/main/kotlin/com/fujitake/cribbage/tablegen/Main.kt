package com.fujitake.cribbage.tablegen

import com.fujitake.cribbage.engine.Card
import com.fujitake.cribbage.engine.DiscardAnalysis
import com.fujitake.cribbage.engine.DiscardTables
import com.fujitake.cribbage.engine.FULL_DECK
import java.io.File
import java.util.stream.IntStream
import kotlin.random.Random

private const val ROUNDS = 3
private const val DEALS_PER_ROUND = 20_000

/**
 * Simulates strong discards to learn what each seat throws and keeps. Crib values depend on the
 * opponent's throws, so each round uses the previous round's tables, starting from uniform.
 * Hand and crib only: including pegging would multiply the runtime for little change in rates.
 */
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "engine/src/main/resources/discard_tables.txt")
    var tables = DiscardTables.UNIFORM
    repeat(ROUNDS) { round ->
        val started = System.currentTimeMillis()
        tables = simulate(tables, seed = 1000L + round)
        println("Round ${round + 1}/$ROUNDS took ${(System.currentTimeMillis() - started) / 1000}s")
    }
    output.parentFile.mkdirs()
    output.writeText(tables.serialize())
    println("Wrote ${output.path}")
}

private class Tally {
    val thrown = LongArray(DiscardTables.PAIR_SLOTS)
    val pairSeen = LongArray(DiscardTables.PAIR_SLOTS)
    val kept = LongArray(14)
    val dealt = LongArray(14)

    fun add(six: List<Card>, keep: List<Card>, discard: List<Card>) {
        for (i in six.indices) for (j in i + 1 until six.size) {
            pairSeen[DiscardTables.pairIndex(six[i].rank, six[j].rank)]++
        }
        thrown[DiscardTables.pairIndex(discard[0].rank, discard[1].rank)]++
        six.forEach { dealt[it.rank]++ }
        keep.forEach { kept[it.rank]++ }
    }

    fun merge(other: Tally) {
        for (i in thrown.indices) {
            thrown[i] += other.thrown[i]
            pairSeen[i] += other.pairSeen[i]
        }
        for (i in kept.indices) {
            kept[i] += other.kept[i]
            dealt[i] += other.dealt[i]
        }
    }

    fun throwRates() = DoubleArray(thrown.size) { i ->
        if (pairSeen[i] == 0L) 0.0 else thrown[i].toDouble() / pairSeen[i]
    }

    fun keepRates() = DoubleArray(kept.size) { i ->
        if (dealt[i] == 0L) 0.0 else kept[i].toDouble() / dealt[i]
    }
}

private fun simulate(previous: DiscardTables, seed: Long): DiscardTables {
    val seats = listOf(true, false).map { dealer ->
        val tallies = IntStream.range(0, DEALS_PER_ROUND).parallel()
            .mapToObj { i ->
                val six = FULL_DECK.shuffled(Random(seed * 31 + i)).take(6)
                val best = DiscardAnalysis.rank(six, dealer, previous, includePegging = false).first()
                Tally().apply { add(six, best.keep, best.discard) }
            }
            .toList()
        Tally().apply { tallies.forEach(::merge) }
    }
    return DiscardTables(
        throwRate = arrayOf(seats[0].throwRates(), seats[1].throwRates()),
        keepRate = arrayOf(seats[0].keepRates(), seats[1].keepRates()),
    )
}

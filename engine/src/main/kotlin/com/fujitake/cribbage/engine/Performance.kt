package com.fujitake.cribbage.engine

/** How one finished hand went, kept so progress can be tracked over time. */
data class HandRecord(
    val time: Long,
    val fullGame: Boolean,
    val youDealt: Boolean,
    val discardBest: Boolean,
    /** Pegging cards where you had a real choice, and how many of those were the top choice. */
    val peggingChoices: Int,
    val peggingBest: Int,
    val discardLost: Double,
    val peggingLost: Double,
) {
    val decisions: Int get() = 1 + peggingChoices
    val best: Int get() = (if (discardBest) 1 else 0) + peggingBest
    val pointsLost: Double get() = discardLost + peggingLost

    fun serialize(): String = listOf(
        time,
        if (fullGame) 1 else 0,
        if (youDealt) 1 else 0,
        if (discardBest) 1 else 0,
        peggingChoices,
        peggingBest,
        discardLost,
        peggingLost,
    ).joinToString(",")

    companion object {
        fun from(report: DiscardReport, plays: List<PlayReport>, fullGame: Boolean, youDealt: Boolean, time: Long): HandRecord {
            val graded = plays.filter { !it.forced }
            return HandRecord(
                time = time,
                fullGame = fullGame,
                youDealt = youDealt,
                discardBest = report.grade == Grade.BEST,
                peggingChoices = graded.size,
                peggingBest = graded.count { it.grade == Grade.BEST },
                discardLost = report.pointsLost,
                peggingLost = graded.sumOf { it.pointsLost },
            )
        }

        /** Reads one line, or returns null if it's unreadable. */
        fun parse(line: String): HandRecord? {
            val f = line.trim().split(",")
            if (f.size != 8) return null
            fun flag(text: String) = when (text) {
                "1" -> true
                "0" -> false
                else -> throw NumberFormatException(text)
            }
            return try {
                HandRecord(
                    time = f[0].toLong(),
                    fullGame = flag(f[1]),
                    youDealt = flag(f[2]),
                    discardBest = flag(f[3]),
                    peggingChoices = f[4].toInt(),
                    peggingBest = f[5].toInt(),
                    discardLost = f[6].toDouble(),
                    peggingLost = f[7].toDouble(),
                )
            } catch (_: NumberFormatException) {
                null
            }
        }

        fun parseAll(text: String): List<HandRecord> = text.lines().mapNotNull(::parse)
    }
}

/**
 * The skill rating. Each hand is worth a performance of [PERFECT] minus [PER_POINT] for every
 * expected point given up, so a perfect hand is 1500 and giving up two points is 1000. The rating
 * moves [WEIGHT] of the way toward each hand's performance, so it settles at your typical play and
 * moves most when a hand is far from it.
 */
object Rating {
    const val START = 1000.0
    const val PERFECT = 1500.0
    const val PER_POINT = 250.0
    const val WEIGHT = 0.05

    fun performance(pointsLost: Double): Double = maxOf(0.0, PERFECT - PER_POINT * pointsLost)

    fun next(rating: Double, pointsLost: Double): Double = rating + WEIGHT * (performance(pointsLost) - rating)
}

/** Everything the profile shows, worked out from the hand records, oldest first. */
class Performance(val records: List<HandRecord>) {
    /** The rating after each hand. */
    val ratings: List<Double> = records.runningFold(Rating.START) { r, hand -> Rating.next(r, hand.pointsLost) }.drop(1)

    val rating: Double get() = ratings.lastOrNull() ?: Rating.START

    val accuracy: Double get() = ratio(records.sumOf { it.best }, records.sumOf { it.decisions }) ?: 0.0

    val discardAccuracy: Double get() = ratio(records.count { it.discardBest }, records.size) ?: 0.0

    /** Null until you've had a real choice while pegging. */
    val peggingAccuracy: Double? get() = ratio(records.sumOf { it.peggingBest }, records.sumOf { it.peggingChoices })

    val averageLost: Double get() = if (records.isEmpty()) 0.0 else records.sumOf { it.pointsLost } / records.size

    /** Accuracy over the [window] hands up to and including each hand. */
    fun rollingAccuracy(window: Int): List<Double> = records.indices.map { i ->
        val recent = records.subList(maxOf(0, i - window + 1), i + 1)
        ratio(recent.sumOf { it.best }, recent.sumOf { it.decisions }) ?: 0.0
    }

    private fun ratio(part: Int, whole: Int): Double? = if (whole == 0) null else part.toDouble() / whole
}

package com.fujitake.cribbage.engine

/** Expected points given up in one finished hand. */
data class HandLoss(val discard: Double, val pegging: Double) {
    val total: Double get() = discard + pegging
}

/** A game rebuilt from a save, with the reports for the current hand's decisions so far. */
class RestoredGame(
    val match: Match,
    val history: List<HandLoss>,
    val report: DiscardReport?,
    val starterCut: Boolean,
    val plays: List<PlayReport>,
)

/**
 * A game in progress. Deals come from the seed and the computer is deterministic, so your choices
 * this hand are enough to rebuild the exact position, even in the middle of pegging.
 */
data class SavedGame(
    val seed: Long,
    val handNumber: Int,
    val scores: List<Int>,
    val history: List<HandLoss>,
    val discard: List<Card>,
    val starterCut: Boolean,
    val plays: List<Card>,
) {
    fun serialize(): String = listOf(
        VERSION,
        seed.toString(),
        handNumber.toString(),
        scores.joinToString(","),
        history.joinToString(",") { "${it.discard}:${it.pegging}" },
        discard.joinToString(",") { it.id.toString() },
        if (starterCut) "1" else "0",
        plays.joinToString(",") { it.id.toString() },
    ).joinToString(";")

    fun restore(memo: DiscardMemo): RestoredGame {
        val match = Match(seed, memo, startHand = handNumber, startScores = scores)
        val hand = match.hand
        if (discard.isEmpty()) return RestoredGame(match, history, null, false, emptyList())
        val report = hand.discard(discard)
        if (!starterCut) return RestoredGame(match, history, report, false, emptyList())
        hand.cutStarter()
        val reports = plays.map { card ->
            while (hand.pegging.toMove == COMPUTER) hand.playComputerCard()
            hand.playYourCard(card)
        }
        return RestoredGame(match, history, report, true, reports)
    }

    companion object {
        private const val VERSION = "1"

        /** Reads a save, or returns null if it's missing or unreadable. */
        fun parse(text: String?): SavedGame? {
            val fields = text?.split(";") ?: return null
            if (fields.size != 8 || fields[0] != VERSION) return null
            fun cards(field: String) = if (field.isEmpty()) emptyList() else field.split(",").map { Card(it.toInt()) }
            return try {
                SavedGame(
                    seed = fields[1].toLong(),
                    handNumber = fields[2].toInt(),
                    scores = fields[3].split(",").map(String::toInt),
                    history = if (fields[4].isEmpty()) emptyList() else fields[4].split(",").map {
                        val (discard, pegging) = it.split(":")
                        HandLoss(discard.toDouble(), pegging.toDouble())
                    },
                    discard = cards(fields[5]),
                    starterCut = fields[6] == "1",
                    plays = cards(fields[7]),
                )
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}

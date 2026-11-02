package com.fujitake.cribbage.engine

import kotlin.math.roundToInt

object Coaching {
    private const val RISK_GAP = 0.05

    /** One line saying why the top card is best, built only from the numbers behind the verdict. */
    fun explain(report: PlayReport): String {
        val played = report.played
        val best = report.best
        val runnerUp = report.options.getOrNull(1)
        if (best.card.rank == played.card.rank || report.grade == Grade.BEST) {
            return when {
                played.pointsNow > 0 -> "Scores ${played.pointsNow} now, and nothing else here does better overall."
                runnerUp != null && played.replyChance + RISK_GAP < runnerUp.replyChance ->
                    "Safest card: a ${percent(played.replyChance)} chance of a scoring reply, " +
                        "against ${percent(runnerUp.replyChance)} for ${runnerUp.card}."
                else -> "Your strongest option once the rest of the sequence is counted."
            }
        }
        return when {
            best.pointsNow > played.pointsNow ->
                "${best.card} scores ${best.pointsNow} now, and ${played.card} scores ${played.pointsNow}."
            played.replyChance > best.replyChance + RISK_GAP ->
                "${played.card} allows a scoring reply ${percent(played.replyChance)} of the time, " +
                    "and ${best.card} only ${percent(best.replyChance)}."
            else -> "${best.card} leaves you better placed for the rest of the sequence."
        }
    }

    private fun percent(chance: Double) = "${(chance * 100).roundToInt()}%"
}

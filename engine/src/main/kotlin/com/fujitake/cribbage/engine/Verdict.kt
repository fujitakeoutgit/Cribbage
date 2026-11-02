package com.fujitake.cribbage.engine

enum class Grade(val label: String) {
    BEST("Best"),
    GOOD("Good"),
    INACCURACY("Inaccuracy"),
    MISTAKE("Mistake"),
}

object Verdict {
    /** Expected points given up below which a choice counts as tied with the best. */
    const val BEST_BELOW = 0.05
    const val GOOD_BELOW = 0.25
    const val INACCURACY_BELOW = 0.75

    fun grade(pointsLost: Double): Grade = when {
        pointsLost < BEST_BELOW -> Grade.BEST
        pointsLost < GOOD_BELOW -> Grade.GOOD
        pointsLost < INACCURACY_BELOW -> Grade.INACCURACY
        else -> Grade.MISTAKE
    }
}

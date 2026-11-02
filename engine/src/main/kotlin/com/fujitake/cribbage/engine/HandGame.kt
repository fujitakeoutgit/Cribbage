package com.fujitake.cribbage.engine

import kotlin.random.Random

const val YOU = 0
const val COMPUTER = 1

/** The cut for deal. The lower card deals, and equal ranks are recut before this is built. */
data class DealCut(val yours: Card, val computers: Card) {
    val youDeal: Boolean get() = yours.rank < computers.rank

    companion object {
        fun draw(random: Random): DealCut {
            while (true) {
                val (a, b) = FULL_DECK.shuffled(random).take(2)
                if (a.rank != b.rank) return DealCut(a, b)
            }
        }
    }
}

data class DiscardReport(
    val options: List<DiscardEval>,
    val chosen: DiscardEval,
    val rank: Int,
    val pointsLost: Double,
    val grade: Grade,
    val chances: KeepChances,
)

/** The verdict on one pegging card. A forced play has no [grade] and gives up nothing. */
data class PlayReport(
    val played: CardEval,
    val options: List<CardEval>,
    val pointsLost: Double,
    val grade: Grade?,
) {
    val best: CardEval get() = options.first()
    val forced: Boolean get() = grade == null
}

/** A hand counted at the show. */
data class HandCount(val seat: Int, val crib: Boolean, val cards: List<Card>, val items: List<ScoreItem>) {
    val points: Int get() = items.sumOf { it.points }
}

data class HandResult(
    val counts: List<HandCount>,
    val pegged: List<Int>,
    val heels: Int,
    val totals: List<Int>,
)

/**
 * One deal from cut to count. Seat views handed to the computer are built only from what it can
 * see, so it can't peek at your hand or the starter.
 */
class HandGame(
    random: Random,
    val cut: DealCut,
    private val tables: DiscardTables = DiscardTables.bundled,
) {
    private val deck = FULL_DECK.shuffled(random)
    val yourSix: List<Card> = deck.take(6).sorted()
    private val computerSix: List<Card> = deck.drop(6).take(6)
    private val hiddenStarter: Card = deck[12]

    val youDeal: Boolean get() = cut.youDeal
    private val dealerSeat: Int get() = if (youDeal) YOU else COMPUTER

    private lateinit var computerChoice: DiscardEval
    lateinit var yourKeep: List<Card>
        private set
    lateinit var crib: List<Card>
        private set
    private lateinit var yourDiscard: List<Card>
    private lateinit var fullHands: List<List<Card>>

    var starter: Card? = null
        private set
    lateinit var pegging: PeggingState
        private set

    /** Ranks your choice against all 15 options and makes the computer's own discard. */
    fun discard(two: List<Card>): DiscardReport {
        require(two.size == 2 && yourSix.containsAll(two))
        val options = DiscardAnalysis.rank(yourSix, youDeal, tables)
        val chosen = options.first { it.discard == two.sorted() }
        val rank = options.indexOf(chosen) + 1
        val lost = options.first().total(youDeal) - chosen.total(youDeal)

        computerChoice = Computer.discard(computerSix, !youDeal, tables)
        yourKeep = chosen.keep
        yourDiscard = chosen.discard
        crib = chosen.discard + computerChoice.discard
        fullHands = listOf(chosen.keep, computerChoice.keep)
        return DiscardReport(options, chosen, rank, lost, Verdict.grade(lost), DiscardAnalysis.chances(chosen.keep, yourSix))
    }

    /** Turns the starter and begins pegging. Returns two if it's a jack, which go to the dealer. */
    fun cutStarter(): Int {
        starter = hiddenStarter
        pegging = PeggingState.start(fullHands, firstToMove = 1 - dealerSeat)
        return heels
    }

    private val heels: Int get() = if (hiddenStarter.rank == 11) 2 else 0

    private fun view(seat: Int): SeatView {
        val discards = if (seat == YOU) yourDiscard else computerChoice.discard
        return SeatView.of(pegging, seat, seat == dealerSeat, discards, hiddenStarter)
    }

    /** Grades your card on what you could see, then plays it. */
    fun playYourCard(card: Card): PlayReport {
        val options = PeggingSearch.evaluate(view(YOU), tables)
        val played = options.first { it.card == card }
        val distinctChoices = options.map { it.card.rank }.distinct().size
        pegging = pegging.play(card)
        val lost = options.first().value - played.value
        return if (distinctChoices == 1) {
            PlayReport(played, options, 0.0, null)
        } else {
            PlayReport(played, options, lost, Verdict.grade(lost))
        }
    }

    fun playComputerCard() {
        pegging = pegging.play(Computer.play(view(COMPUTER), tables))
    }

    fun result(): HandResult {
        val theStarter = requireNotNull(starter)
        val pone = 1 - dealerSeat
        val counts = listOf(
            HandCount(pone, false, fullHands[pone], Scoring.breakdown(fullHands[pone], theStarter, false)),
            HandCount(dealerSeat, false, fullHands[dealerSeat], Scoring.breakdown(fullHands[dealerSeat], theStarter, false)),
            HandCount(dealerSeat, true, crib, Scoring.breakdown(crib, theStarter, true)),
        )
        val totals = listOf(YOU, COMPUTER).map { seat ->
            pegging.pegged[seat] + counts.filter { it.seat == seat }.sumOf { it.points } +
                if (seat == dealerSeat) heels else 0
        }
        return HandResult(counts, pegging.pegged, heels, totals)
    }
}

/** The computer always takes the top-valued option from the shared evaluators. */
object Computer {
    fun discard(six: List<Card>, dealer: Boolean, tables: DiscardTables): DiscardEval =
        DiscardAnalysis.rank(six, dealer, tables).first()

    fun play(view: SeatView, tables: DiscardTables): Card =
        PeggingSearch.evaluate(view, tables).first().card
}

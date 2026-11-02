package com.fujitake.cribbage.engine

/** A card as an id in 0..51: `(rank - 1) * 4 + suit`. Hot loops work on the raw ids. */
@JvmInline
value class Card(val id: Int) : Comparable<Card> {
    /** 1 (ace) to 13 (king). */
    val rank: Int get() = rankOf(id)

    /** 0..3 for clubs, diamonds, hearts, spades. */
    val suit: Int get() = suitOf(id)

    /** Pegging and fifteens value: court cards count ten. */
    val value: Int get() = valueOf(id)

    val rankLabel: String get() = RANK_LABELS[rank]
    val suitSymbol: Char get() = SUIT_SYMBOLS[suit]

    override fun compareTo(other: Card) = id.compareTo(other.id)
    override fun toString() = "$rankLabel$suitSymbol"

    companion object {
        fun of(rank: Int, suit: Int) = Card((rank - 1) * 4 + suit)
    }
}

internal fun rankOf(id: Int) = (id ushr 2) + 1
internal fun suitOf(id: Int) = id and 3
internal fun valueOf(id: Int) = minOf(rankOf(id), 10)
internal fun rankValue(rank: Int) = minOf(rank, 10)

val RANK_LABELS = listOf("", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
const val SUIT_SYMBOLS = "♣♦♥♠"

val FULL_DECK: List<Card> = (0 until 52).map(::Card)

/** Parses a card such as `5H`, `TS` or `10S`. */
fun card(text: String): Card {
    val rankText = text.dropLast(1).uppercase()
    val rank = if (rankText == "T") 10 else RANK_LABELS.indexOf(rankText)
    val suit = "CDHS".indexOf(text.last().uppercaseChar())
    require(rank >= 1 && suit >= 0) { "Bad card: $text" }
    return Card.of(rank, suit)
}

/** Parses a space-separated list of cards. */
fun cards(text: String): List<Card> = text.trim().split(Regex("\\s+")).map(::card)

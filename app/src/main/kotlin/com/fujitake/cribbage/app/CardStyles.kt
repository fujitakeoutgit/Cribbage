package com.fujitake.cribbage.app

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

private val Black = Color(0xFF15171B)
private val Red = Color(0xFFD23B2A)
private val White = Color(0xFFF1F2F4)
private val Coral = Color(0xFFFF7A6B)
private val Blue = Color(0xFF1F5FD0)
private val Green = Color(0xFF1E8A4C)
private val SkyOnDark = Color(0xFF7FA9FF)
private val MintOnDark = Color(0xFF63D394)

/** Suit colors are listed clubs, diamonds, hearts, spades, matching `Card.suit`. */
enum class FaceStyle(
    val label: String,
    val face: Color,
    private val inks: List<Color>,
    private val inksOnDark: List<Color>,
    val outlined: Boolean = false,
) {
    IVORY("Ivory", Color(0xFFF4F1EA), listOf(Black, Red, Red, Black), listOf(White, Ink.Red, Ink.Red, White)),
    SLATE("Slate", Color(0xFF232833), listOf(White, Coral, Coral, White), listOf(White, Ink.Red, Ink.Red, White), outlined = true),
    FOUR_COLOR("Four color", Color(0xFFF4F1EA), listOf(Green, Blue, Red, Black), listOf(MintOnDark, SkyOnDark, Ink.Red, White)),
    CITRON("Citron", Color(0xFFC8F04B), listOf(Color(0xFF12150A), Color(0xFFB0280F), Color(0xFFB0280F), Color(0xFF12150A)), listOf(White, Ink.Red, Ink.Red, White)),
    ;

    fun ink(suit: Int): Color = inks[suit]

    /** The suit's color when written straight on the dark app background. */
    fun inkOnDark(suit: Int): Color = inksOnDark[suit]
}

enum class BackStyle(val label: String, val color: Color, val line: Color) {
    GRAPHITE("Graphite", Color(0xFF232833), Color(0x33C8F04B)),
    COBALT("Cobalt", Color(0xFF1F4FBF), Color(0x40FFFFFF)),
    CRIMSON("Crimson", Color(0xFF9E2A2B), Color(0x40FFFFFF)),
    FOREST("Forest", Color(0xFF1E5B41), Color(0x40FFFFFF)),
}

val LocalFaceStyle = compositionLocalOf { FaceStyle.IVORY }
val LocalBackStyle = compositionLocalOf { BackStyle.GRAPHITE }

package com.ordbit.svenska.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** A complete color scheme for the app. */
data class Palette(
    val id: String,
    val name: String,
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val good: Color,
    val dark: Boolean
)

val Palettes: List<Palette> = listOf(
    Palette(
        "dagsljus", "Dagsljus",
        bg = Color(0xFFF3EFE4), bg2 = Color(0xFFEBE5D4), surface = Color(0xFFFFFDF6),
        ink = Color(0xFF262017), muted = Color(0xFF7C7361), line = Color(0xFFE0D8C3),
        accent = Color(0xFFB23A2E), accentSoft = Color(0xFFF0D9D3), onAccent = Color(0xFFFFFDF6),
        good = Color(0xFF3F7D4E), dark = false
    ),
    Palette(
        "skog", "Skog",
        bg = Color(0xFFEEF0E6), bg2 = Color(0xFFE3E7D6), surface = Color(0xFFFBFCF6),
        ink = Color(0xFF1F2A1D), muted = Color(0xFF6C7561), line = Color(0xFFD6DCC4),
        accent = Color(0xFF3C6E47), accentSoft = Color(0xFFDDE6D4), onAccent = Color(0xFFFBFCF6),
        good = Color(0xFF3C6E47), dark = false
    ),
    Palette(
        "hav", "Hav",
        bg = Color(0xFFE9EEF2), bg2 = Color(0xFFDDE6EE), surface = Color(0xFFFBFDFF),
        ink = Color(0xFF162533), muted = Color(0xFF5F7184), line = Color(0xFFCDD9E3),
        accent = Color(0xFF1F6F8B), accentSoft = Color(0xFFD6E4EA), onAccent = Color(0xFFFBFDFF),
        good = Color(0xFF2F7D57), dark = false
    ),
    Palette(
        "sol", "Sol",
        bg = Color(0xFFFDF3DF), bg2 = Color(0xFFF7E9C4), surface = Color(0xFFFFFDF3),
        ink = Color(0xFF3A2C12), muted = Color(0xFF917F54), line = Color(0xFFECDCB0),
        accent = Color(0xFFD98324), accentSoft = Color(0xFFF6E6C6), onAccent = Color(0xFFFFFDF3),
        good = Color(0xFF5E7D2F), dark = false
    ),
    Palette(
        "skymning", "Skymning",
        bg = Color(0xFF181A23), bg2 = Color(0xFF10121A), surface = Color(0xFF22252F),
        ink = Color(0xFFECE8DF), muted = Color(0xFF8E8E9C), line = Color(0xFF33363F),
        accent = Color(0xFFE3A857), accentSoft = Color(0xFF3A3527), onAccent = Color(0xFF181A23),
        good = Color(0xFF7BBD8A), dark = true
    ),
    Palette(
        "natt", "Natt",
        bg = Color(0xFF14141B), bg2 = Color(0xFF0C0C12), surface = Color(0xFF1F1F2A),
        ink = Color(0xFFE8E6EF), muted = Color(0xFF878596), line = Color(0xFF2E2E3B),
        accent = Color(0xFF7C8CF8), accentSoft = Color(0xFF2A2C44), onAccent = Color(0xFF14141B),
        good = Color(0xFF7BBD8A), dark = true
    )
)

fun paletteById(id: String): Palette = Palettes.firstOrNull { it.id == id } ?: Palettes[0]

val LocalPalette = compositionLocalOf { Palettes[0] }

/* Semantic colors for the three-state marking. Fixed across all themes so the
   meaning (don't know / unsure / know) stays consistent. */
val StatusNo = Color(0xFFC0473B)   // Kan inte
val StatusMid = Color(0xFFC68A2E)  // Osäker
val StatusYes = Color(0xFF3F7D4E)  // Kan

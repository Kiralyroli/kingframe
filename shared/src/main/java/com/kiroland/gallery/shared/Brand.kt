package com.kiroland.gallery.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * "Csend" (quiet) identity shared by the phone and TV apps: flat near-black surfaces,
 * type-led layouts and a single amber accent that marks focus and the primary action.
 */
object Brand {
    // Dark
    val Night0 = Color(0xFF0D0D0F)   // background
    val Night1 = Color(0xFF151518)   // card
    val Night2 = Color(0xFF1A1A1E)   // focused row, raised card
    val Night3 = Color(0xFF2A2A30)   // chip / icon on focus
    val NightIcon = Color(0xFF1D1D21) // icon circle at rest
    val NightLine = Color(0xFF2E2E34) // hairlines, PIN underline

    val TextHigh = Color(0xFFF2F2F2)
    val TextMid = Color(0xFF9A9AA0)
    val TextLow = Color(0xFF6B6B72)
    val TextFaint = Color(0xFF5F5F66) // section labels

    val Amber = Color(0xFFFFB547)
    val OnAmber = Color(0xFF1A1206)
    val Success = Color(0xFF4ADE80)
    val Danger = Color(0xFFF87171)
    val Warning = Color(0xFFFBBF24)

    // Light counterparts (phone only)
    val Paper0 = Color(0xFFFAFAF8)
    val Paper1 = Color(0xFFFFFFFF)
    val Paper2 = Color(0xFFF1F1EE)
    val PaperLine = Color(0xFFE3E3E0)
    val Ink = Color(0xFF141416)
    val InkMid = Color(0xFF5F5F66)
    val InkLow = Color(0xFF8A8A90)
    /** Amber dark enough for text and borders on white. */
    val AmberDeep = Color(0xFFA35A06)

    @OptIn(ExperimentalTextApi::class)
    private fun jakarta(weight: FontWeight) = Font(
        R.font.plus_jakarta_sans,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    /** Plus Jakarta Sans (variable, OFL) – covers Hungarian ő/ű. */
    val Font: FontFamily = FontFamily(
        jakarta(FontWeight.Light),
        jakarta(FontWeight.Normal),
        jakarta(FontWeight.Medium),
        jakarta(FontWeight.SemiBold),
        jakarta(FontWeight.Bold),
    )
}

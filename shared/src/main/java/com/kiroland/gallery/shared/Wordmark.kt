package com.kiroland.gallery.shared

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** "KingFrame" as a wordmark: a solid "King" and a light "Frame". */
val KingFrameWordmark: AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontFamily = Brand.Font, fontWeight = FontWeight.SemiBold)) { append("King") }
    withStyle(SpanStyle(fontFamily = Brand.Font, fontWeight = FontWeight.Light)) { append("Frame") }
}

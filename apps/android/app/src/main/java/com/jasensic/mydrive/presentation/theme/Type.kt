package com.jasensic.mydrive.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Family = FontFamily.Default

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = Family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Hierarchy comes from size and weight, not from boxes: Large Title / Title / Headline / Body / Footnote. */
val AppTypography = Typography(
    displaySmall = style(34, FontWeight.Bold, 41, 0.01f),
    headlineLarge = style(34, FontWeight.Bold, 41, 0.01f),
    headlineMedium = style(34, FontWeight.Bold, 41, 0.01f),
    headlineSmall = style(26, FontWeight.Bold, 32),
    titleLarge = style(22, FontWeight.SemiBold, 28),
    titleMedium = style(17, FontWeight.SemiBold, 22, -0.01f),
    titleSmall = style(15, FontWeight.SemiBold, 20, -0.01f),
    bodyLarge = style(17, FontWeight.Normal, 22, -0.01f),
    bodyMedium = style(15, FontWeight.Normal, 20, -0.005f),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(17, FontWeight.SemiBold, 22, -0.01f),
    labelMedium = style(13, FontWeight.Medium, 18),
    labelSmall = style(11, FontWeight.Medium, 13, 0.01f),
)

package com.jasensic.mydrive.presentation.theme

import androidx.compose.ui.graphics.Color

// One accent, neutral surfaces, red only for destructive actions. Values follow the iOS
// system palette so the app reads as native next to iOS-style chrome.

// Light
val AccentLight = Color(0xFF007AFF)
val GroupedBackgroundLight = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val FillLight = Color(0xFFE9E9EE)
val SeparatorLight = Color(0xFFD9D9DE)
val LabelLight = Color(0xFF000000)
val SecondaryLabelLight = Color(0xFF6C6C70)
val DestructiveLight = Color(0xFFFF3B30)

// Dark (true black background so OLED screens stay calm)
val AccentDark = Color(0xFF0A84FF)
val GroupedBackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val FillDark = Color(0xFF2C2C2E)
val SeparatorDark = Color(0xFF38383A)
val LabelDark = Color(0xFFFFFFFF)
val SecondaryLabelDark = Color(0xFFAEAEB2)
val DestructiveDark = Color(0xFFFF453A)

// Placeholder artwork. Muted, so a screen of covers without images does not shout.
val ArtworkPalette = listOf(
    Color(0xFF5E7CE2),
    Color(0xFF9B6BD6),
    Color(0xFFE0766B),
    Color(0xFF3DA5A0),
    Color(0xFFD99A3E),
    Color(0xFF5B8DEF),
    Color(0xFFC95E8C),
    Color(0xFF4FA36B),
)

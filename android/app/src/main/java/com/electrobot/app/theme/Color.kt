package com.electrobot.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// =========================================================================
// ElectroBot Mobile Design Tokens (Google Material Design 3)
// Fully compliant with M3 Semantic Token Hierarchy (WCAG AAA/AA Contrast)
// =========================================================================

// --- M3 Dark Tonal Palette ---
val M3DarkPrimary = Color(0xFFA8C7FA)
val M3DarkOnPrimary = Color(0xFF003063)
val M3DarkPrimaryContainer = Color(0xFF0842A0)
val M3DarkOnPrimaryContainer = Color(0xFFD3E4FF)

val M3DarkSecondary = Color(0xFF6DD58C)
val M3DarkOnSecondary = Color(0xFF003915)
val M3DarkSecondaryContainer = Color(0xFF005322)
val M3DarkOnSecondaryContainer = Color(0xFF8CF2A6)

val M3DarkTertiary = Color(0xFFFFB74D)
val M3DarkOnTertiary = Color(0xFF452B00)
val M3DarkTertiaryContainer = Color(0xFF633F00)
val M3DarkOnTertiaryContainer = Color(0xFFFFDDAF)

val M3DarkError = Color(0xFFF2B8B5)
val M3DarkOnError = Color(0xFF601410)
val M3DarkErrorContainer = Color(0xFF8C1D18)
val M3DarkOnErrorContainer = Color(0xFFF9DEDC)

val M3DarkBackground = Color(0xFF111318)
val M3DarkOnBackground = Color(0xFFE2E2E9)
val M3DarkSurface = Color(0xFF111318)
val M3DarkOnSurface = Color(0xFFE2E2E9)
val M3DarkSurfaceVariant = Color(0xFF1D2024)
val M3DarkOnSurfaceVariant = Color(0xFFC4C6D0)

val M3DarkSurfaceContainerLowest = Color(0xFF0C0E13)
val M3DarkSurfaceContainerLow = Color(0xFF191C20)
val M3DarkSurfaceContainer = Color(0xFF1D2024)
val M3DarkSurfaceContainerHigh = Color(0xFF282A2F)
val M3DarkSurfaceContainerHighest = Color(0xFF33353A)

val M3DarkOutline = Color(0xFF8E9099)
val M3DarkOutlineVariant = Color(0xFF44474F)

// --- M3 Light Tonal Palette ---
val M3LightPrimary = Color(0xFF005FB0)
val M3LightOnPrimary = Color(0xFFFFFFFF)
val M3LightPrimaryContainer = Color(0xFFD5E3FF)
val M3LightOnPrimaryContainer = Color(0xFF001B3C)

val M3LightSecondary = Color(0xFF006E2E)
val M3LightOnSecondary = Color(0xFFFFFFFF)
val M3LightSecondaryContainer = Color(0xFF8CF2A6)
val M3LightOnSecondaryContainer = Color(0xFF00210A)

val M3LightTertiary = Color(0xFF8A5100)
val M3LightOnTertiary = Color(0xFFFFFFFF)
val M3LightTertiaryContainer = Color(0xFFFFDDAF)
val M3LightOnTertiaryContainer = Color(0xFF2C1600)

val M3LightError = Color(0xFFB3261E)
val M3LightOnError = Color(0xFFFFFFFF)
val M3LightErrorContainer = Color(0xFFF9DEDC)
val M3LightOnErrorContainer = Color(0xFF410E0B)

val M3LightBackground = Color(0xFFF9F9FF)
val M3LightOnBackground = Color(0xFF191C20)
val M3LightSurface = Color(0xFFFFFFFF)
val M3LightOnSurface = Color(0xFF191C20)
val M3LightSurfaceVariant = Color(0xFFE1E2EC)
val M3LightOnSurfaceVariant = Color(0xFF44474F)

val M3LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val M3LightSurfaceContainerLow = Color(0xFFF3F3FA)
val M3LightSurfaceContainer = Color(0xFFEDEDF4)
val M3LightSurfaceContainerHigh = Color(0xFFE7E8EE)
val M3LightSurfaceContainerHighest = Color(0xFFE2E2E9)

val M3LightOutline = Color(0xFF74777F)
val M3LightOutlineVariant = Color(0xFFC4C7D0)

val TextWhite = Color(0xFFFFFFFF)

@Immutable
data class ElectroCustomColors(
    val isDark: Boolean,
    val canvas: Color,
    val headerBg: Color,
    val headerBorder: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val inputFocus: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textTertiary: Color,
    val accentPrimary: Color,
    val accentPrimaryHover: Color,
    val accentSuccess: Color,
    val accentDanger: Color,
    val accentWarning: Color,
    val accentPurple: Color,
    val accentTeal: Color,
    val badgeNeutralBg: Color,
    val badgeNeutralText: Color,
    val badgeNeutralBorder: Color,
    val badgeSuccessBg: Color,
    val badgeSuccessText: Color,
    val badgeSuccessBorder: Color,
    val badgeDangerBg: Color,
    val badgeDangerText: Color,
    val badgeDangerBorder: Color,
    val badgeWarningBg: Color,
    val badgeWarningText: Color,
    val badgeWarningBorder: Color,
    val activeTabBg: Color,
    val activeTabText: Color
)

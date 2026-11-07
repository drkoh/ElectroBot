package com.electrobot.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// =========================================================================
// ElectroBot Mobile Design Tokens (Apple Human Interface Guidelines / Cupertino)
// Pure OLED Dark Mode & Crisp iOS Light Mode Palette
// =========================================================================

// --- Apple Dark Mode Palette (Cupertino Pro) ---
val AppleDarkCanvas = Color(0xFF000000)               // iOS System Background (True OLED Black)
val AppleDarkHeader = Color(0xFF121214)               // Translucent Top Bar & Nav Surface
val AppleDarkHeaderBorder = Color(0x26FFFFFF)         // Hairline 15% White
val AppleDarkCard = Color(0xFF1C1C1E)                 // iOS Secondary System Background (Inset Grouped)
val AppleDarkCardBorder = Color(0x2EFFFFFF)           // Crisp Hairline 18% White
val AppleDarkInput = Color(0xFF2C2C2E)                // iOS Tertiary System Background (Recessed Fields)
val AppleDarkInputBorder = Color(0x3DFFFFFF)          // Hairline 24% White
val AppleDarkInputFocus = Color(0xFF0A84FF)           // Apple System Blue (Focus)

val AppleDarkTextPrimary = Color(0xFFFFFFFF)          // Primary Label
val AppleDarkTextMuted = Color(0xFF8E8E93)            // Secondary Label (iOS System Grey)
val AppleDarkTextTertiary = Color(0xFF636366)         // Tertiary Label

val AppleDarkAccentPrimary = Color(0xFF0A84FF)        // Apple System Blue
val AppleDarkAccentPrimaryHover = Color(0xFF409CFF)   // Apple Light Blue
val AppleDarkAccentSuccess = Color(0xFF30D158)        // Apple System Green
val AppleDarkAccentDanger = Color(0xFFFF453A)         // Apple System Red
val AppleDarkAccentWarning = Color(0xFFFF9F0A)        // Apple System Orange / Amber
val AppleDarkAccentPurple = Color(0xFFBF5AF2)         // Apple System Purple
val AppleDarkAccentTeal = Color(0xFF64D2FF)           // Apple System Teal

val AppleDarkBadgeNeutralBg = Color(0x260A84FF)       // 15% Blue fill
val AppleDarkBadgeNeutralText = Color(0xFF0A84FF)     // Blue text
val AppleDarkBadgeNeutralBorder = Color(0x4D0A84FF)   // 30% Blue stroke

val AppleDarkBadgeSuccessBg = Color(0x2630D158)       // 15% Green fill
val AppleDarkBadgeSuccessText = Color(0xFF30D158)     // Green text
val AppleDarkBadgeSuccessBorder = Color(0x4D30D158)

val AppleDarkBadgeDangerBg = Color(0x26FF453A)        // 15% Red fill
val AppleDarkBadgeDangerText = Color(0xFFFF453A)      // Red text
val AppleDarkBadgeDangerBorder = Color(0x4DFF453A)

val AppleDarkBadgeWarningBg = Color(0x26FF9F0A)       // 15% Orange fill
val AppleDarkBadgeWarningText = Color(0xFFFF9F0A)     // Orange text
val AppleDarkBadgeWarningBorder = Color(0x4DFF9F0A)

val AppleDarkActiveTabBg = Color(0x260A84FF)

// --- Apple Light Mode Palette (Cupertino Clean) ---
val AppleLightCanvas = Color(0xFFF2F2F7)              // iOS System Grouped Background
val AppleLightHeader = Color(0xFFFFFFFF)
val AppleLightHeaderBorder = Color(0x1F000000)        // 12% Black
val AppleLightCard = Color(0xFFFFFFFF)                // iOS Card Surface
val AppleLightCardBorder = Color(0x14000000)          // 8% Black Hairline
val AppleLightInput = Color(0xFFE5E5EA)               // iOS Recessed Field
val AppleLightInputBorder = Color(0x26000000)
val AppleLightInputFocus = Color(0xFF007AFF)

val AppleLightTextPrimary = Color(0xFF000000)
val AppleLightTextMuted = Color(0xFF8E8E93)
val AppleLightTextTertiary = Color(0xFFAEB2B8)

val AppleLightAccentPrimary = Color(0xFF007AFF)       // Apple System Blue
val AppleLightAccentPrimaryHover = Color(0xFF0062CC)
val AppleLightAccentSuccess = Color(0xFF34C759)       // Apple System Green
val AppleLightAccentDanger = Color(0xFFFF3B30)        // Apple System Red
val AppleLightAccentWarning = Color(0xFFFF9500)       // Apple System Orange
val AppleLightAccentPurple = Color(0xFFAF52DE)
val AppleLightAccentTeal = Color(0xFF5AC8FA)

val AppleLightBadgeNeutralBg = Color(0x1A007AFF)
val AppleLightBadgeNeutralText = Color(0xFF007AFF)
val AppleLightBadgeNeutralBorder = Color(0x33007AFF)

val AppleLightBadgeSuccessBg = Color(0x1A34C759)
val AppleLightBadgeSuccessText = Color(0xFF28A745)
val AppleLightBadgeSuccessBorder = Color(0x3334C759)

val AppleLightBadgeDangerBg = Color(0x1AFF3B30)
val AppleLightBadgeDangerText = Color(0xFFE02E24)
val AppleLightBadgeDangerBorder = Color(0x33FF3B30)

val AppleLightBadgeWarningBg = Color(0x1AFF9500)
val AppleLightBadgeWarningText = Color(0xFFD97706)
val AppleLightBadgeWarningBorder = Color(0x33FF9500)

val AppleLightActiveTabBg = Color(0x1A007AFF)

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

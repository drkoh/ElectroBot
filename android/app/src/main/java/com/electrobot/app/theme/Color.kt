package com.electrobot.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// =========================================================================
// ElectroBot Mobile Design Tokens (Apple Human Interface / Cupertino)
// Pure OLED Dark Mode & High-Contrast Crisp iOS Light Mode Palette
// =========================================================================

// --- Apple Dark Mode Palette (Cupertino Pro OLED) ---
val AppleDarkCanvas = Color(0xFF000000)               // iOS System Background (Pure OLED Black)
val AppleDarkHeader = Color(0xFF161618)               // Translucent Top Bar & Nav Surface
val AppleDarkHeaderBorder = Color(0x33FFFFFF)         // Hairline 20% White
val AppleDarkCard = Color(0xFF1C1C1E)                 // iOS Secondary System Background (Inset Grouped)
val AppleDarkCardBorder = Color(0x38FFFFFF)           // Crisp Hairline 22% White
val AppleDarkInput = Color(0xFF2C2C2E)                // iOS Tertiary System Background
val AppleDarkInputBorder = Color(0x4DFFFFFF)          // Hairline 30% White
val AppleDarkInputFocus = Color(0xFF0A84FF)           // Apple System Blue

val AppleDarkTextPrimary = Color(0xFFFFFFFF)          // Primary Label (Pure White)
val AppleDarkTextMuted = Color(0xFF8E8E93)            // Secondary Label (iOS System Grey)
val AppleDarkTextTertiary = Color(0xFF636366)         // Tertiary Label

val AppleDarkAccentPrimary = Color(0xFF0A84FF)        // Apple System Blue
val AppleDarkAccentPrimaryHover = Color(0xFF409CFF)
val AppleDarkAccentSuccess = Color(0xFF30D158)        // Apple System Green
val AppleDarkAccentDanger = Color(0xFFFF453A)         // Apple System Red
val AppleDarkAccentWarning = Color(0xFFFF9F0A)        // Apple System Orange / Amber
val AppleDarkAccentPurple = Color(0xFFBF5AF2)
val AppleDarkAccentTeal = Color(0xFF64D2FF)

val AppleDarkBadgeNeutralBg = Color(0x330A84FF)
val AppleDarkBadgeNeutralText = Color(0xFF58A6FF)
val AppleDarkBadgeNeutralBorder = Color(0x660A84FF)

val AppleDarkBadgeSuccessBg = Color(0x3330D158)
val AppleDarkBadgeSuccessText = Color(0xFF30D158)
val AppleDarkBadgeSuccessBorder = Color(0x6630D158)

val AppleDarkBadgeDangerBg = Color(0x33FF453A)
val AppleDarkBadgeDangerText = Color(0xFFFF453A)
val AppleDarkBadgeDangerBorder = Color(0x66FF453A)

val AppleDarkBadgeWarningBg = Color(0x33FF9F0A)
val AppleDarkBadgeWarningText = Color(0xFFFF9F0A)
val AppleDarkBadgeWarningBorder = Color(0x66FF9F0A)

val AppleDarkActiveTabBg = Color(0x330A84FF)

// --- Apple Light Mode Palette (Crisp High-Contrast iOS Clean) ---
// High contrast to prevent washed-out/pale text on sunlight
val AppleLightCanvas = Color(0xFFF2F2F7)              // iOS System Grouped Background
val AppleLightHeader = Color(0xFFFFFFFF)
val AppleLightHeaderBorder = Color(0xFFD1D1D6)        // Apple Separator
val AppleLightCard = Color(0xFFFFFFFF)                // iOS Card Surface
val AppleLightCardBorder = Color(0xFFE5E5EA)          // Crisp Border
val AppleLightInput = Color(0xFFF2F2F7)               // Recessed Field
val AppleLightInputBorder = Color(0xFFC7C7CC)
val AppleLightInputFocus = Color(0xFF007AFF)

val AppleLightTextPrimary = Color(0xFF000000)          // Pure Black for crisp readability
val AppleLightTextMuted = Color(0xFF48484A)            // Dark Grey for high-contrast subtext
val AppleLightTextTertiary = Color(0xFF636366)

val AppleLightAccentPrimary = Color(0xFF007AFF)       // Apple System Blue
val AppleLightAccentPrimaryHover = Color(0xFF0056B3)
val AppleLightAccentSuccess = Color(0xFF28A745)       // Deep Green
val AppleLightAccentDanger = Color(0xFFD70015)        // Deep Apple Red
val AppleLightAccentWarning = Color(0xFFC96D00)       // Deep Amber
val AppleLightAccentPurple = Color(0xFF8944AB)
val AppleLightAccentTeal = Color(0xFF0071A4)

val AppleLightBadgeNeutralBg = Color(0xFFE1F0FF)      // Solid soft blue
val AppleLightBadgeNeutralText = Color(0xFF0066CC)    // High contrast blue text
val AppleLightBadgeNeutralBorder = Color(0xFFB3D7FF)

val AppleLightBadgeSuccessBg = Color(0xFFDCFCE7)      // Solid soft green
val AppleLightBadgeSuccessText = Color(0xFF15803D)    // High contrast green text
val AppleLightBadgeSuccessBorder = Color(0xFF86EFAC)

val AppleLightBadgeDangerBg = Color(0xFFFEE2E2)       // Solid soft red
val AppleLightBadgeDangerText = Color(0xFFB91C1C)     // High contrast red text
val AppleLightBadgeDangerBorder = Color(0xFFFCA5A5)

val AppleLightBadgeWarningBg = Color(0xFFFEF3C7)      // Solid soft amber
val AppleLightBadgeWarningText = Color(0xFF92400E)    // High contrast amber text
val AppleLightBadgeWarningBorder = Color(0xFFFCD34D)

val AppleLightActiveTabBg = Color(0xFFE1F0FF)

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

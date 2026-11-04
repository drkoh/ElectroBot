package com.electrobot.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// =========================================================================
// ElectroBot Mobile Design Tokens (Fluent 2 / Modern Slate)
// Matches desktop styling & WaSender design tokens
// =========================================================================

// --- Dark Slate Palette (Pro Slate Charcoal) ---
val DarkCanvas = Color(0xFF1C1E24)
val DarkHeader = Color(0xFF21232B)
val DarkHeaderBorder = Color(0xFF323642)
val DarkCard = Color(0xFF262933)
val DarkCardBorder = Color(0xFF353946)
val DarkInput = Color(0xFF1F222B)
val DarkInputBorder = Color(0xFF383D4A)
val DarkInputFocus = Color(0xFF388BFD)
val DarkTextPrimary = Color(0xFFE6EDF3)
val DarkTextMuted = Color(0xFF9BA3B4)
val DarkAccentPrimary = Color(0xFF1F6FEB)
val DarkAccentPrimaryHover = Color(0xFF388BFD)
val DarkAccentSuccess = Color(0xFF238636)
val DarkAccentDanger = Color(0xFFDA3633)
val DarkAccentWarning = Color(0xFFD29922)
val DarkBadgeNeutralBg = Color(0xFF223249)
val DarkBadgeNeutralText = Color(0xFF58A6FF)
val DarkBadgeNeutralBorder = Color(0xFF2C4161)
val DarkBadgeSuccessBg = Color(0xFF1C3D28)
val DarkBadgeSuccessText = Color(0xFF3FB950)
val DarkBadgeSuccessBorder = Color(0xFF235835)
val DarkBadgeDangerBg = Color(0xFF3D1E22)
val DarkBadgeDangerText = Color(0xFFF85149)
val DarkBadgeDangerBorder = Color(0xFF5C2A30)
val DarkActiveTabBg = Color(0xFF293448)

// --- Light Palette (Office Light) ---
val LightCanvas = Color(0xFFF3F4F6)
val LightHeader = Color(0xFFFFFFFF)
val LightHeaderBorder = Color(0xFFE5E7EB)
val LightCard = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFE5E7EB)
val LightInput = Color(0xFFFFFFFF)
val LightInputBorder = Color(0xFFCBD5E1)
val LightInputFocus = Color(0xFF0F6CBD)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextMuted = Color(0xFF475569)
val LightAccentPrimary = Color(0xFF0F6CBD)
val LightAccentPrimaryHover = Color(0xFF115EA3)
val LightAccentSuccess = Color(0xFF107C41)
val LightAccentDanger = Color(0xFFD13438)
val LightAccentWarning = Color(0xFFD97706)
val LightBadgeNeutralBg = Color(0xFFE0EDFF)
val LightBadgeNeutralText = Color(0xFF0F6CBD)
val LightBadgeNeutralBorder = Color(0xFFB9D9FF)
val LightBadgeSuccessBg = Color(0xFFDCFCE7)
val LightBadgeSuccessText = Color(0xFF15803D)
val LightBadgeSuccessBorder = Color(0xFFBBF7D0)
val LightBadgeDangerBg = Color(0xFFFEE2E2)
val LightBadgeDangerText = Color(0xFFB91C1C)
val LightBadgeDangerBorder = Color(0xFFFECACA)
val LightActiveTabBg = Color(0xFFE0EDFF)

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
    val accentPrimary: Color,
    val accentPrimaryHover: Color,
    val accentSuccess: Color,
    val accentDanger: Color,
    val accentWarning: Color,
    val badgeNeutralBg: Color,
    val badgeNeutralText: Color,
    val badgeNeutralBorder: Color,
    val badgeSuccessBg: Color,
    val badgeSuccessText: Color,
    val badgeSuccessBorder: Color,
    val badgeDangerBg: Color,
    val badgeDangerText: Color,
    val badgeDangerBorder: Color,
    val activeTabBg: Color,
    val activeTabText: Color
)

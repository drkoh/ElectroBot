package com.electrobot.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val DarkElectroColors = ElectroCustomColors(
    isDark = true,
    canvas = AppleDarkCanvas,
    headerBg = AppleDarkHeader,
    headerBorder = AppleDarkHeaderBorder,
    cardBg = AppleDarkCard,
    cardBorder = AppleDarkCardBorder,
    inputBg = AppleDarkInput,
    inputBorder = AppleDarkInputBorder,
    inputFocus = AppleDarkInputFocus,
    textPrimary = AppleDarkTextPrimary,
    textMuted = AppleDarkTextMuted,
    textTertiary = AppleDarkTextTertiary,
    accentPrimary = AppleDarkAccentPrimary,
    accentPrimaryHover = AppleDarkAccentPrimaryHover,
    accentSuccess = AppleDarkAccentSuccess,
    accentDanger = AppleDarkAccentDanger,
    accentWarning = AppleDarkAccentWarning,
    accentPurple = AppleDarkAccentPurple,
    accentTeal = AppleDarkAccentTeal,
    badgeNeutralBg = AppleDarkBadgeNeutralBg,
    badgeNeutralText = AppleDarkBadgeNeutralText,
    badgeNeutralBorder = AppleDarkBadgeNeutralBorder,
    badgeSuccessBg = AppleDarkBadgeSuccessBg,
    badgeSuccessText = AppleDarkBadgeSuccessText,
    badgeSuccessBorder = AppleDarkBadgeSuccessBorder,
    badgeDangerBg = AppleDarkBadgeDangerBg,
    badgeDangerText = AppleDarkBadgeDangerText,
    badgeDangerBorder = AppleDarkBadgeDangerBorder,
    badgeWarningBg = AppleDarkBadgeWarningBg,
    badgeWarningText = AppleDarkBadgeWarningText,
    badgeWarningBorder = AppleDarkBadgeWarningBorder,
    activeTabBg = AppleDarkActiveTabBg,
    activeTabText = AppleDarkAccentPrimary
)

val LightElectroColors = ElectroCustomColors(
    isDark = false,
    canvas = AppleLightCanvas,
    headerBg = AppleLightHeader,
    headerBorder = AppleLightHeaderBorder,
    cardBg = AppleLightCard,
    cardBorder = AppleLightCardBorder,
    inputBg = AppleLightInput,
    inputBorder = AppleLightInputBorder,
    inputFocus = AppleLightInputFocus,
    textPrimary = AppleLightTextPrimary,
    textMuted = AppleLightTextMuted,
    textTertiary = AppleLightTextTertiary,
    accentPrimary = AppleLightAccentPrimary,
    accentPrimaryHover = AppleLightAccentPrimaryHover,
    accentSuccess = AppleLightAccentSuccess,
    accentDanger = AppleLightAccentDanger,
    accentWarning = AppleLightAccentWarning,
    accentPurple = AppleLightAccentPurple,
    accentTeal = AppleLightAccentTeal,
    badgeNeutralBg = AppleLightBadgeNeutralBg,
    badgeNeutralText = AppleLightBadgeNeutralText,
    badgeNeutralBorder = AppleLightBadgeNeutralBorder,
    badgeSuccessBg = AppleLightBadgeSuccessBg,
    badgeSuccessText = AppleLightBadgeSuccessText,
    badgeSuccessBorder = AppleLightBadgeSuccessBorder,
    badgeDangerBg = AppleLightBadgeDangerBg,
    badgeDangerText = AppleLightBadgeDangerText,
    badgeDangerBorder = AppleLightBadgeDangerBorder,
    badgeWarningBg = AppleLightBadgeWarningBg,
    badgeWarningText = AppleLightBadgeWarningText,
    badgeWarningBorder = AppleLightBadgeWarningBorder,
    activeTabBg = AppleLightActiveTabBg,
    activeTabText = AppleLightAccentPrimary
)

val LocalElectroColors = staticCompositionLocalOf { DarkElectroColors }

object ElectroTheme {
    val colors: ElectroCustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalElectroColors.current
}

@Composable
fun ElectroBotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val customColors = if (darkTheme) DarkElectroColors else LightElectroColors
    val m3Scheme = if (darkTheme) {
        darkColorScheme(
            primary = AppleDarkAccentPrimary,
            onPrimary = TextWhite,
            background = AppleDarkCanvas,
            surface = AppleDarkCard,
            onBackground = AppleDarkTextPrimary,
            onSurface = AppleDarkTextPrimary
        )
    } else {
        lightColorScheme(
            primary = AppleLightAccentPrimary,
            onPrimary = TextWhite,
            background = AppleLightCanvas,
            surface = AppleLightCard,
            onBackground = AppleLightTextPrimary,
            onSurface = AppleLightTextPrimary
        )
    }

    CompositionLocalProvider(LocalElectroColors provides customColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content
        )
    }
}

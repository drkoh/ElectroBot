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
    canvas = DarkCanvas,
    headerBg = DarkHeader,
    headerBorder = DarkHeaderBorder,
    cardBg = DarkCard,
    cardBorder = DarkCardBorder,
    inputBg = DarkInput,
    inputBorder = DarkInputBorder,
    inputFocus = DarkInputFocus,
    textPrimary = DarkTextPrimary,
    textMuted = DarkTextMuted,
    accentPrimary = DarkAccentPrimary,
    accentPrimaryHover = DarkAccentPrimaryHover,
    accentSuccess = DarkAccentSuccess,
    accentDanger = DarkAccentDanger,
    accentWarning = DarkAccentWarning,
    badgeNeutralBg = DarkBadgeNeutralBg,
    badgeNeutralText = DarkBadgeNeutralText,
    badgeNeutralBorder = DarkBadgeNeutralBorder,
    badgeSuccessBg = DarkBadgeSuccessBg,
    badgeSuccessText = DarkBadgeSuccessText,
    badgeSuccessBorder = DarkBadgeSuccessBorder,
    badgeDangerBg = DarkBadgeDangerBg,
    badgeDangerText = DarkBadgeDangerText,
    badgeDangerBorder = DarkBadgeDangerBorder,
    activeTabBg = DarkActiveTabBg,
    activeTabText = DarkBadgeNeutralText
)

val LightElectroColors = ElectroCustomColors(
    isDark = false,
    canvas = LightCanvas,
    headerBg = LightHeader,
    headerBorder = LightHeaderBorder,
    cardBg = LightCard,
    cardBorder = LightCardBorder,
    inputBg = LightInput,
    inputBorder = LightInputBorder,
    inputFocus = LightInputFocus,
    textPrimary = LightTextPrimary,
    textMuted = LightTextMuted,
    accentPrimary = LightAccentPrimary,
    accentPrimaryHover = LightAccentPrimaryHover,
    accentSuccess = LightAccentSuccess,
    accentDanger = LightAccentDanger,
    accentWarning = LightAccentWarning,
    badgeNeutralBg = LightBadgeNeutralBg,
    badgeNeutralText = LightBadgeNeutralText,
    badgeNeutralBorder = LightBadgeNeutralBorder,
    badgeSuccessBg = LightBadgeSuccessBg,
    badgeSuccessText = LightBadgeSuccessText,
    badgeSuccessBorder = LightBadgeSuccessBorder,
    badgeDangerBg = LightBadgeDangerBg,
    badgeDangerText = LightBadgeDangerText,
    badgeDangerBorder = LightBadgeDangerBorder,
    activeTabBg = LightActiveTabBg,
    activeTabText = LightAccentPrimary
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
            primary = DarkAccentPrimary,
            onPrimary = TextWhite,
            background = DarkCanvas,
            surface = DarkCard,
            onBackground = DarkTextPrimary,
            onSurface = DarkTextPrimary
        )
    } else {
        lightColorScheme(
            primary = LightAccentPrimary,
            onPrimary = TextWhite,
            background = LightCanvas,
            surface = LightCard,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary
        )
    }

    CompositionLocalProvider(LocalElectroColors provides customColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content
        )
    }
}

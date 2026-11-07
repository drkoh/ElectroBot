package com.electrobot.app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val M3DarkColorScheme = darkColorScheme(
    primary = M3DarkPrimary,
    onPrimary = M3DarkOnPrimary,
    primaryContainer = M3DarkPrimaryContainer,
    onPrimaryContainer = M3DarkOnPrimaryContainer,
    secondary = M3DarkSecondary,
    onSecondary = M3DarkOnSecondary,
    secondaryContainer = M3DarkSecondaryContainer,
    onSecondaryContainer = M3DarkOnSecondaryContainer,
    tertiary = M3DarkTertiary,
    onTertiary = M3DarkOnTertiary,
    tertiaryContainer = M3DarkTertiaryContainer,
    onTertiaryContainer = M3DarkOnTertiaryContainer,
    error = M3DarkError,
    onError = M3DarkOnError,
    errorContainer = M3DarkErrorContainer,
    onErrorContainer = M3DarkOnErrorContainer,
    background = M3DarkBackground,
    onBackground = M3DarkOnBackground,
    surface = M3DarkSurface,
    onSurface = M3DarkOnSurface,
    surfaceVariant = M3DarkSurfaceVariant,
    onSurfaceVariant = M3DarkOnSurfaceVariant,
    surfaceContainerLowest = M3DarkSurfaceContainerLowest,
    surfaceContainerLow = M3DarkSurfaceContainerLow,
    surfaceContainer = M3DarkSurfaceContainer,
    surfaceContainerHigh = M3DarkSurfaceContainerHigh,
    surfaceContainerHighest = M3DarkSurfaceContainerHighest,
    outline = M3DarkOutline,
    outlineVariant = M3DarkOutlineVariant
)

val M3LightColorScheme = lightColorScheme(
    primary = M3LightPrimary,
    onPrimary = M3LightOnPrimary,
    primaryContainer = M3LightPrimaryContainer,
    onPrimaryContainer = M3LightOnPrimaryContainer,
    secondary = M3LightSecondary,
    onSecondary = M3LightOnSecondary,
    secondaryContainer = M3LightSecondaryContainer,
    onSecondaryContainer = M3LightOnSecondaryContainer,
    tertiary = M3LightTertiary,
    onTertiary = M3LightOnTertiary,
    tertiaryContainer = M3LightTertiaryContainer,
    onTertiaryContainer = M3LightOnTertiaryContainer,
    error = M3LightError,
    onError = M3LightOnError,
    errorContainer = M3LightErrorContainer,
    onErrorContainer = M3LightOnErrorContainer,
    background = M3LightBackground,
    onBackground = M3LightOnBackground,
    surface = M3LightSurface,
    onSurface = M3LightOnSurface,
    surfaceVariant = M3LightSurfaceVariant,
    onSurfaceVariant = M3LightOnSurfaceVariant,
    surfaceContainerLowest = M3LightSurfaceContainerLowest,
    surfaceContainerLow = M3LightSurfaceContainerLow,
    surfaceContainer = M3LightSurfaceContainer,
    surfaceContainerHigh = M3LightSurfaceContainerHigh,
    surfaceContainerHighest = M3LightSurfaceContainerHighest,
    outline = M3LightOutline,
    outlineVariant = M3LightOutlineVariant
)

val DarkElectroColors = ElectroCustomColors(
    isDark = true,
    canvas = M3DarkBackground,
    headerBg = M3DarkSurfaceContainer,
    headerBorder = M3DarkOutlineVariant,
    cardBg = M3DarkSurfaceContainerLow,
    cardBorder = M3DarkOutlineVariant,
    inputBg = M3DarkSurfaceContainerHigh,
    inputBorder = M3DarkOutline,
    inputFocus = M3DarkPrimary,
    textPrimary = M3DarkOnSurface,
    textMuted = M3DarkOnSurfaceVariant,
    textTertiary = M3DarkOutline,
    accentPrimary = M3DarkPrimary,
    accentPrimaryHover = M3DarkPrimaryContainer,
    accentSuccess = M3DarkSecondary,
    accentDanger = M3DarkError,
    accentWarning = M3DarkTertiary,
    accentPurple = M3DarkPrimary,
    accentTeal = M3DarkSecondary,
    badgeNeutralBg = M3DarkPrimaryContainer.copy(alpha = 0.5f),
    badgeNeutralText = M3DarkOnPrimaryContainer,
    badgeNeutralBorder = M3DarkPrimary.copy(alpha = 0.5f),
    badgeSuccessBg = M3DarkSecondaryContainer.copy(alpha = 0.6f),
    badgeSuccessText = M3DarkSecondary,
    badgeSuccessBorder = M3DarkSecondary.copy(alpha = 0.5f),
    badgeDangerBg = M3DarkErrorContainer.copy(alpha = 0.6f),
    badgeDangerText = M3DarkError,
    badgeDangerBorder = M3DarkError.copy(alpha = 0.5f),
    badgeWarningBg = M3DarkTertiaryContainer.copy(alpha = 0.6f),
    badgeWarningText = M3DarkTertiary,
    badgeWarningBorder = M3DarkTertiary.copy(alpha = 0.5f),
    activeTabBg = M3DarkPrimaryContainer,
    activeTabText = M3DarkOnPrimaryContainer
)

val LightElectroColors = ElectroCustomColors(
    isDark = false,
    canvas = M3LightBackground,
    headerBg = M3LightSurface,
    headerBorder = M3LightOutlineVariant,
    cardBg = M3LightSurfaceContainerLowest,
    cardBorder = M3LightOutlineVariant,
    inputBg = M3LightSurfaceContainerLow,
    inputBorder = M3LightOutline,
    inputFocus = M3LightPrimary,
    textPrimary = M3LightOnSurface,
    textMuted = M3LightOnSurfaceVariant,
    textTertiary = M3LightOutline,
    accentPrimary = M3LightPrimary,
    accentPrimaryHover = M3LightPrimaryContainer,
    accentSuccess = M3LightSecondary,
    accentDanger = M3LightError,
    accentWarning = M3LightTertiary,
    accentPurple = M3LightPrimary,
    accentTeal = M3LightSecondary,
    badgeNeutralBg = M3LightPrimaryContainer,
    badgeNeutralText = M3LightOnPrimaryContainer,
    badgeNeutralBorder = M3LightPrimary.copy(alpha = 0.4f),
    badgeSuccessBg = M3LightSecondaryContainer,
    badgeSuccessText = M3LightOnSecondaryContainer,
    badgeSuccessBorder = M3LightSecondary.copy(alpha = 0.4f),
    badgeDangerBg = M3LightErrorContainer,
    badgeDangerText = M3LightOnErrorContainer,
    badgeDangerBorder = M3LightError.copy(alpha = 0.4f),
    badgeWarningBg = M3LightTertiaryContainer,
    badgeWarningText = M3LightOnTertiaryContainer,
    badgeWarningBorder = M3LightTertiary.copy(alpha = 0.4f),
    activeTabBg = M3LightPrimaryContainer,
    activeTabText = M3LightOnPrimaryContainer
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
    val m3Scheme: ColorScheme = if (darkTheme) M3DarkColorScheme else M3LightColorScheme

    CompositionLocalProvider(LocalElectroColors provides customColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = Typography,
            content = content
        )
    }
}

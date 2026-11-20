package com.morad.housekeepermini.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Housekeeper Signature Burgundy & Warm Parchment Palette
private val BurgundyPrimaryLight = Color(0xFF9E2F27)
private val BurgundyAccentDark = Color(0xFFEAB19E)
private val BurgundyContainerLight = Color(0xFFEFE5E0)
private val BurgundyContainerDark = Color(0xFF97393A)

private val LightColors = lightColorScheme(
    primary = BurgundyPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = BurgundyContainerLight,
    onPrimaryContainer = Color(0xFF280705),
    secondary = Color(0xFF166C36),
    onSecondary = Color.White,
    background = Color(0xFFF9F4F1),
    onBackground = Color(0xFF260C10),
    surface = Color(0xFFFFFCFA),
    onSurface = Color(0xFF260C10),
    surfaceVariant = Color(0xFFF0E4DF),
    onSurfaceVariant = Color(0xFF6B4A45),
    error = Color(0xFFB91C1C)
)

private val DarkColors = darkColorScheme(
    primary = BurgundyAccentDark,
    onPrimary = Color(0xFF280705),
    primaryContainer = BurgundyContainerDark,
    onPrimaryContainer = Color(0xFFF9F4F1),
    secondary = Color(0xFF6FC798),
    onSecondary = Color(0xFF062E19),
    background = Color(0xFF260C10),
    onBackground = Color(0xFFF2E4E1),
    surface = Color(0xFF331D21),
    onSurface = Color(0xFFF2E4E1),
    surfaceVariant = Color(0xFF402529),
    onSurfaceVariant = Color(0xFFC9A49D),
    error = Color(0xFFFF6C56)
)

@Composable
fun HousekeeperMiniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

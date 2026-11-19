package com.morad.housekeepermini.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TealPrimary = Color(0xFF0E7490)
private val TealLight = Color(0xFF38BDF8)
private val TealContainerLight = Color(0xFFE0F2FE)
private val TealContainerDark = Color(0xFF0C4A6E)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF082F49),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFB91C1C)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF082F49),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171)
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

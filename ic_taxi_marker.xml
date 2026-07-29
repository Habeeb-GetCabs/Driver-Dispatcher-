package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RedAndWhiteColorScheme = lightColorScheme(
    primary = TaxiRedPrimary,
    onPrimary = TaxiWhite,
    primaryContainer = TaxiRedLight,
    onPrimaryContainer = TaxiWhite,
    secondary = TaxiRedDark,
    onSecondary = TaxiWhite,
    background = Color(0xFFC62828), // Red Background Canvas
    onBackground = TaxiWhite,       // White Letters
    surface = Color(0xFFB71C1C),    // Red Surface
    onSurface = TaxiWhite,          // White Letters
    surfaceVariant = Color(0xFFD32F2F),
    onSurfaceVariant = TaxiWhite,
    outline = Color(0xFFFFCDD2)
)

private val DarkRedAndWhiteColorScheme = darkColorScheme(
    primary = TaxiRedLight,
    onPrimary = TaxiWhite,
    primaryContainer = TaxiRedDark,
    onPrimaryContainer = TaxiWhite,
    secondary = TaxiRedPrimary,
    onSecondary = TaxiWhite,
    background = Color(0xFF1A0505),
    onBackground = TaxiWhite,
    surface = Color(0xFF2C0A0A),
    onSurface = TaxiWhite,
    surfaceVariant = Color(0xFF3E1010),
    onSurfaceVariant = TaxiWhite,
    outline = Color(0xFFE57373)
)

@Composable
fun GetTaxiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkRedAndWhiteColorScheme else RedAndWhiteColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

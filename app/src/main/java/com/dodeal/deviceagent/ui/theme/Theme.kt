package com.dodeal.deviceagent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DodealColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = Color(0xFFD8ECF6),
    onPrimaryContainer = BrandSecondary,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCED9DF),
    onSecondaryContainer = BrandSecondary,
    background = SurfaceLight,
    onBackground = BrandSecondary,
    surface = SurfaceLight,
    onSurface = BrandSecondary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceMuted,
    outline = OutlineLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun DodealTheme(content: @Composable () -> Unit) {
    // A managed company device: keep one consistent brand look regardless of system dark mode.
    MaterialTheme(colorScheme = DodealColors, content = content)
}

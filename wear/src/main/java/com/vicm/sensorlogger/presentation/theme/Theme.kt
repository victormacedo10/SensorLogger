package com.vicm.sensorlogger.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

internal val WearAppColorPalette = Colors(
    primary = NavyBlue,
    onPrimary = White,
    surface = White,
    onSurface = Black,
    background = White,
    onBackground = Black
)

@Composable
fun SensorLoggerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = WearAppColorPalette,
        content = content
    )
}
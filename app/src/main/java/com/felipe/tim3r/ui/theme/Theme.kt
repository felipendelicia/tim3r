package com.felipe.tim3r.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TimerOrange = Color(0xFFFF5A1F)
val TimerGreen = Color(0xFFB9F227)
val TimerBlue = Color(0xFF39D7FF)
val TimerBackground = Color(0xFF0A0A0B)
val TimerSurface = Color(0xFF171719)
val TimerSurfaceHigh = Color(0xFF242427)
val TimerMuted = Color(0xFFA5A5AA)

private val Tim3rColors = darkColorScheme(
    primary = TimerOrange,
    onPrimary = Color.Black,
    secondary = TimerGreen,
    onSecondary = Color.Black,
    tertiary = TimerBlue,
    background = TimerBackground,
    onBackground = Color(0xFFF7F7F7),
    surface = TimerSurface,
    onSurface = Color(0xFFF7F7F7),
    surfaceVariant = TimerSurfaceHigh,
    onSurfaceVariant = TimerMuted,
    outline = Color(0xFF39393D),
)

@Composable
fun Tim3rTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Tim3rColors,
        content = content,
    )
}

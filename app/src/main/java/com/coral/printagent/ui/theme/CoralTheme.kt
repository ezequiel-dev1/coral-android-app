package com.coral.printagent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CoralBlue = Color(0xFF3551A3)
val CoralSalmon = Color(0xFFF3805C)
private val SalmonLight = Color(0xFFFFE3DA)
private val BlueLight = Color(0xFFDCE3F7)

private val Scheme = lightColorScheme(
    primary = CoralBlue,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    onPrimaryContainer = CoralBlue,
    secondary = CoralSalmon,
    onSecondary = Color(0xFF3A1206),
    secondaryContainer = SalmonLight,
    onSecondaryContainer = Color(0xFF5A1D0C),
    tertiary = CoralSalmon,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF1B1F2E),
    onSurface = Color(0xFF1B1F2E),
    error = Color(0xFFB3261E),
)

@Composable
fun CoralTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

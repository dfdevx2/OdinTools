package de.langerhans.odintools.ui.theme

import androidx.compose.ui.graphics.Color

data class ConsoleTheme(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val text: Color
)

val RetroDarkTheme = ConsoleTheme(
    background = Color(0xFF0F0F13),
    surface = Color(0xFF1C1C24),
    primary = Color(0xFFE5002B),
    text = Color(0xFFF3F4F6)
)

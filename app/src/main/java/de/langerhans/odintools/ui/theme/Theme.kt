package de.langerhans.odintools.ui.theme

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import de.langerhans.odintools.R

val ConsoleFont = FontFamily(Font(R.font.outfit_regular))

data class ConsoleTheme(
    val name: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val text: Color,
    val isDynamic: Boolean = false,
    val fontFamily: FontFamily = ConsoleFont
)

val DynamicTheme = ConsoleTheme("Dinâmico (Papel de Parede)", Color.Black, Color.DarkGray, Color.White, Color.White, isDynamic = true)
val OdinTheme = ConsoleTheme("Odin OS", Color(0xFF0F0F13), Color(0xFF1C1C24), Color(0xFFE5002B), Color(0xFFF3F4F6))
val CyberpunkTheme = ConsoleTheme("Cyberpunk", Color(0xFF09090B), Color(0xFF13131A), Color(0xFFFCEE0A), Color(0xFF00FFD1))
val SnesTheme = ConsoleTheme("SNES", Color(0xFFE5E6EA), Color(0xFFD0D3D9), Color(0xFF5A4D9C), Color(0xFF333333))
val NesTheme = ConsoleTheme("NES", Color(0xFF808080), Color(0xFF000000), Color(0xFFE60012), Color(0xFFFFFFFF))
val XboxTheme = ConsoleTheme("Xbox", Color(0xFF101010), Color(0xFF1A1A1A), Color(0xFF107C10), Color(0xFFFFFFFF))
val SteamTheme = ConsoleTheme("Steam OS", Color(0xFF171A21), Color(0xFF2A475E), Color(0xFF66C0F4), Color(0xFFC7D5E0))
val PlayStationTheme = ConsoleTheme("PlayStation", Color(0xFF000033), Color(0xFF003399), Color(0xFF0066CC), Color(0xFFFFFFFF))

val AvailableThemes = listOf(DynamicTheme, OdinTheme, CyberpunkTheme, SnesTheme, NesTheme, XboxTheme, SteamTheme, PlayStationTheme)

@Composable
fun getResolvedTheme(theme: ConsoleTheme): ConsoleTheme {
    if (theme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicColors = dynamicDarkColorScheme(LocalContext.current)
        return theme.copy(
            background = dynamicColors.background,
            surface = dynamicColors.surfaceVariant,
            primary = dynamicColors.primary,
            text = dynamicColors.onBackground
        )
    }
    return theme
}

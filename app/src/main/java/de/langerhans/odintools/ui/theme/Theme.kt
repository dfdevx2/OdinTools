package de.langerhans.odintools.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import de.langerhans.odintools.R

// Conectando as fontes que você baixou
val OrbitronFont = FontFamily(Font(R.font.orbitron_medium))
val PixelFont = FontFamily(Font(R.font.pressstart2p_regular))
val DefaultFont = FontFamily.Default

data class ConsoleTheme(
    val name: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val text: Color,
    val fontFamily: FontFamily
)

// Os 7 Temas do nosso OdinOS
val OdinTheme = ConsoleTheme("Odin OS", Color(0xFF0F0F13), Color(0xFF1C1C24), Color(0xFFE5002B), Color(0xFFF3F4F6), DefaultFont)
val CyberpunkTheme = ConsoleTheme("Cyberpunk", Color(0xFF09090B), Color(0xFF13131A), Color(0xFFFCEE0A), Color(0xFF00FFD1), OrbitronFont)
val SnesTheme = ConsoleTheme("SNES", Color(0xFFE5E6EA), Color(0xFFD0D3D9), Color(0xFF5A4D9C), Color(0xFF333333), PixelFont)
val NesTheme = ConsoleTheme("NES", Color(0xFF808080), Color(0xFF000000), Color(0xFFE60012), Color(0xFFFFFFFF), PixelFont)
val XboxTheme = ConsoleTheme("Xbox", Color(0xFF101010), Color(0xFF1A1A1A), Color(0xFF107C10), Color(0xFFFFFFFF), OrbitronFont)
val SteamTheme = ConsoleTheme("Steam OS", Color(0xFF171A21), Color(0xFF2A475E), Color(0xFF66C0F4), Color(0xFFC7D5E0), DefaultFont)
val PlayStationTheme = ConsoleTheme("PlayStation", Color(0xFF000033), Color(0xFF003399), Color(0xFF0066CC), Color(0xFFFFFFFF), OrbitronFont)

val AvailableThemes = listOf(OdinTheme, CyberpunkTheme, SnesTheme, NesTheme, XboxTheme, SteamTheme, PlayStationTheme)

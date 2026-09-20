package de.langerhans.odintools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.langerhans.odintools.ui.theme.AvailableThemes
import de.langerhans.odintools.ui.theme.ConsoleTheme

@Composable
fun WelcomeScreen(
    theme: ConsoleTheme,
    currentThemeIndex: Int,
    currentLanguage: String,
    bgmEnabled: Boolean,
    bgmVolume: Float,
    sfxEnabled: Boolean,
    sfxVolume: Float,
    onThemeChange: (Int) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBgmToggle: (Boolean) -> Unit,
    onBgmVolume: (Float) -> Unit,
    onSfxToggle: (Boolean) -> Unit,
    onSfxVolume: (Float) -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var expandedLang by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf(false) }

    val isEn = currentLanguage == "English (US)"

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surface.copy(alpha = 0.9f))
                .border(1.dp, theme.primary, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEn) "WELCOME TO ODIN HUB" else "BEM-VINDO AO ODIN HUB",
                fontSize = 22.sp,
                fontFamily = theme.fontFamily,
                fontWeight = FontWeight.Black,
                color = theme.primary,
                letterSpacing = 2.sp
            )

            Text(
                text = if (isEn) "Set up your initial preferences to start" else "Configure suas preferências iniciais para começar",
                fontSize = 13.sp,
                fontFamily = theme.fontFamily,
                color = theme.text.copy(alpha = 0.7f)
            )

            HorizontalDivider(color = theme.text.copy(alpha = 0.2f))

            when (step) {
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(if (isEn) "System Language" else "Idioma do Sistema", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Box {
                            Button(onClick = { expandedLang = true }, colors = ButtonDefaults.buttonColors(containerColor = theme.background), modifier = Modifier.fillMaxWidth()) {
                                Text(currentLanguage, color = theme.text, fontFamily = theme.fontFamily)
                            }
                            DropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }) {
                                listOf("Português (PT-BR)", "English (US)").forEach { lang ->
                                    DropdownMenuItem(text = { Text(lang) }, onClick = { onLanguageChange(lang); expandedLang = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (isEn) "Visual Theme" else "Tema Visual", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Box {
                            Button(onClick = { expandedTheme = true }, colors = ButtonDefaults.buttonColors(containerColor = theme.background), modifier = Modifier.fillMaxWidth()) {
                                Text(AvailableThemes[currentThemeIndex].name, color = theme.text, fontFamily = theme.fontFamily)
                            }
                            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                                AvailableThemes.forEachIndexed { idx, t ->
                                    DropdownMenuItem(text = { Text(t.name) }, onClick = { onThemeChange(idx); expandedTheme = false })
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isEn) "Background Music (BGM)" else "Música de Fundo (BGM)", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Switch(checked = bgmEnabled, onCheckedChange = onBgmToggle)
                        }
                        Slider(value = bgmVolume, onValueChange = onBgmVolume, enabled = bgmEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isEn) "Sound Effects (SFX)" else "Efeitos Sonoros (SFX)", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Switch(checked = sfxEnabled, onCheckedChange = onSfxToggle)
                        }
                        Slider(value = sfxVolume, onValueChange = onSfxVolume, enabled = sfxEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) {
                    Button(onClick = { step-- }, colors = ButtonDefaults.buttonColors(containerColor = theme.background)) {
                        Text(if (isEn) "Back" else "Voltar", color = theme.text, fontFamily = theme.fontFamily)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (step < 1) step++ else onFinish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) {
                    Text(if (step < 1) (if (isEn) "Next" else "Avançar") else (if (isEn) "START BOOT" else "INICIAR BOOT"), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = theme.fontFamily)
                }
            }
        }
    }
}
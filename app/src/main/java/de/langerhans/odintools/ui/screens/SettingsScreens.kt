package de.langerhans.odintools.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import de.langerhans.odintools.R
import de.langerhans.odintools.main.MainUiModel
import de.langerhans.odintools.main.MainViewModel
import de.langerhans.odintools.tools.DeviceType.ODIN2
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.ui.composables.*
import de.langerhans.odintools.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    navigateToOverrideList: () -> Unit
) {
    val uiState: MainUiModel by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Estados visuais
    var currentThemeIndex by remember { mutableIntStateOf(0) }
    val currentTheme = AvailableThemes[currentThemeIndex]

    // Estados de Áudio
    var bgmEnabled by remember { mutableStateOf(true) }
    var bgmVolume by remember { mutableFloatStateOf(0.3f) }
    var sfxEnabled by remember { mutableStateOf(true) }
    var sfxVolume by remember { mutableFloatStateOf(0.8f) }

    val context = LocalContext.current
    val bgmPlayer = remember { MediaPlayer.create(context, R.raw.bgm_1).apply { isLooping = true } }

    fun playSfx(resId: Int) {
        if (sfxEnabled) {
            MediaPlayer.create(context, resId)?.apply {
                setVolume(sfxVolume, sfxVolume)
                setOnCompletionListener { release() }
                start()
            }
        }
    }

    // Gerenciamento BGM
    LaunchedEffect(bgmEnabled, bgmVolume) {
        bgmPlayer.setVolume(bgmVolume, bgmVolume)
        if (bgmEnabled && !bgmPlayer.isPlaying) bgmPlayer.start()
        else if (!bgmEnabled && bgmPlayer.isPlaying) bgmPlayer.pause()
    }
    DisposableEffect(Unit) { onDispose { bgmPlayer.release() } }

    if (uiState.showPServerNotAvailableDialog) PServerNotAvailableDialog()
    else if (uiState.showIncompatibleDeviceDialog) NotAnOdinDialog { viewModel.incompatibleDeviceDialogDismissed() }

    Box(modifier = Modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            ConsoleMenuBar(
                selectedTab = selectedTab, theme = currentTheme,
                onTabSelected = {
                    if (selectedTab != it) { playSfx(R.raw.sfx_nav); selectedTab = it }
                }
            )

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                when (selectedTab) {
                    0 -> PerformancePanel(uiState, viewModel, currentTheme, navigateToOverrideList) { playSfx(R.raw.sfx_select) }
                    1 -> DisplayPanel(currentTheme) { playSfx(R.raw.sfx_select) }
                    2 -> ControlsPanel(uiState, viewModel, currentTheme) { playSfx(R.raw.sfx_select) }
                    3 -> SystemPanel(
                        theme = currentTheme,
                        currentThemeIndex = currentThemeIndex,
                        bgmEnabled = bgmEnabled, bgmVolume = bgmVolume,
                        sfxEnabled = sfxEnabled, sfxVolume = sfxVolume,
                        playClick = { playSfx(R.raw.sfx_select) },
                        onThemeChange = { currentThemeIndex = it },
                        onBgmToggle = { bgmEnabled = it }, onBgmVolume = { bgmVolume = it },
                        onSfxToggle = { sfxEnabled = it }, onSfxVolume = { sfxVolume = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ConsoleMenuBar(selectedTab: Int, theme: ConsoleTheme, onTabSelected: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ConsoleTabItem(0, "PERFORMANCE", R.drawable.ic_sliders, selectedTab, theme, onTabSelected)
        ConsoleTabItem(1, "DISPLAY", R.drawable.ic_palette, selectedTab, theme, onTabSelected)
        ConsoleTabItem(2, "CONTROLES", R.drawable.ic_gamepad, selectedTab, theme, onTabSelected)
        ConsoleTabItem(3, "SISTEMA", R.drawable.ic_app_settings, selectedTab, theme, onTabSelected)
    }
}

@Composable
fun ConsoleTabItem(index: Int, title: String, iconResId: Int, selectedTab: Int, theme: ConsoleTheme, onClick: (Int) -> Unit) {
    val isSelected = selectedTab == index
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else if (isSelected) 1.1f else 1.0f, label = "tabScale")
    val color by animateColorAsState(if (isSelected) theme.primary else theme.text.copy(alpha = 0.4f), label = "tabColor")

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale).clickable(interactionSource = interactionSource, indication = null) { onClick(index) }.padding(8.dp)) {
        Icon(painterResource(iconResId), contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = color, fontSize = 12.sp, fontFamily = theme.fontFamily, fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.height(3.dp).width(24.dp).clip(RoundedCornerShape(50)).background(if (isSelected) theme.primary else Color.Transparent))
    }
}

// ==========================================
// ABAS DA INTERFACE
// ==========================================

@Composable
fun DisplayPanel(theme: ConsoleTheme, playClick: () -> Unit) {
    var satValue by remember { mutableFloatStateOf(1.0f) }
    var tempValue by remember { mutableFloatStateOf(6500f) }
    var expandedProfile by remember { mutableStateOf(false) }
    val profiles = listOf("Nativo", "Vibrante", "Cinema", "Filme")
    var selectedProfile by remember { mutableStateOf(profiles[0]) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Calibração de Tela", theme)

        ConsoleCard("Ajustes Manuais", "Saturação e Temperatura de cor", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Saturação: ${"%.1f".format(satValue)}", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = satValue, onValueChange = { satValue = it }, valueRange = 0.0f..2.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))

                Spacer(modifier = Modifier.height(8.dp))

                Text("Temperatura: ${tempValue.toInt()}K", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 4000f..9000f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleCard("Perfis de Imagem", selectedProfile, theme, { expandedProfile = true; playClick() }) {
            DropdownMenu(expanded = expandedProfile, onDismissRequest = { expandedProfile = false }, modifier = Modifier.background(theme.surface)) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile, color = theme.text, fontFamily = theme.fontFamily) },
                        onClick = {
                            selectedProfile = profile
                            expandedProfile = false
                            playClick()
                            // Aplicaríamos o reshade aqui
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ControlsPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, playClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Mapeamento", theme)

        ConsoleCard("Atalhos do Sistema", "Comportamento geral", theme, playClick) {
            SwitchPreference(icon = R.drawable.ic_home, title = R.string.singlePressHome, description = R.string.singlePressHomeDescription, state = uiState.singlePressHomeEnabled) { playClick(); viewModel.updateSinglePressHomePreference(it) }
        }

        if (uiState.deviceType == ODIN2) {
            ConsoleCard("Botões Traseiros (Macro)", "Mapear M1 e M2", theme, playClick) {
                TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m1Button, description = R.string.remapButtonDescription) { playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M1_VALUE) }
                TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m2Button, description = R.string.remapButtonDescription) { playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M2_VALUE) }
            }
        }
    }
}

@Composable
fun SystemPanel(
    theme: ConsoleTheme, currentThemeIndex: Int,
    bgmEnabled: Boolean, bgmVolume: Float, sfxEnabled: Boolean, sfxVolume: Float,
    playClick: () -> Unit, onThemeChange: (Int) -> Unit,
    onBgmToggle: (Boolean) -> Unit, onBgmVolume: (Float) -> Unit,
    onSfxToggle: (Boolean) -> Unit, onSfxVolume: (Float) -> Unit
) {
    var expandedTheme by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        ConsoleSectionHeader("Personalização UI", theme)
        ConsoleCard("Tema do Console", AvailableThemes[currentThemeIndex].name, theme, { expandedTheme = true; playClick() }) {
            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }, modifier = Modifier.background(theme.surface)) {
                AvailableThemes.forEachIndexed { index, consoleTheme ->
                    DropdownMenuItem(
                        text = { Text(consoleTheme.name, color = if (currentThemeIndex == index) theme.primary else theme.text, fontFamily = theme.fontFamily) },
                        onClick = { onThemeChange(index); expandedTheme = false; playClick() }
                    )
                }
            }
        }

        ConsoleSectionHeader("Mixer de Áudio", theme)
        ConsoleCard("Música de Fundo (BGM)", "Volume: ${(bgmVolume * 100).toInt()}%", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Habilitar Música", color = theme.text, fontFamily = theme.fontFamily)
                    Switch(checked = bgmEnabled, onCheckedChange = { onBgmToggle(it); playClick() }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.5f)))
                }
                Slider(value = bgmVolume, onValueChange = { onBgmVolume(it) }, enabled = bgmEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleCard("Efeitos Sonoros (SFX)", "Volume: ${(sfxVolume * 100).toInt()}%", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Habilitar SFX", color = theme.text, fontFamily = theme.fontFamily)
                    Switch(checked = sfxEnabled, onCheckedChange = { onSfxToggle(it); playClick() }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.5f)))
                }
                Slider(value = sfxVolume, onValueChange = { onSfxVolume(it) }, enabled = sfxEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleSectionHeader("Sobre", theme)
        ConsoleCard("OdinTools OS", "Versão 1.3.1 - Por Langerhans & SeuNome", theme, playClick) {
            TriggerPreference(icon = R.drawable.ic_file_save, title = R.string.dumpLogToFile, description = R.string.dumpLogToFileDescription) { playClick() }
            TriggerPreference(icon = R.drawable.ic_app_settings, title = R.string.unknown, description = R.string.unknown) { /* Apoie / Verificar Update */ }
        }
    }
}

@Composable
fun PerformancePanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, navigateToOverrideList: () -> Unit, playClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("AutoTDP & Frequências", theme)
        ConsoleCard(stringResource(R.string.appOverrides), stringResource(R.string.appOverridesDescription), theme, playClick) {
            SwitchableTriggerPreference(icon = R.drawable.ic_app_settings, title = R.string.appOverrides, description = R.string.appOverridesDescription, state = uiState.appOverridesEnabled, onClick = { playClick(); navigateToOverrideList() }) { playClick(); viewModel.appOverridesEnabled(it) }
        }
    }
}

// ==========================================
// COMPONENTES BASE
// ==========================================

@Composable
fun ConsoleSectionHeader(title: String, theme: ConsoleTheme) {
    Text(title.uppercase(), fontSize = 14.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = theme.text.copy(alpha = 0.5f), letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp).padding(top = 8.dp))
}

@Composable
fun ConsoleCard(title: String, subtitle: String, theme: ConsoleTheme, playClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth().clickable { playClick() }) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(title, fontSize = 18.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = theme.text, modifier = Modifier.padding(horizontal = 16.dp))
            Text(subtitle, fontSize = 13.sp, fontFamily = theme.fontFamily, color = theme.text.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
            HorizontalDivider(color = theme.background, thickness = 2.dp)
            content()
        }
    }
}

package de.langerhans.odintools.ui.screens

import android.media.MediaPlayer
import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import de.langerhans.odintools.R
import de.langerhans.odintools.main.MainUiModel
import de.langerhans.odintools.main.MainViewModel
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.ui.composables.*
import de.langerhans.odintools.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(viewModel: MainViewModel = hiltViewModel(), navigateToOverrideList: () -> Unit) {
    val uiState: MainUiModel by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    var isFirstRun by remember { mutableStateOf(true) }
    var showBootAnimation by remember { mutableStateOf(isFirstRun) }

    var currentThemeIndex by remember { mutableIntStateOf(1) }
    var useAmoledBlack by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf("Português (PT-BR)") }

    val rawTheme = AvailableThemes[currentThemeIndex]
    val finalTheme = getResolvedTheme(rawTheme, useAmoledBlack)

    var bgmEnabled by remember { mutableStateOf(true) }
    var bgmVolume by remember { mutableFloatStateOf(0.3f) }
    var sfxEnabled by remember { mutableStateOf(true) }
    var sfxVolume by remember { mutableFloatStateOf(0.8f) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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

    LaunchedEffect(bgmEnabled, bgmVolume, showBootAnimation) {
        bgmPlayer.setVolume(bgmVolume, bgmVolume)
        if (bgmEnabled && !bgmPlayer.isPlaying && !showBootAnimation) bgmPlayer.start()
        else if ((!bgmEnabled || showBootAnimation) && bgmPlayer.isPlaying) bgmPlayer.pause()
    }
    DisposableEffect(Unit) { onDispose { bgmPlayer.release() } }

    if (showBootAnimation) {
        BootAndWelcomeScreen(
            theme = finalTheme,
            onFinish = {
                showBootAnimation = false
                isFirstRun = false
                playSfx(R.raw.sfx_select)
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(finalTheme.background)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_R1 -> { playSfx(R.raw.sfx_nav); selectedTab = (selectedTab + 1).coerceAtMost(3); true }
                        KeyEvent.KEYCODE_BUTTON_L1 -> { playSfx(R.raw.sfx_nav); selectedTab = (selectedTab - 1).coerceAtLeast(0); true }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {

            Text(
                text = "ODIN HUB",
                fontSize = 28.sp,
                fontFamily = finalTheme.fontFamily,
                fontWeight = FontWeight.Black,
                color = finalTheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp)
            )

            ConsoleMenuBar(selectedTab = selectedTab, theme = finalTheme) {
                if (selectedTab != it) { playSfx(R.raw.sfx_nav); selectedTab = it }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(tween(300))
                    } else {
                        slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(tween(300))
                    }
                },
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 16.dp)
            ) { targetTab ->
                when (targetTab) {
                    0 -> PerformancePanel(uiState, viewModel, finalTheme, navigateToOverrideList) { playSfx(R.raw.sfx_select) }
                    1 -> DisplayPanel(finalTheme) { playSfx(R.raw.sfx_select) }
                    2 -> ControlsPanel(uiState, viewModel, finalTheme) { playSfx(R.raw.sfx_select) }
                    3 -> SystemPanel(
                        theme = finalTheme, currentThemeIndex = currentThemeIndex, currentLanguage = currentLanguage,
                        amoledBlack = useAmoledBlack, bgmEnabled = bgmEnabled, bgmVolume = bgmVolume, sfxEnabled = sfxEnabled, sfxVolume = sfxVolume,
                        playClick = { playSfx(R.raw.sfx_select) }, onThemeChange = { currentThemeIndex = it }, onLanguageChange = { currentLanguage = it },
                        onAmoledToggle = { useAmoledBlack = it; playSfx(R.raw.sfx_select) },
                        onBgmToggle = { bgmEnabled = it; playSfx(R.raw.sfx_select) }, onBgmVolume = { bgmVolume = it },
                        onSfxToggle = { sfxEnabled = it; playSfx(R.raw.sfx_select) }, onSfxVolume = { sfxVolume = it },
                        onReplayBoot = { showBootAnimation = true }
                    )
                }
            }
        }
    }
}

// ==========================================
// TELA DE BOOT (OOBE)
// ==========================================
@Composable
fun BootAndWelcomeScreen(theme: ConsoleTheme, onFinish: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }

    val textScale by animateFloatAsState(targetValue = when (stage) { 0 -> 0.8f; 1, 2 -> 1.0f; else -> 2.5f }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "scale")
    val textAlpha by animateFloatAsState(targetValue = when (stage) { 0 -> 0f; 1, 2 -> 1f; else -> 0f }, animationSpec = tween(800), label = "alpha")
    val glowPosition by animateFloatAsState(targetValue = if (stage >= 2) 2000f else -500f, animationSpec = tween(1500, easing = LinearEasing), label = "glow")
    val welcomeAlpha by animateFloatAsState(targetValue = if (stage == 4) 1f else 0f, animationSpec = tween(1000), label = "welcomeAlpha")

    LaunchedEffect(Unit) {
        delay(300)
        stage = 1 // Aparece o texto suavemente
        delay(800)
        stage = 2 // Dispara o raio de luz passando pelas letras
        delay(1200)
        stage = 3 // Texto dá um zoom agressivo para a tela e some
        delay(600)
        stage = 4 // Revela a tela de Boas-Vindas
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (stage < 4) {
            Text(
                text = "ODIN HUB",
                fontSize = 56.sp,
                fontFamily = theme.fontFamily,
                fontWeight = FontWeight.Black,
                color = theme.primary.copy(alpha = textAlpha),
                letterSpacing = 10.sp,
                modifier = Modifier
                    .scale(textScale)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = textAlpha * 0.8f), Color.Transparent),
                                start = androidx.compose.ui.geometry.Offset(glowPosition, 0f),
                                end = androidx.compose.ui.geometry.Offset(glowPosition + 300f, 0f)
                            )
                        )
                    }
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(32.dp).graphicsLayer(alpha = welcomeAlpha)) {
                Text("Bem-vindo ao Odin Hub", fontSize = 32.sp, fontFamily = theme.fontFamily, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Sua central de jogos e desempenho definitiva.", fontSize = 16.sp, color = Color.LightGray, fontFamily = theme.fontFamily)

                Button(onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = theme.primary), modifier = Modifier.padding(top = 24.dp)) {
                    Text("ENTRAR NO SISTEMA", fontFamily = theme.fontFamily, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

// ==========================================
// ABAS E PAINEIS
// ==========================================

@Composable
fun ConsoleMenuBar(selectedTab: Int, theme: ConsoleTheme, onTabSelected: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ConsoleTabItem(0, "PERFORMANCE", R.drawable.ic_sliders, selectedTab, theme, onTabSelected)
        ConsoleTabItem(1, "DISPLAY", R.drawable.ic_palette, selectedTab, theme, onTabSelected)
        ConsoleTabItem(2, "CONTROLES", R.drawable.ic_gamepad, selectedTab, theme, onTabSelected)
        ConsoleTabItem(3, "SISTEMA", R.drawable.ic_app_settings, selectedTab, theme, onTabSelected)
    }
}

@Composable
fun ConsoleTabItem(index: Int, title: String, iconResId: Int, selectedTab: Int, theme: ConsoleTheme, onClick: (Int) -> Unit) {
    val isSelected = selectedTab == index
    val color by animateColorAsState(if (isSelected) theme.primary else theme.text.copy(alpha = 0.4f), label = "tabColor")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick(index) }.padding(8.dp)
    ) {
        Icon(painterResource(iconResId), contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = color, fontSize = 12.sp, fontFamily = theme.fontFamily, fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 1.sp)
        Box(modifier = Modifier.height(3.dp).width(24.dp).clip(RoundedCornerShape(50)).background(if (isSelected) theme.primary else Color.Transparent))
    }
}

@Composable
fun PerformancePanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, navigateToOverrideList: () -> Unit, playClick: () -> Unit) {
    var tdpValue by remember { mutableFloatStateOf(15f) }
    var cpuClock by remember { mutableFloatStateOf(3200f) }
    var gpuClock by remember { mutableFloatStateOf(800f) }
    var expandedFan by remember { mutableStateOf(false) }
    val fanModes = listOf("Smart", "Quiet", "Balanced", "Sport", "Full (Max)")
    var selectedFan by remember { mutableStateOf(fanModes[0]) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Limites de Hardware", theme)

        ConsoleCard("AutoTDP Dinâmico", "Controla o consumo máximo de energia (W)", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Limite Global: ${tdpValue.toInt()} W", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = tdpValue, onValueChange = { tdpValue = it }, valueRange = 5f..30f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleCard("Frequências Manuais (Clock)", "Ajuste individual de CPU e GPU", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Max CPU: ${cpuClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = cpuClock, onValueChange = { cpuClock = it }, valueRange = 1000f..4200f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Max GPU: ${gpuClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = gpuClock, onValueChange = { gpuClock = it }, valueRange = 300f..1100f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleSectionHeader("Refrigeração", theme)
        ConsoleCard("Controle da Ventoinha (Fan)", selectedFan, theme, { expandedFan = true; playClick() }) {
            DropdownMenu(expanded = expandedFan, onDismissRequest = { expandedFan = false }, modifier = Modifier.background(theme.surface)) {
                fanModes.forEach { mode -> DropdownMenuItem(text = { Text(mode, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { selectedFan = mode; expandedFan = false; playClick() }) }
            }
        }

        ConsoleCard("Overrides por Jogo", "Configurar regras específicas", theme, playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Habilitar Overrides", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.appOverridesEnabled, theme = theme, onCheckedChange = { viewModel.appOverridesEnabled(it); playClick() })
            }
            TriggerPreference(icon = R.drawable.ic_app_settings, title = R.string.appOverrides, description = R.string.appOverridesDescription) { playClick(); navigateToOverrideList() }
        }
    }
}

@Composable
fun DisplayPanel(theme: ConsoleTheme, playClick: () -> Unit) {
    var satValue by remember { mutableFloatStateOf(1.0f) }
    var tempValue by remember { mutableFloatStateOf(6500f) }
    var expandedProfile by remember { mutableStateOf(false) }
    val profiles = listOf("Nativo", "Vibrante", "Cinema", "Retrô")
    var selectedProfile by remember { mutableStateOf(profiles[0]) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Calibração de Tela", theme)
        ConsoleCard("Perfis de Imagem Global", selectedProfile, theme, { expandedProfile = true; playClick() }) {
            DropdownMenu(expanded = expandedProfile, onDismissRequest = { expandedProfile = false }, modifier = Modifier.background(theme.surface)) {
                profiles.forEach { profile -> DropdownMenuItem(text = { Text(profile, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { selectedProfile = profile; expandedProfile = false; playClick() }) }
            }
        }
        ConsoleCard("Ajustes Manuais", "Saturação e Temperatura", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Saturação: ${"%.1f".format(satValue)}", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = satValue, onValueChange = { satValue = it }, valueRange = 0.0f..2.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Temperatura: ${tempValue.toInt()}K", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 4000f..9000f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }
    }
}

@Composable
fun ControlsPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, playClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Mapeamento e Atalhos", theme)
        ConsoleCard("Botões de Sistema", "Comportamento geral", theme, playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Toque Único no Home", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.singlePressHomeEnabled, theme = theme, onCheckedChange = { viewModel.updateSinglePressHomePreference(it); playClick() })
            }
        }
        ConsoleCard("Botões Traseiros (Macro)", "Mapear M1 e M2", theme, playClick) {
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m1Button, description = R.string.remapButtonDescription) { playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M1_VALUE) }
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m2Button, description = R.string.remapButtonDescription) { playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M2_VALUE) }
        }
    }
}

@Composable
fun SystemPanel(
    theme: ConsoleTheme, currentThemeIndex: Int, currentLanguage: String, amoledBlack: Boolean,
    bgmEnabled: Boolean, bgmVolume: Float, sfxEnabled: Boolean, sfxVolume: Float,
    playClick: () -> Unit, onThemeChange: (Int) -> Unit, onLanguageChange: (String) -> Unit, onAmoledToggle: (Boolean) -> Unit,
    onBgmToggle: (Boolean) -> Unit, onBgmVolume: (Float) -> Unit, onSfxToggle: (Boolean) -> Unit, onSfxVolume: (Float) -> Unit,
    onReplayBoot: () -> Unit
) {
    var expandedLang by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        ConsoleSectionHeader("Idioma e Região", theme)
        ConsoleCard("Idioma do Sistema", currentLanguage, theme, { expandedLang = true; playClick() }) {
            DropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }, modifier = Modifier.background(theme.surface)) {
                listOf("Português (PT-BR)", "English (US)").forEach { lang ->
                    DropdownMenuItem(text = { Text(lang, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { onLanguageChange(lang); expandedLang = false; playClick() })
                }
            }
        }

        ConsoleSectionHeader("Personalização UI", theme)
        ConsoleCard("Tema do Console", AvailableThemes[currentThemeIndex].name, theme, { expandedTheme = true; playClick() }) {
            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }, modifier = Modifier.background(theme.surface)) {
                AvailableThemes.forEachIndexed { index, consoleTheme ->
                    DropdownMenuItem(text = { Text(consoleTheme.name, color = if (currentThemeIndex == index) theme.primary else theme.text, fontFamily = theme.fontFamily) }, onClick = { onThemeChange(index); expandedTheme = false; playClick() })
                }
            }
        }
        ConsoleCard("Preto AMOLED", "Fundo escuro absoluto (Adaptativo)", theme, { onAmoledToggle(!amoledBlack); playClick() }) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Forçar Preto AMOLED", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = amoledBlack, theme = theme, onCheckedChange = { onAmoledToggle(it); playClick() })
            }
        }

        ConsoleSectionHeader("Mixer de Áudio", theme)
        ConsoleCard("Música de Fundo (BGM)", "Volume: ${(bgmVolume * 100).toInt()}%", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Habilitar BGM", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = bgmEnabled, theme = theme, onCheckedChange = { onBgmToggle(it); playClick() })
                }
                Slider(value = bgmVolume, onValueChange = { onBgmVolume(it) }, enabled = bgmEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleCard("Efeitos Sonoros (SFX)", "Volume: ${(sfxVolume * 100).toInt()}%", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Habilitar SFX", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = sfxEnabled, theme = theme, onCheckedChange = { onSfxToggle(it); playClick() })
                }
                Slider(value = sfxVolume, onValueChange = { onSfxVolume(it) }, enabled = sfxEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleSectionHeader("Sobre o Sistema", theme)
        ConsoleCard("Odin Hub", "Versão 0.5 - Desenvolvido por Seu Nome", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { playClick() }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                        Text("GitHub", fontFamily = theme.fontFamily, color = Color.White)
                    }
                    Button(onClick = { playClick() }, colors = ButtonDefaults.buttonColors(containerColor = theme.background)) {
                        Text("Atualizações", fontFamily = theme.fontFamily, color = theme.text)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rever Animação de Inicialização", color = theme.primary, fontFamily = theme.fontFamily, modifier = Modifier.clickable { onReplayBoot() })
            }
        }
    }
}

// ==========================================
// COMPONENTES CUSTOMIZADOS (Estilo SteamOS)
// ==========================================

@Composable
fun ConsoleSectionHeader(title: String, theme: ConsoleTheme) {
    Text(title.uppercase(), fontSize = 14.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = theme.text.copy(alpha = 0.5f), letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp).padding(top = 8.dp))
}

@Composable
fun ConsoleCard(title: String, subtitle: String, theme: ConsoleTheme, playClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isFocused) {
        if (isFocused) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "cardScale")
    val glow by animateDpAsState(targetValue = if (isFocused) 12.dp else 0.dp, animationSpec = tween(200), label = "cardGlow")
    val borderColor by animateColorAsState(targetValue = if (isFocused) theme.primary else Color.Transparent, label = "cardBorder")

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(glow, RoundedCornerShape(12.dp), spotColor = theme.primary, ambientColor = theme.primary)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { playClick() }
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(title, fontSize = 18.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = if (isFocused) theme.primary else theme.text, modifier = Modifier.padding(horizontal = 16.dp))
            Text(subtitle, fontSize = 13.sp, fontFamily = theme.fontFamily, color = theme.text.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
            HorizontalDivider(color = theme.background, thickness = 2.dp)
            content()
        }
    }
}

@Composable
fun ConsoleToggle(checked: Boolean, theme: ConsoleTheme, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(targetValue = if (checked) 24.dp else 4.dp, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "toggleMove")
    val bgColor by animateColorAsState(targetValue = if (checked) theme.primary.copy(alpha = 0.3f) else theme.background, label = "toggleBg")
    val thumbColor by animateColorAsState(targetValue = if (checked) theme.primary else theme.text.copy(alpha = 0.5f), label = "toggleThumb")

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, thumbColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.padding(start = thumbOffset).size(16.dp).clip(RoundedCornerShape(4.dp)).background(thumbColor))
    }
}

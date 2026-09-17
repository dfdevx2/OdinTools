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

    // Estado do tema (no futuro, salvaremos isso na memória do aparelho)
    var currentThemeIndex by remember { mutableIntStateOf(0) }
    val currentTheme = AvailableThemes[currentThemeIndex]
    val context = LocalContext.current

    // Toca SFX rápido sem travar a interface
    fun playSfx(resId: Int) {
        MediaPlayer.create(context, resId)?.apply {
            setOnCompletionListener { release() }
            start()
        }
    }

    // Inicia a Música de Fundo (BGM)
    DisposableEffect(Unit) {
        val mediaPlayer = MediaPlayer.create(context, R.raw.bgm_1).apply {
            isLooping = true
            setVolume(0.2f, 0.2f) // Volume suave para não incomodar
            start()
        }
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    if (uiState.showPServerNotAvailableDialog) PServerNotAvailableDialog()
    else if (uiState.showIncompatibleDeviceDialog) NotAnOdinDialog { viewModel.incompatibleDeviceDialogDismissed() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            ConsoleMenuBar(
                selectedTab = selectedTab,
                theme = currentTheme,
                onTabSelected = {
                    if (selectedTab != it) {
                        playSfx(R.raw.sfx_nav)
                        selectedTab = it
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> PerformancePanel(uiState, viewModel, currentTheme, navigateToOverrideList) { playSfx(R.raw.sfx_select) }
                    1 -> DisplayPanel(viewModel, currentTheme) { playSfx(R.raw.sfx_select) }
                    2 -> ControlsPanel(uiState, viewModel, currentTheme) { playSfx(R.raw.sfx_select) }
                    3 -> SystemPanel(currentTheme, currentThemeIndex, onThemeChange = {
                        currentThemeIndex = it
                        playSfx(R.raw.sfx_select)
                    })
                }
            }
        }
    }
}

@Composable
fun ConsoleMenuBar(selectedTab: Int, theme: ConsoleTheme, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabScale"
    )
    val color by animateColorAsState(if (isSelected) theme.primary else theme.text.copy(alpha = 0.4f), label = "tabColor")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale).clickable(interactionSource = interactionSource, indication = null) { onClick(index) }.padding(8.dp)
    ) {
        Icon(painterResource(iconResId), contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = color, fontSize = 12.sp, fontFamily = theme.fontFamily, fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.height(3.dp).width(24.dp).clip(RoundedCornerShape(50)).background(if (isSelected) theme.primary else Color.Transparent))
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

@Composable
fun DisplayPanel(viewModel: MainViewModel, theme: ConsoleTheme, playClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Ajustes de Tela", theme)
        ConsoleCard(stringResource(R.string.saturation), stringResource(R.string.saturationDescription), theme, playClick) {
            TriggerPreference(icon = R.drawable.ic_palette, title = R.string.saturation, description = R.string.saturationDescription) { playClick(); viewModel.saturationClicked() }
        }
    }
}

@Composable
fun ControlsPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, playClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Mapeamento", theme)
        ConsoleCard("Botões de Sistema", "Comportamento de atalhos", theme, playClick) {
            SwitchPreference(icon = R.drawable.ic_home, title = R.string.singlePressHome, description = R.string.singlePressHomeDescription, state = uiState.singlePressHomeEnabled) { playClick(); viewModel.updateSinglePressHomePreference(it) }
        }
    }
}

@Composable
fun SystemPanel(theme: ConsoleTheme, currentThemeIndex: Int, onThemeChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader("Personalização", theme)

        ConsoleCard("Tema do Console", "Mude a aparência e a interface", theme, { }) {
            AvailableThemes.forEachIndexed { index, consoleTheme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(index) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = consoleTheme.name, fontFamily = theme.fontFamily, color = if (currentThemeIndex == index) theme.primary else theme.text)
                    if (currentThemeIndex == index) {
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(theme.primary))
                    }
                }
            }
        }
    }
}

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

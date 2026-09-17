package de.langerhans.odintools.ui.screens

import android.view.SoundEffectConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
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

// Paleta de Cores do Tema (Será conectada ao ViewModel depois para mudar globalmente)
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

@Composable
fun SettingsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    navigateToOverrideList: () -> Unit
) {
    val uiState: MainUiModel by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Por enquanto, travado no tema escuro retrô. Na próxima etapa, puxaremos a cor dinâmica.
    val currentTheme = RetroDarkTheme
    val view = LocalView.current

    if (uiState.showPServerNotAvailableDialog) PServerNotAvailableDialog()
    else if (uiState.showIncompatibleDeviceDialog) NotAnOdinDialog { viewModel.incompatibleDeviceDialogDismissed() }

    if (uiState.showSaturationDialog) {
        SaturationPreferenceDialog(
            initialValue = uiState.currentSaturation,
            onCancel = { viewModel.saturationDialogDismissed() },
            onSave = { viewModel.saveSaturation(it) },
        )
    }

    Scaffold(containerColor = currentTheme.background) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // Header: Barra de Navegação de Console
            ConsoleMenuBar(
                selectedTab = selectedTab,
                theme = currentTheme,
                onTabSelected = {
                    if (selectedTab != it) {
                        view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
                        selectedTab = it
                    }
                }
            )

            // Painel de Conteúdo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> PerformancePanel(uiState, viewModel, currentTheme, navigateToOverrideList)
                    1 -> DisplayPanel(uiState, viewModel, currentTheme)
                    2 -> ControlsPanel(uiState, viewModel, currentTheme)
                    3 -> SystemPanel(currentTheme)
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
            .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConsoleTabItem(0, "PERFORMANCE", Icons.Default.Speed, selectedTab, theme, onTabSelected)
        ConsoleTabItem(1, "DISPLAY", Icons.Default.DesktopWindows, selectedTab, theme, onTabSelected)
        ConsoleTabItem(2, "CONTROLES", Icons.Default.SportsEsports, selectedTab, theme, onTabSelected)
        ConsoleTabItem(3, "SISTEMA", Icons.Default.Settings, selectedTab, theme, onTabSelected)
    }
}

@Composable
fun ConsoleTabItem(index: Int, title: String, icon: ImageVector, selectedTab: Int, theme: ConsoleTheme, onClick: (Int) -> Unit) {
    val isSelected = selectedTab == index
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabScale"
    )

    val color by animateColorAsState(
        targetValue = if (isSelected) theme.primary else theme.text.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "tabColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick(index) }
            .padding(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = color,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(24.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isSelected) theme.primary else Color.Transparent)
        )
    }
}

@Composable
fun PerformancePanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, navigateToOverrideList: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConsoleSectionHeader("AutoTDP & Frequências (PULSE ENGINE)", theme)
        ConsoleCard("Overrides de Aplicativo", "Gerenciar perfis de energia por jogo", theme) {
            SwitchableTriggerPreference(
                icon = R.drawable.ic_app_settings,
                title = R.string.appOverrides,
                description = R.string.appOverridesDescription,
                state = uiState.appOverridesEnabled,
                onClick = navigateToOverrideList,
            ) { viewModel.appOverridesEnabled(it) }
        }
        // Os sliders e controles do Pulse virão para cá na próxima iteração
    }
}

@Composable
fun DisplayPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConsoleSectionHeader("Ajustes de Tela", theme)
        ConsoleCard("Cores e Saturação", "Calibração nativa do display AMOLED/LCD", theme) {
            TriggerPreference(
                icon = R.drawable.ic_palette,
                title = R.string.saturation,
                description = R.string.saturationDescription,
            ) { viewModel.saturationClicked() }
        }
    }
}

@Composable
fun ControlsPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConsoleSectionHeader("Mapeamento e Atalhos", theme)
        ConsoleCard("Botões de Sistema", "Comportamento do botão Home e atalhos traseiros", theme) {
            SwitchPreference(
                icon = R.drawable.ic_home,
                title = R.string.singlePressHome,
                description = R.string.singlePressHomeDescription,
                state = uiState.singlePressHomeEnabled,
            ) { viewModel.updateSinglePressHomePreference(it) }

            if (uiState.deviceType == ODIN2) {
                TriggerPreference(
                    icon = R.drawable.ic_gamepad,
                    title = R.string.m1Button,
                    description = R.string.remapButtonDescription,
                ) { viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M1_VALUE) }

                TriggerPreference(
                    icon = R.drawable.ic_gamepad,
                    title = R.string.m2Button,
                    description = R.string.remapButtonDescription,
                ) { viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M2_VALUE) }
            }
        }
    }
}

@Composable
fun SystemPanel(theme: ConsoleTheme) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConsoleSectionHeader("Personalização e Sobre", theme)
        ConsoleCard("Aparência Visual", "Modificar esquema de cores e comportamento da UI", theme) {
            TriggerPreference(icon = R.drawable.ic_palette, title = R.string.app_name, description = R.string.app_name) { /* Futuro seletor de tema */ }
        }
        ConsoleCard("OdinTools OS", "Informações e atualizações do sistema", theme) {
            TriggerPreference(icon = R.drawable.ic_file_save, title = R.string.dumpLogToFile, description = R.string.dumpLogToFileDescription) { /* Placeholder */ }
        }
    }
}

@Composable
fun ConsoleSectionHeader(title: String, theme: ConsoleTheme) {
    Text(
        text = title.uppercase(),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = theme.text.copy(alpha = 0.5f),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
    )
}

@Composable
fun ConsoleCard(title: String, subtitle: String, theme: ConsoleTheme, content: @Composable ColumnScope.() -> Unit) {
    val view = LocalView.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { view.playSoundEffect(SoundEffectConstants.CLICK) }
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = theme.text.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, bottom = 12.dp)
            )
            Divider(color = theme.background, thickness = 2.dp)
            content()
        }
    }
}

package de.langerhans.odintools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.langerhans.odintools.R
import de.langerhans.odintools.main.MainUiModel
import de.langerhans.odintools.main.MainViewModel
import de.langerhans.odintools.tools.DeviceType.ODIN2
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.ui.composables.*

@Composable
fun SettingsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    navigateToOverrideList: () -> Unit
) {
    val uiState: MainUiModel by viewModel.uiState.collectAsState()

    // O fundo dinâmico será puxado do tema global futuramente.
    // Por hora, aplicamos um fundo escuro moderno estilo Armoury Crate.
    val backgroundColor = Color(0xFF121212)
    val cardColor = Color(0xFF1E1E2E)

    // Diálogos de Alerta
    if (uiState.showPServerNotAvailableDialog) PServerNotAvailableDialog()
    else if (uiState.showIncompatibleDeviceDialog) NotAnOdinDialog { viewModel.incompatibleDeviceDialogDismissed() }

    Scaffold(
        topBar = { OdinTopAppBar(deviceVersion = uiState.deviceVersion) },
        containerColor = backgroundColor
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Painel: Desempenho e Aplicativos
            SettingsPanel(title = R.string.appOverrides, cardColor = cardColor) {
                SwitchableTriggerPreference(
                    icon = R.drawable.ic_app_settings,
                    title = R.string.appOverrides,
                    description = R.string.appOverridesDescription,
                    state = uiState.appOverridesEnabled,
                    onClick = navigateToOverrideList,
                ) { viewModel.appOverridesEnabled(it) }

                SwitchableTriggerPreference(
                    icon = R.drawable.ic_gamepad_docked,
                    title = R.string.videoOutputOverride,
                    description = R.string.videoOutputOverrideDescription,
                    state = uiState.videoOutputOverrideEnabled,
                    onClick = { viewModel.videoOutputOverrideClicked() },
                ) { viewModel.updateVideoOutputOverridePreference(it) }
            }

            // Painel: Controles e Botões
            SettingsPanel(title = R.string.quickSettings, cardColor = cardColor) {
                TriggerPreference(
                    icon = R.drawable.ic_face_buttons,
                    title = R.string.controllerStyle,
                    description = R.string.controllerStyleDesc,
                ) { viewModel.showControllerStylePreference() }

                TriggerPreference(
                    icon = R.drawable.ic_sliders,
                    title = R.string.l2r2mode,
                    description = R.string.l2r2modeDesc,
                ) { viewModel.showL2r2StylePreference() }

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

            // Painel: Hardware (Tela, Bateria e Motor de Vibração)
            SettingsPanel(title = R.string.display, cardColor = cardColor) {
                TriggerPreference(
                    icon = R.drawable.ic_palette,
                    title = R.string.saturation,
                    description = R.string.saturationDescription,
                ) { viewModel.saturationClicked() }

                if (uiState.deviceType == ODIN2) {
                    SwitchableTriggerPreference(
                        icon = R.drawable.ic_vibration,
                        title = R.string.vibrationStrength,
                        description = R.string.vibrationStrengthDescription,
                        state = uiState.vibrationEnabled,
                        onClick = { viewModel.vibrationClicked() },
                    ) { viewModel.updateVibrationPreference(it) }

                    SwitchableTriggerPreference(
                        icon = R.drawable.ic_electrical_services,
                        title = R.string.chargeLimit,
                        description = R.string.chargeLimitDescription,
                        state = uiState.chargeLimitEnabled,
                        onClick = { viewModel.chargeLimitClicked() },
                    ) { viewModel.updateChargeLimitPreference(it) }
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Componente visual para criar as "caixas" do menu estilo console
@Composable
fun SettingsPanel(title: Int, cardColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content
        )
    }
}

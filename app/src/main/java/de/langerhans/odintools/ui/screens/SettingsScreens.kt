package de.langerhans.odintools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    if (uiState.showPServerNotAvailableDialog) PServerNotAvailableDialog()
    else if (uiState.showIncompatibleDeviceDialog) NotAnOdinDialog { viewModel.incompatibleDeviceDialogDismissed() }

    if (uiState.showControllerStyleDialog) {
        CheckBoxDialogPreference(
            items = viewModel.controllerStyleOptions,
            title = R.string.controllerStyle,
            onCancel = { viewModel.hideControllerStylePreference() },
        ) {
            viewModel.updateControllerStyles(it)
            viewModel.hideControllerStylePreference()
        }
    }

    if (uiState.showL2r2StyleDialog) {
        CheckBoxDialogPreference(
            items = viewModel.l2r2StyleOptions,
            title = R.string.l2r2mode,
            onCancel = { viewModel.hideL2r2StylePreference() },
        ) {
            viewModel.updateL2r2Styles(it)
            viewModel.hideL2r2StylePreference()
        }
    }

    if (uiState.showSaturationDialog) {
        SaturationPreferenceDialog(
            initialValue = uiState.currentSaturation,
            onCancel = { viewModel.saturationDialogDismissed() },
            onSave = { viewModel.saveSaturation(it) },
        )
    }

    if (uiState.showVideoOutputOverrideDialog) {
        VideoOutputOverridePreferenceDialog(
            initialControllerStyle = uiState.videoOutputControllerStyle,
            initialL2R2Style = uiState.videoOutputL2R2Style,
            onCancel = { viewModel.videoOutputOverrideDialogDismissed() },
            onSave = { newControllerStyle, newL2R2Style ->
                viewModel.saveVideoOutputOverride(newControllerStyle, newL2R2Style)
            },
        )
    }

    if (uiState.showVibrationDialog) {
        VibrationPreferenceDialog(
            initialValue = uiState.currentVibration,
            onCancel = { viewModel.vibrationDialogDismissed() },
            onSave = { viewModel.saveVibration(it) },
        )
    }

    if (uiState.showRemapButtonDialog) {
        RemapButtonDialog(
            initialValue = uiState.currentButtonKeyCode,
            onCancel = { viewModel.remapButtonDialogDismissed() },
            onReset = { viewModel.resetButtonKeyCode(uiState.currentButtonSetting) },
            onSave = { viewModel.saveButtonKeyCode(uiState.currentButtonSetting, it) },
        )
    }

    if (uiState.showChargeLimitDialog) {
        ChargeLimitPreferenceDialog(
            initialValue = uiState.currentChargeLimit,
            onCancel = { viewModel.chargeLimitDialogDismissed() },
            onSave = { viewModel.saveChargeLimit(it) },
        )
    }

    Scaffold(topBar = { OdinTopAppBar(deviceVersion = uiState.deviceVersion) }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsHeader(R.string.appOverrides)
            SwitchableTriggerPreference(
                icon = R.drawable.ic_app_settings,
                title = R.string.appOverrides,
                description = R.string.appOverridesDescription,
                state = uiState.appOverridesEnabled,
                onClick = navigateToOverrideList,
            ) { newValue -> viewModel.appOverridesEnabled(newValue) }

            SwitchableTriggerPreference(
                icon = R.drawable.ic_gamepad_docked,
                title = R.string.videoOutputOverride,
                description = R.string.videoOutputOverrideDescription,
                state = uiState.videoOutputOverrideEnabled,
                onClick = { viewModel.videoOutputOverrideClicked() },
            ) { viewModel.updateVideoOutputOverridePreference(it) }

            SwitchPreference(
                icon = R.drawable.ic_more_time,
                title = R.string.overrideDelay,
                description = R.string.overrideDelayDescription,
                state = uiState.overrideDelayEnabled,
            ) { viewModel.overrideDelayEnabled(it) }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsHeader(R.string.quickSettings)
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

            Spacer(modifier = Modifier.height(16.dp))

            SettingsHeader(R.string.buttons)
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

            Spacer(modifier = Modifier.height(16.dp))

            SettingsHeader(name = R.string.display)
            TriggerPreference(
                icon = R.drawable.ic_palette,
                title = R.string.saturation,
                description = R.string.saturationDescription,
            ) { viewModel.saturationClicked() }

            if (uiState.deviceType == ODIN2) {
                Spacer(modifier = Modifier.height(16.dp))

                SettingsHeader(name = R.string.haptics)
                SwitchableTriggerPreference(
                    icon = R.drawable.ic_vibration,
                    title = R.string.vibrationStrength,
                    description = R.string.vibrationStrengthDescription,
                    state = uiState.vibrationEnabled,
                    onClick = { viewModel.vibrationClicked() },
                ) { viewModel.updateVibrationPreference(it) }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsHeader(R.string.battery)
                SwitchableTriggerPreference(
                    icon = R.drawable.ic_electrical_services,
                    title = R.string.chargeLimit,
                    description = R.string.chargeLimitDescription,
                    state = uiState.chargeLimitEnabled,
                    onClick = { viewModel.chargeLimitClicked() },
                ) { viewModel.updateChargeLimitPreference(it) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsHeader(R.string.debug)
            TriggerPreference(
                icon = R.drawable.ic_file_save,
                title = R.string.dumpLogToFile,
                description = R.string.dumpLogToFileDescription,
            ) { viewModel.dumpLogToFile() }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

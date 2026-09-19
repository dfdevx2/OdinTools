package de.langerhans.odintools.main

import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.tools.DeviceType

data class MainUiModel(
    val deviceType: DeviceType = DeviceType.ODIN2, // Corrigido de UNKNOWN para ODIN2
    val deviceVersion: String = "",
    val showIncompatibleDeviceDialog: Boolean = false,
    val showPServerNotAvailableDialog: Boolean = false,
    val singlePressHomeEnabled: Boolean = false,
    val showControllerStyleDialog: Boolean = false,
    val showL2r2StyleDialog: Boolean = false,
    val showSaturationDialog: Boolean = false,
    val currentSaturation: Float = 1.0f,
    val showVibrationDialog: Boolean = false,
    val vibrationEnabled: Boolean = false,
    val currentVibration: Int = 0,
    val showRemapButtonDialog: Boolean = false,
    val currentButtonSetting: String? = null,
    val currentButtonKeyCode: Int = 0,
    val showVideoOutputOverrideDialog: Boolean = false,
    val videoOutputOverrideEnabled: Boolean = false,
    val videoOutputControllerStyle: ControllerStyle? = null,
    val videoOutputL2R2Style: L2R2Style? = null,
    val appOverridesEnabled: Boolean = true,
    val overrideDelayEnabled: Boolean = false,
    val showChargeLimitDialog: Boolean = false,
    val chargeLimitEnabled: Boolean = false,
    val currentChargeLimit: ClosedRange<Int> = 20..80,

    // Variáveis do Cérebro de Performance
    val performanceProfile: String = "Smart",
    val useRootTarget: Boolean = true,
    val tdpValue: Float = 15f,
    val cpuPerfClock: Float = 3530f,
    val cpuPrimeClock: Float = 4320f,
    val gpuClock: Float = 1100f
)

// Isso resolve os 32 erros do MainViewModel
data class CheckboxPreferenceUiModel(
    val key: String,
    val text: Int,
    val checked: Boolean
)

package de.langerhans.odintools.main

import androidx.annotation.StringRes
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.tools.DeviceType

data class MainUiModel(
    val deviceType: DeviceType = DeviceType.ODIN2,
    val deviceVersion: String = "",
    val showIncompatibleDeviceDialog: Boolean = false,
    val singlePressHomeEnabled: Boolean = false,
    val showPServerNotAvailableDialog: Boolean = false,

    val showControllerStyleDialog: Boolean = false,
    val showL2r2StyleDialog: Boolean = false,

    val showSaturationDialog: Boolean = false,
    val currentSaturation: Float = 1.0f,
    val currentTemperature: Float = 6500f,

    val showVibrationDialog: Boolean = false,
    val vibrationEnabled: Boolean = false,
    val currentVibration: Int = 0,

    val showRemapButtonDialog: Boolean = false,
    val currentButtonSetting: String = "",
    val currentButtonKeyCode: Int = 0,

    val appOverridesEnabled: Boolean = false,
    val overrideDelayEnabled: Boolean = false,

    val showChargeLimitDialog: Boolean = false,
    val chargeLimitEnabled: Boolean = false,
    val currentChargeLimit: ClosedRange<Int> = 75..85,

    val showVideoOutputOverrideDialog: Boolean = false,
    val videoOutputOverrideEnabled: Boolean = false,
    val videoOutputControllerStyle: ControllerStyle = ControllerStyle.Unknown,
    val videoOutputL2R2Style: L2R2Style = L2R2Style.Unknown,

    // Estados Odin Hub - Performance
    val performanceProfile: String = "Stock",
    val tdpValue: Float = 15f,
    val cpuPerfClock: Float = 3530f,
    val cpuPrimeClock: Float = 4320f,
    val gpuClock: Float = 1100f,
    val activeLimitMode: String = "TDP",
    val savedCustomProfiles: List<String> = emptyList(),
    val showSaveProfileDialog: Boolean = false,
    val useRootTarget: Boolean = false,

    // Estados Odin Hub - Display
    val globalLsfgEnabled: Boolean = false,
    val globalSgsrEnabled: Boolean = false,
    val lsfgMultiplier: String = "2x",
    val lsfgFramePacing: Boolean = true,
    val lsfgQuality: Float = 1.0f,
    val sgsrMode: String = "Quality",
    val sgsrSharpness: Float = 0.5f,
    val reshadeProfile: String = "Native",
    val showFpsOverlay: Boolean = false, // Propriedade adicionada

    // Estados Odin Hub - Overlay e DLL
    val isDllImported: Boolean = false,
    val overlayEnabled: Boolean = false
)

data class CheckboxPreferenceUiModel(
    val key: String,
    @StringRes val text: Int,
    var checked: Boolean
)

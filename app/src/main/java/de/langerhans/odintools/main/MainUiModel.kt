package de.langerhans.odintools.main

import androidx.annotation.StringRes
import de.langerhans.odintools.data.CustomProfile
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

    val selectedThemeIndex: Int = 1,
    val useAmoledBlack: Boolean = false,

    // --- HARDWARE & PERFORMANCE STATES ---
    val performanceProfile: String = "Stock",
    val tdpValue: Float = 15f,
    val cpuPerfClock: Float = 3530f,
    val cpuPrimeClock: Float = 4320f,
    val gpuClock: Float = 1100f,
    val activeLimitMode: String = "TDP",
    val useRootTarget: Boolean = false,
    val fanMode: Int = 0,
    val customProfiles: List<CustomProfile> = emptyList(),

    // --- DISPLAY & UPSCALING STATES ---
    val globalLsfgEnabled: Boolean = false,
    val globalSgsrEnabled: Boolean = false,
    val lsfgMultiplier: String = "2x",
    val lsfgFramePacing: Boolean = true,
    val lsfgQuality: Float = 1.0f,
    val sgsrMode: String = "Quality",
    val sgsrSharpness: Float = 0.5f,
    val reshadeProfile: String = "Native",
    val showFpsOverlay: Boolean = false,
    val isDllImported: Boolean = false,

    // --- OVERLAY STATES ---
    val overlayEnabled: Boolean = false,
    val overlayHandleOpacity: Float = 0.5f,
    val overlayHandleWidth: Int = 22
)

data class CheckboxPreferenceUiModel(
    val key: String,
    @StringRes val text: Int,
    var checked: Boolean
)

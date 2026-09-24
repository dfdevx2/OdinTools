package com.dfdx047.odinhub.main

import com.dfdx047.odinhub.data.CustomProfile
import com.dfdx047.odinhub.models.HomeGestureConfig
import com.dfdx047.odinhub.models.MacroStep
import com.dfdx047.odinhub.models.TdpProfiles
import com.dfdx047.odinhub.tools.DeviceType
import com.dfdx047.odinhub.tools.hardware.ClockTables
import com.dfdx047.odinhub.tools.hardware.ThermalStatus

data class MainUiModel(
    val deviceType: DeviceType = DeviceType.ODIN2,
    val deviceVersion: String = "",
    val showIncompatibleDeviceDialog: Boolean = false,
    val showPServerNotAvailableDialog: Boolean = false,

    val currentSaturation: Float = 1.0f,
    val currentTemperature: Float = 6500f,

    val showRemapButtonDialog: Boolean = false,
    val showOverlayShortcutDialog: Boolean = false,
    val currentButtonSetting: String = "",
    val currentButtonKeyCode: Int = 0,
    val overlayShortcutKeyCode: Int = 0,

    /** Gestos do botão Home (substituem o antigo "Toque único no Home", que já existe nas definições da AYN). */
    val homeGestures: HomeGestureConfig = HomeGestureConfig(),
    /** Macros dos botões traseiros: estado de cada um e qual está aberto no editor ("m1"/"m2"). */
    val m1MacroEnabled: Boolean = false,
    val m1MacroSteps: List<MacroStep> = emptyList(),
    val m2MacroEnabled: Boolean = false,
    val m2MacroSteps: List<MacroStep> = emptyList(),
    val macroEditorFor: String? = null,
    val appOverridesEnabled: Boolean = false,

    val selectedThemeIndex: Int = 1,
    val useAmoledBlack: Boolean = false,

    val performanceProfile: String = "Stock",
    val tdpValue: Float = 15f,
    /** Perfil de TDP selecionado -- ver TdpProfiles ("custom" = nenhum chip destacado). */
    val tdpProfileId: String = TdpProfiles.ID_TRIPLE_A,
    /** Tabelas reais de frequências do SoC (MHz), para os chips de clocks. */
    val clockTables: ClockTables = ClockTables.EMPTY,
    /** Estado do limite térmico (via PServerBinder) -- ver ThermalManager. */
    val thermalStatus: ThermalStatus = ThermalStatus(),
    val cpuPerfClock: Float = 3530f,
    val cpuPrimeClock: Float = 4320f,
    val gpuClock: Float = 1100f,
    val activeLimitMode: String = "TDP",
    val fanMode: Int = 0,
    val customProfiles: List<CustomProfile> = emptyList(),

    val globalLsfgEnabled: Boolean = false,
    val globalSgsrEnabled: Boolean = false,
    val lsfgMultiplier: String = "2x",
    val lsfgFramePacing: Boolean = true,
    val lsfgPerformanceMode: Boolean = false,
    val sgsrMode: String = "Quality",
    val sgsrSharpness: Float = 0.5f,
    val reshadeProfile: String = "Native",
    val showFpsOverlay: Boolean = false,
    val isDllImported: Boolean = false,

    val overlayEnabled: Boolean = false,
    val overlayHandleOpacity: Float = 0.5f,
    val overlayHandleWidth: Int = 22,
    val overlayPanelOpacity: Float = 0.95f,

    val bgmEnabled: Boolean = true,
    val bgmVolume: Float = 0.5f,
    val sfxEnabled: Boolean = true,
    val sfxVolume: Float = 0.8f
)
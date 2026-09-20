package de.langerhans.odintools.main

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.R
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.ControllerStyle.Disconnect
import de.langerhans.odintools.models.ControllerStyle.Odin
import de.langerhans.odintools.models.ControllerStyle.Xbox
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.models.L2R2Style.Analog
import de.langerhans.odintools.models.L2R2Style.Both
import de.langerhans.odintools.models.L2R2Style.Digital
import de.langerhans.odintools.tools.DeviceType.ODIN2
import de.langerhans.odintools.tools.DeviceUtils
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.ShellExecutor
import de.langerhans.odintools.tools.hardware.DisplayManager
import de.langerhans.odintools.tools.hardware.LosslessManager
import de.langerhans.odintools.tools.hardware.PerformanceManager
import de.langerhans.odintools.services.GamingOverlayService
import de.langerhans.odintools.tools.hardware.VulkanNativeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceUtils: DeviceUtils,
    private val executor: ShellExecutor,
    private val settings: SettingsRepo,
    private val prefs: SharedPrefsRepo,
    private val performanceManager: PerformanceManager,
    private val displayManager: DisplayManager,
    private val losslessManager: LosslessManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiModel())
    val uiState: StateFlow<MainUiModel> = _uiState.asStateFlow()

    private var _controllerStyleOptions = getCurrentControllerStyles().toMutableStateList()
    val controllerStyleOptions: List<CheckboxPreferenceUiModel> get() = _controllerStyleOptions
    private var _l2r2StyleOptions = getCurrentL2r2Styles().toMutableStateList()
    val l2r2StyleOptions: List<CheckboxPreferenceUiModel> get() = _l2r2StyleOptions

    init {
        settings.applyRequiredSettings()
        val deviceType = deviceUtils.getDeviceType()

        // Critical safety reset: Clear global Vulkan layer property on launch to prevent system-wide crashes
        executor.executeAsRoot("setprop debug.vulkan.layers \"\"")
        executor.executeAsRoot("setprop debug.vulkan.layer.dir \"\"")

        _uiState.update {
            MainUiModel(
                deviceType = deviceType,
                deviceVersion = deviceUtils.getDeviceVersion(),
                showIncompatibleDeviceDialog = deviceType != ODIN2,
                singlePressHomeEnabled = !settings.preventPressHome,
                showPServerNotAvailableDialog = !executor.pServerAvailable,
                overrideDelayEnabled = prefs.overrideDelay,
                vibrationEnabled = settings.vibrationEnabled,
                chargeLimitEnabled = prefs.chargeLimitEnabled,
                videoOutputOverrideEnabled = prefs.videoOutputOverrideEnabled,

                currentSaturation = prefs.saturationOverride,
                currentTemperature = prefs.temperatureOverride,

                globalLsfgEnabled = prefs.globalLsfgEnabled,
                lsfgMultiplier = prefs.lsfgMultiplier,
                lsfgFramePacing = prefs.lsfgFramePacing,
                globalSgsrEnabled = prefs.globalSgsrEnabled,
                sgsrMode = prefs.sgsrMode,
                reshadeProfile = prefs.reshadeProfile,
                showFpsOverlay = prefs.showFpsOverlay,
                isDllImported = losslessManager.isDllImported
            )
        }

        // Push initial states to the C++ Vulkan Bridge
        VulkanNativeBridge.applyLsfg(prefs.globalLsfgEnabled, prefs.lsfgMultiplier, prefs.lsfgFramePacing)
        VulkanNativeBridge.applySgsr(prefs.globalSgsrEnabled, prefs.sgsrMode)
        VulkanNativeBridge.applyReshade(prefs.reshadeProfile, prefs.saturationOverride, prefs.temperatureOverride)
    }

    fun finishWelcomeSetup() {
        prefs.isFirstRun = false
    }

    fun isFirstRun(): Boolean = prefs.isFirstRun

    // Odin Hub - Overlay Sidebar
    fun toggleOverlay(enabled: Boolean) {
        _uiState.update { it.copy(overlayEnabled = enabled) }
        val intent = Intent(context, GamingOverlayService::class.java)
        if (enabled) {
            context.startService(intent)
        } else {
            context.stopService(intent)
        }
    }

    fun toggleFpsOverlay(enabled: Boolean) {
        prefs.showFpsOverlay = enabled
        _uiState.update { it.copy(showFpsOverlay = enabled) }
    }

    // Odin Hub - Performance
    fun updateLimitMode(mode: String) {
        _uiState.update { it.copy(activeLimitMode = mode) }
    }

    fun updatePerformanceProfile(profile: String) {
        _uiState.update { it.copy(performanceProfile = profile) }
        when (profile) {
            "Power Save" -> { updateTdp(5f); updateManualClocks(1735f, 2246f, 160f) }
            "Balanced" -> updateTdp(10f)
            "Triple A" -> updateTdp(15f)
            "Stock" -> { updateTdp(25f); updateManualClocks(3530f, 4320f, 1100f) }
        }
    }

    fun updateTdp(watts: Float) {
        _uiState.update { it.copy(tdpValue = watts) }
        performanceManager.applyDynamicTdp(watts)
    }

    fun updateManualClocks(perfClock: Float, primeClock: Float, gpuClock: Float) {
        _uiState.update { it.copy(cpuPerfClock = perfClock, cpuPrimeClock = primeClock, gpuClock = gpuClock) }
        performanceManager.applyAbsoluteClocks((perfClock * 1000).toLong(), (primeClock * 1000).toLong(), (gpuClock * 1000000).toLong())
    }

    // Odin Hub - Display & Vulkan Shaders
    fun updateGlobalLsfg(enabled: Boolean) {
        prefs.globalLsfgEnabled = enabled
        _uiState.update { it.copy(globalLsfgEnabled = enabled) }
        VulkanNativeBridge.applyLsfg(enabled, prefs.lsfgMultiplier, prefs.lsfgFramePacing)
    }

    fun updateLsfgOptions(multiplier: String, pacing: Boolean) {
        prefs.lsfgMultiplier = multiplier
        prefs.lsfgFramePacing = pacing
        _uiState.update { it.copy(lsfgMultiplier = multiplier, lsfgFramePacing = pacing) }
        VulkanNativeBridge.applyLsfg(prefs.globalLsfgEnabled, multiplier, pacing)
    }

    fun refreshDllStatus() {
        _uiState.update { it.copy(isDllImported = losslessManager.isDllImported) }
    }

    fun saveSaturation(newValue: Float) {
        prefs.saturationOverride = newValue
        displayManager.applySaturation(newValue)
        _uiState.update { it.copy(currentSaturation = newValue) }
    }

    fun saveTemperature(newValue: Float) {
        prefs.temperatureOverride = newValue
        displayManager.applyTemperature(newValue)
        _uiState.update { it.copy(currentTemperature = newValue) }
    }

    fun resetDisplayColors() {
        saveSaturation(1.0f)
        saveTemperature(6500f)
    }

    // Native OdinTools Functions
    fun incompatibleDeviceDialogDismissed() {
        _uiState.update { it.copy(showIncompatibleDeviceDialog = false) }
    }

    fun updateSinglePressHomePreference(newValue: Boolean) {
        settings.preventPressHome = !newValue
        _uiState.update { it.copy(singlePressHomeEnabled = newValue) }
    }

    fun showControllerStylePreference() {
        _controllerStyleOptions = getCurrentControllerStyles().toMutableStateList()
        _uiState.update { it.copy(showControllerStyleDialog = true) }
    }

    fun hideControllerStylePreference() {
        _uiState.update { it.copy(showControllerStyleDialog = false) }
    }

    private fun getCurrentControllerStyles(): List<CheckboxPreferenceUiModel> {
        val disabled = prefs.disabledControllerStyle
        return listOf(
            CheckboxPreferenceUiModel(Xbox.id, R.string.xbox, disabled != Xbox.id),
            CheckboxPreferenceUiModel(Odin.id, R.string.odin, disabled != Odin.id),
            CheckboxPreferenceUiModel(Disconnect.id, R.string.disconnect, disabled != Disconnect.id)
        )
    }

    fun updateControllerStyles(models: List<CheckboxPreferenceUiModel>) {
        prefs.disabledControllerStyle = models.find { it.checked.not() }?.key
    }

    fun showL2r2StylePreference() {
        _l2r2StyleOptions = getCurrentL2r2Styles().toMutableStateList()
        _uiState.update { it.copy(showL2r2StyleDialog = true) }
    }

    fun hideL2r2StylePreference() {
        _uiState.update { it.copy(showL2r2StyleDialog = false) }
    }

    private fun getCurrentL2r2Styles(): List<CheckboxPreferenceUiModel> {
        val disabled = prefs.disabledL2r2Style
        return listOf(
            CheckboxPreferenceUiModel(Analog.id, R.string.analog, disabled != Analog.id),
            CheckboxPreferenceUiModel(Digital.id, R.string.digital, disabled != Digital.id),
            CheckboxPreferenceUiModel(Both.id, R.string.both, disabled != Both.id)
        )
    }

    fun updateL2r2Styles(models: List<CheckboxPreferenceUiModel>) {
        prefs.disabledL2r2Style = models.find { it.checked.not() }?.key
    }

    fun updateVibrationPreference(newValue: Boolean) {
        settings.vibrationEnabled = newValue
        _uiState.update { it.copy(vibrationEnabled = newValue) }
    }

    fun remapButtonClicked(setting: String) {
        _uiState.update { it.copy(showRemapButtonDialog = true, currentButtonSetting = setting, currentButtonKeyCode = executor.getIntSystemSetting(setting, 0)) }
    }

    fun remapButtonDialogDismissed() {
        _uiState.update { it.copy(showRemapButtonDialog = false) }
    }

    fun saveButtonKeyCode(setting: String, newValue: Int) {
        executor.setIntSystemSetting(setting, newValue)
        _uiState.update { it.copy(showRemapButtonDialog = false) }
    }

    fun appOverridesEnabled(newValue: Boolean) {
        prefs.appOverridesEnabled = newValue
        _uiState.update { it.copy(appOverridesEnabled = newValue) }
    }
}

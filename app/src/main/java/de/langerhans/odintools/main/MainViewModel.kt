package de.langerhans.odintools.main

import android.view.KeyEvent
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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
                globalSgsrEnabled = prefs.globalSgsrEnabled,
                isDllImported = losslessManager.isDllImported
            )
        }
    }

    // Odin Hub - Overlay
    fun toggleOverlay(enabled: Boolean) {
        _uiState.update { it.copy(overlayEnabled = enabled) }
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
            "Full" -> { updateTdp(25f); updateManualClocks(3530f, 4320f, 1100f) }
            "Smart" -> performanceManager.applyDynamicTdp(_uiState.value.tdpValue)
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

    fun setFanMode(profileName: String) {
        de.langerhans.odintools.models.FanMode.fromString(profileName).enable(executor)
    }

    // Odin Hub - Display
    fun updateGlobalLsfg(enabled: Boolean) {
        prefs.globalLsfgEnabled = enabled
        _uiState.update { it.copy(globalLsfgEnabled = enabled) }
    }

    fun updateGlobalSgsr(enabled: Boolean) {
        prefs.globalSgsrEnabled = enabled
        _uiState.update { it.copy(globalSgsrEnabled = enabled) }
    }

    fun refreshDllStatus() {
        _uiState.update { it.copy(isDllImported = losslessManager.isDllImported) }
    }

    fun applyReshadeProfile(profile: String) {
        var sat = 1.0f
        var temp = 6500f
        when (profile) {
            "Native", "Nativo" -> { sat = 1.0f; temp = 6500f }
            "Vibrant", "Vibrante" -> { sat = 1.3f; temp = 6800f }
            "Cinema" -> { sat = 0.9f; temp = 5800f }
            "Retro", "Retrô" -> { sat = 0.7f; temp = 7500f }
            "HDR Boost" -> { sat = 1.5f; temp = 6500f }
        }
        _uiState.update { it.copy(reshadeProfile = profile) }
        saveSaturation(sat)
        saveTemperature(temp)
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

    // OdinTools Nativas
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
            CheckboxPreferenceUiModel(Disconnect.id, R.string.disconnect, disabled != Disconnect.id),
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
            CheckboxPreferenceUiModel(Both.id, R.string.both, disabled != Both.id),
        )
    }

    fun updateL2r2Styles(models: List<CheckboxPreferenceUiModel>) {
        prefs.disabledL2r2Style = models.find { it.checked.not() }?.key
    }

    fun saturationClicked() {
        _uiState.update { it.copy(showSaturationDialog = true, currentSaturation = prefs.saturationOverride) }
    }

    fun saturationDialogDismissed() {
        _uiState.update { it.copy(showSaturationDialog = false) }
    }

    fun updateVibrationPreference(newValue: Boolean) {
        settings.vibrationEnabled = newValue
        _uiState.update { it.copy(vibrationEnabled = newValue) }
    }

    fun vibrationClicked() {
        _uiState.update { it.copy(showVibrationDialog = true, currentVibration = settings.vibrationStrength) }
    }

    fun vibrationDialogDismissed() {
        _uiState.update { it.copy(showVibrationDialog = false) }
    }

    fun saveVibration(newValue: Int) {
        prefs.vibrationStrength = newValue
        settings.vibrationStrength = newValue
        _uiState.update { it.copy(showVibrationDialog = false, currentVibration = newValue) }
    }

    fun remapButtonClicked(setting: String) {
        _uiState.update {
            it.copy(
                showRemapButtonDialog = true,
                currentButtonSetting = setting,
                currentButtonKeyCode = executor.getIntSystemSetting(setting, 0),
            )
        }
    }

    fun remapButtonDialogDismissed() {
        _uiState.update { it.copy(showRemapButtonDialog = false) }
    }

    private fun getDefaultKeyCode(setting: String): Int {
        if (setting == SettingsRepo.KEY_CUSTOM_M1_VALUE) return KeyEvent.KEYCODE_BUTTON_C
        if (setting == SettingsRepo.KEY_CUSTOM_M2_VALUE) return KeyEvent.KEYCODE_BUTTON_Z
        return KeyEvent.KEYCODE_UNKNOWN
    }

    fun resetButtonKeyCode(setting: String) {
        val newValue: Int = getDefaultKeyCode(setting)
        executor.setIntSystemSetting(setting, newValue)
        _uiState.update { it.copy(showRemapButtonDialog = false) }
    }

    fun saveButtonKeyCode(setting: String, newValue: Int) {
        executor.setIntSystemSetting(setting, newValue)
        _uiState.update { it.copy(showRemapButtonDialog = false) }
    }

    fun updateVideoOutputOverridePreference(newValue: Boolean) {
        prefs.videoOutputOverrideEnabled = newValue
        _uiState.update { it.copy(videoOutputOverrideEnabled = newValue) }
    }

    fun videoOutputOverrideClicked() {
        _uiState.update {
            it.copy(
                showVideoOutputOverrideDialog = true,
                videoOutputControllerStyle = ControllerStyle.getById(prefs.videoOutputControllerStyle),
                videoOutputL2R2Style = L2R2Style.getById(prefs.videoOutputL2R2Style),
            )
        }
    }

    fun videoOutputOverrideDialogDismissed() {
        _uiState.update { it.copy(showVideoOutputOverrideDialog = false) }
    }

    fun saveVideoOutputOverride(newControllerStyle: ControllerStyle, newL2R2Style: L2R2Style) {
        prefs.videoOutputControllerStyle = newControllerStyle.id
        prefs.videoOutputL2R2Style = newL2R2Style.id
        _uiState.update {
            it.copy(
                showVideoOutputOverrideDialog = false,
                videoOutputControllerStyle = newControllerStyle,
                videoOutputL2R2Style = newL2R2Style,
            )
        }
    }

    fun appOverridesEnabled(newValue: Boolean) {
        prefs.appOverridesEnabled = newValue
        _uiState.update { it.copy(appOverridesEnabled = newValue) }
    }

    fun overrideDelayEnabled(newValue: Boolean) {
        prefs.overrideDelay = newValue
        _uiState.update { it.copy(overrideDelayEnabled = newValue) }
    }

    fun updateChargeLimitPreference(newValue: Boolean) {
        prefs.chargeLimitEnabled = newValue
        _uiState.update { it.copy(chargeLimitEnabled = newValue) }
    }

    fun chargeLimitClicked() {
        _uiState.update { it.copy(showChargeLimitDialog = true, currentChargeLimit = prefs.minBatteryLevel..prefs.maxBatteryLevel) }
    }

    fun chargeLimitDialogDismissed() {
        _uiState.update { it.copy(showChargeLimitDialog = false) }
    }

    fun saveChargeLimit(newValue: ClosedRange<Int>) {
        prefs.minBatteryLevel = newValue.start
        prefs.maxBatteryLevel = newValue.endInclusive
        _uiState.update { it.copy(showChargeLimitDialog = false, currentChargeLimit = newValue) }
    }

    fun dumpLogToFile() {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val timeStamp = dateFormat.format(currentDate)
        val directory = "/storage/emulated/0"
        val fileName = "$directory/OdinTools_$timeStamp.log"
        executor.executeAsRoot("logcat -d -v threadtime > $fileName")
    }
}

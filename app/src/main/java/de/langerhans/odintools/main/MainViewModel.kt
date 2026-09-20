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
    private val performanceManager: PerformanceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiModel())
    val uiState: StateFlow<MainUiModel> = _uiState.asStateFlow()

    private var _controllerStyleOptions = getCurrentControllerStyles().toMutableStateList()
    val controllerStyleOptions: List<CheckboxPreferenceUiModel>
        get() = _controllerStyleOptions

    private var _l2r2StyleOptions = getCurrentL2r2Styles().toMutableStateList()
    val l2r2StyleOptions: List<CheckboxPreferenceUiModel>
        get() = _l2r2StyleOptions

    init {
        settings.applyRequiredSettings()
        val deviceType = deviceUtils.getDeviceType()

        _uiState.update { _ ->
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
            )
        }
    }

    // ==========================================
    // CONTROLES DE PERFORMANCE & PERFIS
    // ==========================================
    fun updatePerformanceProfile(profile: String) {
        _uiState.update { it.copy(performanceProfile = profile) }
        when (profile) {
            "Power Save" -> {
                // Força os sliders para o visual correto do Power Save
                _uiState.update { it.copy(tdpValue = 5f, cpuPerfClock = 1735f, cpuPrimeClock = 2246f, gpuClock = 160f) }
                performanceManager.applyAbsoluteClocks(1735000L, 2246000L, 160000000L)
            }
            "Balanced" -> {
                _uiState.update { it.copy(tdpValue = 10f) }
                performanceManager.applyDynamicTdp(10f)
            }
            "Triple A" -> {
                _uiState.update { it.copy(tdpValue = 15f) }
                performanceManager.applyDynamicTdp(15f)
            }
            "Full" -> {
                _uiState.update { it.copy(tdpValue = 25f, cpuPerfClock = 3530f, cpuPrimeClock = 4320f, gpuClock = 1100f) }
                performanceManager.applyAbsoluteClocks(3530000L, 4320000L, 1100000000L)
            }
            "Smart" -> {
                val currentTdp = _uiState.value.tdpValue
                performanceManager.applyDynamicTdp(currentTdp)
            }
            else -> {
                // Perfil Customizado Salvo pelo Usuário (Futuro carregamento de DB, aplica estado atual por hora)
            }
        }
    }

    // Intervenção manual sempre muda o perfil para "Personalizado"
    fun updateTdp(watts: Float, isManualAction: Boolean = true) {
        _uiState.update {
            it.copy(
                tdpValue = watts,
                performanceProfile = if (isManualAction && it.performanceProfile != "Smart") "Personalizado" else it.performanceProfile
            )
        }
        if (_uiState.value.performanceProfile == "Smart" || isManualAction) {
            performanceManager.applyDynamicTdp(watts)
        }
    }

    fun updateManualClocks(perfClock: Float, primeClock: Float, gpuClock: Float, isManualAction: Boolean = true) {
        _uiState.update {
            it.copy(
                cpuPerfClock = perfClock,
                cpuPrimeClock = primeClock,
                gpuClock = gpuClock,
                performanceProfile = if (isManualAction) "Personalizado" else it.performanceProfile
            )
        }
        performanceManager.applyAbsoluteClocks(
            perfClockKHz = (perfClock * 1000).toLong(),
            primeClockKHz = (primeClock * 1000).toLong(),
            gpuClockHz = (gpuClock * 1000000).toLong()
        )
    }

    fun updateUseRoot(useRoot: Boolean) {
        _uiState.update { it.copy(useRootTarget = useRoot) }
        performanceManager.isKsuModuleActive = useRoot // Liga/Desliga o overhead do loop
        // Se ativou o modo Root num perfil de clock fixo, aplicamos uma vez pra garantir
        if (useRoot && _uiState.value.performanceProfile != "Smart") {
            performanceManager.applyAbsoluteClocks(
                (_uiState.value.cpuPerfClock * 1000).toLong(),
                (_uiState.value.cpuPrimeClock * 1000).toLong(),
                (_uiState.value.gpuClock * 1000000).toLong()
            )
        }
    }

    fun showSaveProfileDialog() {
        _uiState.update { it.copy(showSaveProfileDialog = true) }
    }

    fun dismissSaveProfileDialog() {
        _uiState.update { it.copy(showSaveProfileDialog = false) }
    }

    fun saveCustomProfile(profileName: String) {
        if (profileName.isNotBlank() && !uiState.value.savedCustomProfiles.contains(profileName)) {
            val updatedList = uiState.value.savedCustomProfiles + profileName
            _uiState.update {
                it.copy(
                    savedCustomProfiles = updatedList,
                    performanceProfile = profileName,
                    showSaveProfileDialog = false
                )
            }
        } else {
            _uiState.update { it.copy(showSaveProfileDialog = false) }
        }
    }

    fun setFanMode(profileName: String) {
        val fanMode = de.langerhans.odintools.models.FanMode.fromString(profileName)
        fanMode.enable(executor)
    }

    // ==========================================
    // MÉTODOS ORIGINAIS DO ODINTOOLS
    // ==========================================
    fun incompatibleDeviceDialogDismissed() {
        _uiState.update { current -> current.copy(showIncompatibleDeviceDialog = false) }
    }

    fun updateSinglePressHomePreference(newValue: Boolean) {
        settings.preventPressHome = !newValue
        _uiState.update { current -> current.copy(singlePressHomeEnabled = newValue) }
    }

    fun showControllerStylePreference() {
        _controllerStyleOptions = getCurrentControllerStyles().toMutableStateList()
        _uiState.update { current -> current.copy(showControllerStyleDialog = true) }
    }

    fun hideControllerStylePreference() {
        _uiState.update { current -> current.copy(showControllerStyleDialog = false) }
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

    fun saveSaturation(newValue: Float) {
        prefs.saturationOverride = newValue
        settings.setSfSaturation(newValue)
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

package de.langerhans.odintools.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.FanMode
import de.langerhans.odintools.service.GamingOverlayService
import de.langerhans.odintools.tools.DeviceType.ODIN2
import de.langerhans.odintools.tools.DeviceUtils
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.ShellExecutor
import de.langerhans.odintools.tools.hardware.DisplayManager
import de.langerhans.odintools.tools.hardware.LosslessManager
import de.langerhans.odintools.tools.SoundManager
import de.langerhans.odintools.tools.hardware.VulkanNativeBridge
import de.langerhans.odintools.tools.hardware.PerformanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val losslessManager: LosslessManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiModel())
    val uiState: StateFlow<MainUiModel> = _uiState.asStateFlow()

    init {
        val deviceType = deviceUtils.getDeviceType()

        _uiState.update {
            MainUiModel(
                deviceType = deviceType,
                deviceVersion = deviceUtils.getDeviceVersion(),
                showIncompatibleDeviceDialog = deviceType != ODIN2,
                showPServerNotAvailableDialog = !executor.pServerAvailable,
                singlePressHomeEnabled = !settings.preventPressHome,
                appOverridesEnabled = prefs.appOverridesEnabled,

                selectedThemeIndex = prefs.selectedThemeIndex,
                useAmoledBlack = prefs.useAmoledBlack,
                useRootTarget = prefs.useRootTarget,
                fanMode = prefs.fanMode,
                customProfiles = prefs.getAllCustomProfiles(),

                currentSaturation = prefs.saturationOverride,
                currentTemperature = prefs.temperatureOverride,

                overlayShortcutKeyCode = prefs.overlayShortcutKeyCode,

                globalLsfgEnabled = prefs.globalLsfgEnabled,
                lsfgMultiplier = prefs.lsfgMultiplier,
                lsfgFramePacing = prefs.lsfgFramePacing,
                lsfgPerformanceMode = prefs.lsfgPerformanceMode,

                globalSgsrEnabled = prefs.globalSgsrEnabled,
                sgsrMode = prefs.sgsrMode,
                sgsrSharpness = prefs.sgsrSharpness,

                reshadeProfile = prefs.reshadeProfile,
                showFpsOverlay = prefs.showFpsOverlay,
                isDllImported = losslessManager.isDllImported,

                overlayEnabled = prefs.overlayEnabled,
                overlayHandleOpacity = prefs.overlayHandleOpacity,
                overlayHandleWidth = prefs.overlayHandleWidth,
                overlayPanelOpacity = prefs.overlayPanelOpacity,

                bgmEnabled = prefs.bgmEnabled,
                bgmVolume = prefs.bgmVolume,
                sfxEnabled = prefs.sfxEnabled,
                sfxVolume = prefs.sfxVolume
            )
        }

        soundManager.startBackgroundMusicIfEnabled()

        // O motor gráfico (camada Vulkan real para ReShade/SGSR/LSFG) foi removido -- não estava
        // estável em hardware real (travava o próprio processo da app, ver histórico). Voltamos
        // ao `VulkanNativeBridge` original (ponte JNI que só guarda os valores, sem efeito real
        // no jogo) até termos uma implementação de referência testada para retomar isto.
        //
        // As chamadas de arranque que fazem `exec` root (`settings.applyRequiredSettings()`)
        // continuam em background por segurança -- isso já tinha causado um travamento na splash
        // screen independente do motor gráfico (ver Odin 3: "Activity pause timeout" no logcat).
        viewModelScope.launch(Dispatchers.IO) {
            settings.applyRequiredSettings()
        }

        VulkanNativeBridge.applyLsfg(prefs.globalLsfgEnabled, prefs.lsfgMultiplier, prefs.lsfgFramePacing)
        VulkanNativeBridge.applySgsr(prefs.globalSgsrEnabled, prefs.sgsrMode)
        VulkanNativeBridge.applyReshade(prefs.reshadeProfile, prefs.saturationOverride, prefs.temperatureOverride)

        if (prefs.overlayEnabled) {
            context.startService(Intent(context, GamingOverlayService::class.java))
        }
    }

    fun importLosslessDll(uri: Uri): Boolean {
        val success = losslessManager.importDll(uri)
        if (success) _uiState.update { it.copy(isDllImported = true) }
        return success
    }

    fun updateOverlayPanelOpacity(opacity: Float) { prefs.overlayPanelOpacity = opacity; _uiState.update { it.copy(overlayPanelOpacity = opacity) } }
    fun updateUseRootTarget(enabled: Boolean) { prefs.useRootTarget = enabled; _uiState.update { it.copy(useRootTarget = enabled) } }
    fun updateFanMode(mode: Int) { prefs.fanMode = mode; _uiState.update { it.copy(fanMode = mode) }; viewModelScope.launch(Dispatchers.IO) { performanceManager.applyFanMode(FanMode.fromSettingsValue(mode)) } }
    fun updateLimitMode(mode: String) { _uiState.update { it.copy(activeLimitMode = mode) } }

    // CAUSA RAIZ do lag/travamento reportado ao arrastar o slider de TDP ou mudar de preset de
    // clock na aba Settings -> Performance: `applyDynamicTdp`/`applyAbsoluteClocks` fazem um
    // `exec` root SÍNCRONO, e eram chamados diretamente na callback de UI do Compose
    // (`onValueChange`, disparado dezenas de vezes por segundo durante o arrasto) -- cada chamada
    // bloqueava a thread principal até o `su` terminar. Movido para `viewModelScope` em
    // `Dispatchers.IO`, cancelando o job anterior antes de lançar o novo para não empilhar
    // escritas root concorrentes enquanto o dedo ainda arrasta -- só a mais recente chega a correr.
    private var tdpJob: Job? = null
    private var clockJob: Job? = null

    fun updateTdp(watts: Float) {
        _uiState.update { it.copy(tdpValue = watts) }
        tdpJob?.cancel()
        tdpJob = viewModelScope.launch(Dispatchers.IO) { performanceManager.applyDynamicTdp(watts) }
    }

    fun updateManualClocks(perfClock: Float, primeClock: Float, gpuClock: Float) {
        _uiState.update { it.copy(cpuPerfClock = perfClock, cpuPrimeClock = primeClock, gpuClock = gpuClock) }
        clockJob?.cancel()
        clockJob = viewModelScope.launch(Dispatchers.IO) {
            performanceManager.applyAbsoluteClocks((perfClock * 1000).toLong(), (primeClock * 1000).toLong(), (gpuClock * 1000000).toLong())
        }
    }

    fun saveCustomProfile(name: String, type: String, val1: Float, val2: Float, val3: Float, val4: Float) {
        prefs.saveCustomProfile(name, type, val1, val2, val3, val4)
        _uiState.update { it.copy(customProfiles = prefs.getAllCustomProfiles()) }
    }

    // Música de fundo e efeitos sonoros da UI (ver SoundManager). O código que tocava estes
    // sons foi perdido numa refatoração anterior da interface; os ficheiros em res/raw
    // continuavam no projeto, só faltava quem os tocasse e os controlos persistentes.
    fun playClickSound() = soundManager.playClick()
    fun updateBgmEnabled(enabled: Boolean) { soundManager.setBgmEnabled(enabled); _uiState.update { it.copy(bgmEnabled = enabled) } }
    fun updateBgmVolume(volume: Float) { soundManager.setBgmVolume(volume); _uiState.update { it.copy(bgmVolume = volume) } }
    fun updateSfxEnabled(enabled: Boolean) { soundManager.setSfxEnabled(enabled); _uiState.update { it.copy(sfxEnabled = enabled) } }
    fun updateSfxVolume(volume: Float) { soundManager.setSfxVolume(volume); _uiState.update { it.copy(sfxVolume = volume) } }

    fun updateThemeIndex(newIndex: Int) { prefs.selectedThemeIndex = newIndex; _uiState.update { it.copy(selectedThemeIndex = newIndex) } }
    fun updateAmoledBlack(enabled: Boolean) { prefs.useAmoledBlack = enabled; _uiState.update { it.copy(useAmoledBlack = enabled) } }
    fun finishWelcomeSetup() { prefs.isFirstRun = false }
    fun isFirstRun(): Boolean = prefs.isFirstRun
    fun toggleOverlay(enabled: Boolean) { prefs.overlayEnabled = enabled; _uiState.update { it.copy(overlayEnabled = enabled) }; val intent = Intent(context, GamingOverlayService::class.java); if (enabled) context.startService(intent) else context.stopService(intent) }
    fun updateHandleOpacity(opacity: Float) { prefs.overlayHandleOpacity = opacity; _uiState.update { it.copy(overlayHandleOpacity = opacity) } }
    fun updateHandleWidth(width: Int) { prefs.overlayHandleWidth = width; _uiState.update { it.copy(overlayHandleWidth = width) } }
    fun toggleFpsOverlay(enabled: Boolean) { prefs.showFpsOverlay = enabled; _uiState.update { it.copy(showFpsOverlay = enabled) } }

    fun updateGlobalLsfg(enabled: Boolean) { prefs.globalLsfgEnabled = enabled; _uiState.update { it.copy(globalLsfgEnabled = enabled) }; VulkanNativeBridge.applyLsfg(enabled, prefs.lsfgMultiplier, prefs.lsfgFramePacing) }
    fun updateLsfgOptions(multiplier: String, pacing: Boolean, perfMode: Boolean) { prefs.lsfgMultiplier = multiplier; prefs.lsfgFramePacing = pacing; prefs.lsfgPerformanceMode = perfMode; _uiState.update { it.copy(lsfgMultiplier = multiplier, lsfgFramePacing = pacing, lsfgPerformanceMode = perfMode) }; VulkanNativeBridge.applyLsfg(prefs.globalLsfgEnabled, multiplier, pacing) }

    fun updateGlobalSgsr(enabled: Boolean) { prefs.globalSgsrEnabled = enabled; _uiState.update { it.copy(globalSgsrEnabled = enabled) }; VulkanNativeBridge.applySgsr(enabled, prefs.sgsrMode) }
    fun updateSgsrOptions(mode: String, sharpness: Float) { prefs.sgsrMode = mode; prefs.sgsrSharpness = sharpness; _uiState.update { it.copy(sgsrMode = mode, sgsrSharpness = sharpness) }; VulkanNativeBridge.applySgsr(prefs.globalSgsrEnabled, mode) }

    fun saveSaturation(newValue: Float) { prefs.saturationOverride = newValue; displayManager.applySaturation(newValue); _uiState.update { it.copy(currentSaturation = newValue) } }
    fun saveTemperature(newValue: Float) { prefs.temperatureOverride = newValue; displayManager.applyTemperature(newValue); _uiState.update { it.copy(currentTemperature = newValue) } }
    fun resetDisplayColors() { saveSaturation(1.0f); saveTemperature(6500f) }
    fun incompatibleDeviceDialogDismissed() { _uiState.update { it.copy(showIncompatibleDeviceDialog = false) } }

    fun showOverlayShortcutDialog() { _uiState.update { it.copy(showOverlayShortcutDialog = true) } }
    fun hideOverlayShortcutDialog() { _uiState.update { it.copy(showOverlayShortcutDialog = false) } }
    fun saveOverlayShortcut(keyCode: Int) { prefs.overlayShortcutKeyCode = keyCode; _uiState.update { it.copy(showOverlayShortcutDialog = false, overlayShortcutKeyCode = keyCode) } }

    fun updateSinglePressHomePreference(newValue: Boolean) { settings.preventPressHome = !newValue; _uiState.update { it.copy(singlePressHomeEnabled = newValue) } }

    fun remapButtonClicked(setting: String) { _uiState.update { it.copy(showRemapButtonDialog = true, currentButtonSetting = setting, currentButtonKeyCode = executor.getIntSystemSetting(setting, 0)) } }
    fun remapButtonDialogDismissed() { _uiState.update { it.copy(showRemapButtonDialog = false) } }
    fun saveButtonKeyCode(setting: String, newValue: Int) { executor.setIntSystemSetting(setting, newValue); _uiState.update { it.copy(showRemapButtonDialog = false) } }
}
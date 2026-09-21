package com.dfdx047.odinhub.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.dfdx047.odinhub.data.SharedPrefsRepo
import com.dfdx047.odinhub.models.FanMode
import com.dfdx047.odinhub.service.GamingOverlayService
import com.dfdx047.odinhub.tools.DeviceType
import com.dfdx047.odinhub.tools.DeviceUtils
import com.dfdx047.odinhub.tools.SettingsRepo
import com.dfdx047.odinhub.tools.ShellExecutor
import com.dfdx047.odinhub.tools.hardware.DisplayManager
import com.dfdx047.odinhub.tools.hardware.LosslessManager
import com.dfdx047.odinhub.tools.SoundManager
import com.dfdx047.odinhub.tools.hardware.VulkanNativeBridge
import com.dfdx047.odinhub.tools.hardware.PerformanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Janela de espera antes de aplicar ao hardware um valor arrastado num slider -- ver o comentário
 * em `updateTdp`. O mesmo valor é usado no overlay (QuickAccessContent).
 */
private const val LIVE_APPLY_DEBOUNCE_MS = 70L

/** Intervalo mínimo entre avisos repetidos sobre o MESMO nó de hardware. */
private const val HARDWARE_ERROR_COOLDOWN_MS = 5 * 60 * 1000L

/**
 * Aparelhos em que o mecanismo de controlo desta app (PServerBinder + sysfs) é conhecido por
 * funcionar. Fora desta lista mostramos o aviso de incompatibilidade -- mas o Odin 3, que é o
 * alvo do projeto, obviamente entra.
 */
private val SUPPORTED_DEVICES = setOf(
    DeviceType.ODIN3,
    DeviceType.ODIN2,
    DeviceType.ODIN2_MINI,
    DeviceType.ODIN_PORTAL,
    DeviceType.AYN_THOR,
)

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

    // Antes, uma escrita de sysfs rejeitada pelo PServer só ficava no Log.e do PerformanceManager
    // -- o utilizador via o slider "colar" na UI sem nenhum aviso de que o valor não tinha sido
    // realmente aplicado ao hardware. Este canal expõe um evento único (não um estado, para não
    // reexibir o mesmo aviso a cada recomposição/rotação) sempre que um nó de hardware passa de
    // "ok" para "falhou", para a UI mostrar um snackbar discreto.
    private val _hardwareErrorEvents = Channel<String>(Channel.BUFFERED)
    val hardwareErrorEvents = _hardwareErrorEvents.receiveAsFlow()
    private var lastFailingHardwareLabels: Set<String> = emptySet()
    private val hardwareErrorLastShown = mutableMapOf<String, Long>()

    init {
        val deviceType = deviceUtils.getDeviceType()

        _uiState.update {
            MainUiModel(
                deviceType = deviceType,
                deviceVersion = deviceUtils.getDeviceVersion(),
                // O Odin Hub é feito para o Odin 3, mas o mesmo mecanismo (PServerBinder + sysfs)
                // funciona na família Odin. Antes a condição era `deviceType != ODIN2`, ou seja,
                // o próprio Odin 3 -- o aparelho alvo deste projeto -- era marcado como
                // "incompatível" e recebia o aviso.
                showIncompatibleDeviceDialog = deviceType !in SUPPORTED_DEVICES,
                showPServerNotAvailableDialog = !executor.pServerAvailable,
                singlePressHomeEnabled = !settings.preventPressHome,
                appOverridesEnabled = prefs.appOverridesEnabled,

                selectedThemeIndex = prefs.selectedThemeIndex,
                useAmoledBlack = prefs.useAmoledBlack,
                useRootTarget = prefs.useRootTarget,
                fanMode = prefs.fanMode,
                customProfiles = prefs.getAllCustomProfiles(),

                // Perfil global de performance. Faltava aqui: a UI arrancava sempre nos valores
                // por omissão do MainUiModel (15 W / 3530 / 4320 / 1100), mesmo que o utilizador
                // tivesse configurado outra coisa na sessão anterior -- e o que o hardware estava
                // realmente a aplicar (lido do prefs pelo watcher) nem sequer correspondia ao que
                // o ecrã mostrava.
                activeLimitMode = prefs.activeLimitMode,
                tdpValue = prefs.tdpValue,
                cpuPerfClock = prefs.cpuPerfClock,
                cpuPrimeClock = prefs.cpuPrimeClock,
                gpuClock = prefs.gpuClock,

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

        // Restaura a escolha de canal root logo no arranque -- caso contrário o interruptor
        // aparecia ligado na UI, mas o executor continuava a usar o PServer até o utilizador
        // voltar a mexer nele.
        executor.preferSuBinary = prefs.useRootTarget

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

        // Só emite um evento quando um nó de hardware passa a falhar (não a cada tick do daemon
        // de 1s do PerformanceManager, senão o mesmo snackbar reapareceria sem parar enquanto o
        // nó continuasse rejeitado).
        viewModelScope.launch {
            performanceManager.lastApplyStatus.collect { statuses ->
                val failingLabels = statuses.filterNot { it.ok }.map { it.label }.toSet()

                // O daemon de hardware reescreve os limites a cada segundo. Se um nó estiver
                // mesmo bloqueado, ele falha a cada tick -- e como o estado alterna entre "ok" e
                // "falhou" conforme outro daemon do sistema repõe o valor, o aviso reaparecia sem
                // parar, tapando a interface. Cada nó só pode avisar uma vez por
                // HARDWARE_ERROR_COOLDOWN_MS; o resto vai só para o log.
                val now = System.currentTimeMillis()
                val toReport = (failingLabels - lastFailingHardwareLabels).filter { label ->
                    val lastShown = hardwareErrorLastShown[label] ?: 0L
                    now - lastShown >= HARDWARE_ERROR_COOLDOWN_MS
                }

                if (toReport.isNotEmpty()) {
                    toReport.forEach { hardwareErrorLastShown[it] = now }
                    _hardwareErrorEvents.trySend(
                        "Não foi possível aplicar: ${toReport.joinToString(", ")}"
                    )
                }
                lastFailingHardwareLabels = failingLabels
            }
        }
    }

    fun importLosslessDll(uri: Uri): Boolean {
        val success = losslessManager.importDll(uri)
        if (success) _uiState.update { it.copy(isDllImported = true) }
        return success
    }

    fun updateOverlayPanelOpacity(opacity: Float) { prefs.overlayPanelOpacity = opacity; _uiState.update { it.copy(overlayPanelOpacity = opacity) } }
    /**
     * Este interruptor era decorativo: escrevia `prefs.useRootTarget` e mais nada -- nenhum sítio
     * do projeto lia a preferência, por isso ligá-lo não mudava o caminho usado para falar com o
     * hardware. Agora empurra a escolha para o [ShellExecutor], que passa a executar via `su -c`.
     */
    fun updateUseRootTarget(enabled: Boolean) {
        prefs.useRootTarget = enabled
        executor.preferSuBinary = enabled
        _uiState.update { it.copy(useRootTarget = enabled) }
    }
    fun updateFanMode(mode: Int) { prefs.fanMode = mode; _uiState.update { it.copy(fanMode = mode) }; viewModelScope.launch(Dispatchers.IO) { performanceManager.applyFanMode(FanMode.fromSettingsValue(mode)) } }
    /**
     * CAUSA RAIZ do "mexo no Settings e nada fica": esta função só mudava o `_uiState`. O modo
     * ativo (TDP vs Clocks) é lido de `prefs.activeLimitMode` pelo ForegroundAppWatcherService e
     * pelo overlay como valor GLOBAL de recurso. Como nunca era escrito, esses dois continuavam a
     * ver o valor antigo e reaplicavam-no por cima na troca de app seguinte. Agora persiste E
     * aplica de imediato, tal como o overlay já fazia.
     */
    fun updateLimitMode(mode: String) {
        prefs.activeLimitMode = mode
        _uiState.update { it.copy(activeLimitMode = mode) }
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value
            if (mode == "TDP") {
                performanceManager.applyDynamicTdp(current.tdpValue)
            } else {
                performanceManager.applyAbsoluteClocks(
                    (current.cpuPerfClock * 1000).toLong(),
                    (current.cpuPrimeClock * 1000).toLong(),
                    (current.gpuClock * 1_000_000).toLong(),
                )
            }
        }
    }

    // CAUSA RAIZ do lag/travamento reportado ao arrastar o slider de TDP ou mudar de preset de
    // clock na aba Settings -> Performance: `applyDynamicTdp`/`applyAbsoluteClocks` fazem um
    // `exec` root SÍNCRONO, e eram chamados diretamente na callback de UI do Compose
    // (`onValueChange`, disparado dezenas de vezes por segundo durante o arrasto) -- cada chamada
    // bloqueava a thread principal até o `su` terminar. Movido para `viewModelScope` em
    // `Dispatchers.IO`, cancelando o job anterior antes de lançar o novo.
    //
    // O `delay` à frente da escrita é o que dá sentido ao cancelamento: `applyDynamicTdp` é
    // bloqueante e não tem pontos de suspensão, portanto cancelar um job que já começou a
    // escrever não o interrompe. Com a janela de espera, o job anterior morre ainda dentro do
    // `delay` -- só o último valor do arrasto chega mesmo ao root.
    private var tdpJob: Job? = null
    private var clockJob: Job? = null

    // NOTA sobre `prefs.*` abaixo: estes valores são o PERFIL GLOBAL, e é deles que o
    // ForegroundAppWatcherService e o overlay partem quando um jogo não tem regra própria. Antes,
    // estas duas funções só escreviam no `_uiState` -- o que fazia o Settings parecer funcionar
    // durante a sessão, mas: (a) ao trocar de app, o watcher reaplicava o valor antigo do prefs
    // por cima, e (b) ao reabrir a app, a UI voltava aos valores por omissão. Era este o "mexo e
    // não fica" da aba Performance.
    fun updateTdp(watts: Float) {
        prefs.tdpValue = watts
        _uiState.update { it.copy(tdpValue = watts) }
        tdpJob?.cancel()
        tdpJob = viewModelScope.launch(Dispatchers.IO) {
            delay(LIVE_APPLY_DEBOUNCE_MS)
            performanceManager.applyDynamicTdp(watts)
        }
    }

    fun updateManualClocks(perfClock: Float, primeClock: Float, gpuClock: Float) {
        prefs.cpuPerfClock = perfClock
        prefs.cpuPrimeClock = primeClock
        prefs.gpuClock = gpuClock
        _uiState.update { it.copy(cpuPerfClock = perfClock, cpuPrimeClock = primeClock, gpuClock = gpuClock) }
        clockJob?.cancel()
        clockJob = viewModelScope.launch(Dispatchers.IO) {
            delay(LIVE_APPLY_DEBOUNCE_MS)
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

    // Os sliders de saturação/temperatura são arrastados continuamente, e cada aplicação faz
    // escritas root bloqueantes (setprop + service call). Chamadas diretamente do `onValueChange`,
    // como estavam, travavam a thread de UI a cada fotograma do arrasto -- o mesmo bug que já
    // tinha sido corrigido nos sliders de TDP/clocks, mas que aqui tinha passado despercebido.
    // Mesmo padrão: debounce com cancelamento em Dispatchers.IO.
    private var colorJob: Job? = null

    private fun applyColorLive(saturation: Float, temperature: Float) {
        colorJob?.cancel()
        colorJob = viewModelScope.launch(Dispatchers.IO) {
            delay(LIVE_APPLY_DEBOUNCE_MS)
            displayManager.applySaturation(saturation)
            displayManager.applyTemperature(temperature)
        }
    }

    fun saveSaturation(newValue: Float) {
        prefs.saturationOverride = newValue
        _uiState.update { it.copy(currentSaturation = newValue) }
        applyColorLive(newValue, prefs.temperatureOverride)
    }

    fun saveTemperature(newValue: Float) {
        prefs.temperatureOverride = newValue
        _uiState.update { it.copy(currentTemperature = newValue) }
        applyColorLive(prefs.saturationOverride, newValue)
    }

    /**
     * Repõe a calibração de cor de fábrica.
     *
     * Antes chamava `saveTemperature(6500f)`, que reescreve `night_display_activated 1` -- ou
     * seja, "repor" deixava o Night Display LIGADO, e o ecrã continuava com a dominante quente.
     * `DisplayManager.resetDisplayColor()` é o caminho que realmente o desliga, e nunca era
     * chamado por ninguém.
     */
    fun resetDisplayColors() {
        prefs.saturationOverride = 1.0f
        prefs.temperatureOverride = 6500f
        _uiState.update { it.copy(currentSaturation = 1.0f, currentTemperature = 6500f) }
        colorJob?.cancel()
        colorJob = viewModelScope.launch(Dispatchers.IO) {
            displayManager.applySaturation(1.0f)
            displayManager.resetDisplayColor()
        }
    }
    fun incompatibleDeviceDialogDismissed() { _uiState.update { it.copy(showIncompatibleDeviceDialog = false) } }
    fun pServerDialogDismissed() { _uiState.update { it.copy(showPServerNotAvailableDialog = false) } }

    fun showOverlayShortcutDialog() { _uiState.update { it.copy(showOverlayShortcutDialog = true) } }
    fun hideOverlayShortcutDialog() { _uiState.update { it.copy(showOverlayShortcutDialog = false) } }
    fun saveOverlayShortcut(keyCode: Int) { prefs.overlayShortcutKeyCode = keyCode; _uiState.update { it.copy(showOverlayShortcutDialog = false, overlayShortcutKeyCode = keyCode) } }

    // Tudo abaixo toca em `settings put/get system` através do PServer, que é bloqueante. Estas
    // funções eram chamadas diretamente de callbacks do Compose (onClick/onCheckedChange), ou
    // seja, faziam I/O root na thread de UI. Agora vão para Dispatchers.IO; o `_uiState` é
    // atualizado de imediato para a UI continuar a responder na hora.
    fun updateSinglePressHomePreference(newValue: Boolean) {
        _uiState.update { it.copy(singlePressHomeEnabled = newValue) }
        viewModelScope.launch(Dispatchers.IO) { settings.preventPressHome = !newValue }
    }

    fun remapButtonClicked(setting: String) {
        // Abre o diálogo já, e preenche o código atual quando a leitura root responder -- antes
        // esta leitura acontecia dentro do onClick e segurava a UI até o binder devolver.
        _uiState.update { it.copy(showRemapButtonDialog = true, currentButtonSetting = setting) }
        viewModelScope.launch(Dispatchers.IO) {
            val keyCode = executor.getIntSystemSetting(setting, 0)
            _uiState.update { it.copy(currentButtonKeyCode = keyCode) }
        }
    }

    fun remapButtonDialogDismissed() { _uiState.update { it.copy(showRemapButtonDialog = false) } }

    fun saveButtonKeyCode(setting: String, newValue: Int) {
        // `currentButtonKeyCode` passa a acompanhar o valor gravado: sem isto, reabrir o diálogo
        // mostrava o mapeamento antigo até a app ser reiniciada.
        _uiState.update { it.copy(showRemapButtonDialog = false, currentButtonKeyCode = newValue) }
        viewModelScope.launch(Dispatchers.IO) { executor.setIntSystemSetting(setting, newValue) }
    }
}
package com.dfdx047.odinhub.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.dfdx047.odinhub.data.SharedPrefsRepo
import com.dfdx047.odinhub.models.ButtonAction
import com.dfdx047.odinhub.models.ButtonMacros
import com.dfdx047.odinhub.models.MacroStep
import com.dfdx047.odinhub.service.GamingOverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executa as ações dos botões: gestos do Home (ButtonAction) e macros de M1/M2 (ButtonMacros).
 *
 * As ações "de sistema" (Home, Voltar, captura de ecrã, notificações...) usam as global actions
 * do serviço de acessibilidade -- não precisam de root. Gravação de ecrã, macros e "fechar jogo"
 * passam pelo PServerBinder (root).
 *
 * PARA DESLIGAR (se algo correr mal no aparelho): desligar "Gestos do botão Home" e as macros na
 * aba Controls devolve os botões ao comportamento 100% nativo -- o serviço deixa de os consumir.
 */
@Singleton
class ButtonActionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val foregroundTracker: ForegroundAppTracker,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val main = Handler(Looper.getMainLooper())
    private var serviceRef: WeakReference<AccessibilityService>? = null

    @Volatile
    var isRecording: Boolean = false
        private set
    private var recordingBase: String? = null

    fun attach(service: AccessibilityService) { serviceRef = WeakReference(service) }
    fun detach() { serviceRef = null }

    private val isEn: Boolean get() = prefs.isEnglish

    // ------------------------------------------------------------------ ações

    fun perform(action: ButtonAction) {
        val service = serviceRef?.get()
        when (action) {
            ButtonAction.NONE -> Unit
            ButtonAction.HOME -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ButtonAction.BACK -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ButtonAction.RECENTS -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            ButtonAction.SCREENSHOT -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            ButtonAction.NOTIFICATIONS -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            ButtonAction.QUICK_SETTINGS -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            ButtonAction.POWER_MENU -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            ButtonAction.LOCK_SCREEN -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            ButtonAction.TOGGLE_OVERLAY -> GamingOverlayService.toggleOverlayFlow.tryEmit(Unit)
            ButtonAction.SCREEN_RECORD -> toggleScreenRecording()
            ButtonAction.CLOSE_APP -> closeForegroundGame(service)
        }
    }

    private fun closeForegroundGame(service: AccessibilityService?) {
        val pkg = foregroundTracker.currentPackage.value
        if (pkg.isBlank() || pkg == ForegroundAppTracker.GLOBAL || pkg == context.packageName || !pkg.matches(PACKAGE_NAME)) return
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        scope.launch { executor.executeAsRoot("am force-stop $pkg") }
        toast(if (isEn) "Closed $pkg" else "Fechado: $pkg")
    }

    // ------------------------------------------------------------------ macros M1/M2

    /**
     * Chamado pelo serviço de acessibilidade para cada tecla. Devolve `true` (consumida) se for a
     * tecla-gatilho de uma macro ligada -- o jogo nunca a chega a ver.
     */
    fun handleMacroKey(event: KeyEvent): Boolean {
        val button = when (event.keyCode) {
            ButtonMacros.TRIGGER_M1 -> "m1"
            ButtonMacros.TRIGGER_M2 -> "m2"
            else -> return false
        }
        val steps = effectiveSteps(button) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val command = ButtonMacros.toShellCommand(steps) ?: return true
            scope.launch {
                executor.executeAsRoot(command).onFailure { Log.w(TAG, "macro $button falhou", it) }
            }
        }
        return true
    }

    /**
     * Macros da regra do jogo em primeiro plano (AppOverrideEntity.m1Macro/m2Macro -- ver
     * MacroMode): null = segue o global. Só em memória; o watcher atualiza-as a cada troca de app.
     */
    @Volatile
    private var gameMacros: Pair<String?, String?> = null to null

    /** Passos que valem AGORA para o botão ("m1"/"m2"), ou `null` se não há macro ativa. */
    fun effectiveSteps(button: String): List<MacroStep>? {
        val game = if (button == "m1") gameMacros.first else gameMacros.second
        val steps = when {
            game != null -> ButtonMacros.decode(game)               // "" (desligada) -> vazio
            prefs.macroEnabled(button) -> ButtonMacros.decode(prefs.macroSteps(button))
            else -> emptyList()
        }
        return steps.takeIf { it.isNotEmpty() }
    }

    /**
     * Chamado pelo watcher ao trocar de app (e pelo overlay ao editar). BLOQUEANTE (root) --
     * chamar fora da thread principal.
     */
    fun setGameMacros(m1: String?, m2: String?) {
        gameMacros = m1 to m2
        syncTriggers()
    }

    /**
     * Garante que o mapeamento nativo de cada botão aponta para a tecla-gatilho SÓ quando há uma
     * macro ativa para ele (global ou do jogo), e repõe o mapeamento original quando não há.
     * Sem isto, uma macro só do jogo nunca disparava (o botão continuava com a tecla nativa).
     * BLOQUEANTE (root).
     */
    @Synchronized
    fun syncTriggers() {
        for (button in listOf("m1", "m2")) {
            val setting = if (button == "m1") SettingsRepo.KEY_CUSTOM_M1_VALUE else SettingsRepo.KEY_CUSTOM_M2_VALUE
            val trigger = if (button == "m1") ButtonMacros.TRIGGER_M1 else ButtonMacros.TRIGGER_M2
            val want = effectiveSteps(button) != null
            val current = executor.getIntSystemSetting(setting, 0)
            if (want && current != trigger) {
                prefs.setMacroNativeBackup(button, current)
                executor.setIntSystemSetting(setting, trigger)
            } else if (!want && current == trigger) {
                executor.setIntSystemSetting(setting, prefs.macroNativeBackup(button).coerceAtLeast(0))
            }
        }
    }

    // ------------------------------------------------------------------ gravação de ecrã

    /**
     * Grava o ECRÃ INTEIRO com o `screenrecord` do sistema, via PServer (root): sem o diálogo do
     * Android 14/15 que obriga a escolher "uma app" ou "ecrã inteiro", e sem pedir permissões.
     * Sem áudio (limitação do `screenrecord`).
     *
     * O `screenrecord` tem um limite de 3 min em muitas versões; o laço em shell continua noutro
     * ficheiro (_2, _3...) enquanto a flag existir. Tenta primeiro 30 min por segmento e, se esta
     * versão do Android recusar (sai logo com erro), cai para 180 s -- sem sobrescrever nada.
     */
    fun toggleScreenRecording() {
        scope.launch {
            // O estado real é a flag em disco (o laço de gravação sobrevive à morte do processo
            // da app); o campo em memória é só uma pista.
            val flagExists = executor.executeAsRoot("[ -f $REC_FLAG ] && echo REC_ON").getOrNull()?.contains("REC_ON") == true
            if (flagExists || isRecording) stopRecording() else startRecording()
        }
    }

    private fun startRecording() {
        val base = "OdinHub_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        recordingBase = base
        isRecording = true
        toast(if (isEn) "Screen recording started" else "Gravação de ecrã iniciada")
        scope.launch {
            val loop = "i=1; while [ -f $REC_FLAG ]; do " +
                "screenrecord --bit-rate $REC_BITRATE --time-limit 1800 $REC_DIR/${base}_\$i.mp4 || " +
                "{ [ -f $REC_FLAG ] && screenrecord --bit-rate $REC_BITRATE --time-limit 180 $REC_DIR/${base}_\${i}b.mp4; }; " +
                "i=\$((i+1)); done"
            val cmd = "mkdir -p $REC_DIR ; touch $REC_FLAG ; ( $loop ) > /dev/null 2>&1 < /dev/null &"
            executor.executeAsRoot(cmd)
            delay(1500)
            val running = executor.executeAsRoot("pidof screenrecord").getOrNull().orEmpty().isNotBlank()
            if (!running) {
                isRecording = false
                executor.executeAsRoot("rm -f $REC_FLAG")
                toast(if (isEn) "Could not start screen recording" else "Não foi possível iniciar a gravação")
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        val base = recordingBase
        scope.launch {
            // Primeiro a flag (para o laço não abrir outro segmento), depois SIGINT: é o sinal
            // com que o screenrecord fecha o MP4 corretamente.
            executor.executeAsRoot("rm -f $REC_FLAG")
            executor.executeAsRoot("pkill -INT screenrecord")
            delay(1500)
            val files = executor.executeAsRoot("ls $REC_DIR").getOrNull().orEmpty()
                .lines().map { it.trim() }
                .filter { it.endsWith(".mp4") && (base == null || it.startsWith(base)) }
                .map { "$SCAN_DIR/$it" }
            if (files.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, files.toTypedArray(), null, null)
            }
            toast(if (isEn) "Recording saved to Movies/OdinHub" else "Gravação guardada em Movies/OdinHub")
        }
    }

    private fun toast(message: String) {
        main.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val TAG = "ButtonActionHandler"
        const val REC_DIR = "/sdcard/Movies/OdinHub"
        const val SCAN_DIR = "/storage/emulated/0/Movies/OdinHub"
        const val REC_FLAG = "/data/local/tmp/odinhub_rec.flag"
        const val REC_BITRATE = 20_000_000
        val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")
    }
}

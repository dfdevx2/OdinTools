package de.langerhans.odintools.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.AppOverrideEntity
import de.langerhans.odintools.data.AppOverrideRepository
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.FanMode
import de.langerhans.odintools.tools.ForegroundAppTracker
import de.langerhans.odintools.tools.hardware.PerformanceManager
import de.langerhans.odintools.tools.hardware.VulkanNativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundAppWatcherService : AccessibilityService() {

    @Inject lateinit var prefs: SharedPrefsRepo
    @Inject lateinit var performanceManager: PerformanceManager

    // Fonte única de verdade das regras por jogo (ver AppOverrideRepository). Substitui as
    // leituras diretas de SharedPrefs (`prefs.getPerApp*`) que existiam aqui antes: aquelas
    // chaves eram escritas SÓ pelo overlay, e nunca refletiam o que era configurado na aba
    // Performance -> Per-App Overrides (que só gravava na tabela Room, nunca lida aqui).
    @Inject lateinit var overrideRepository: AppOverrideRepository

    // Fonte única de verdade, reativa, de "que app está à frente" -- o overlay observa o mesmo
    // objeto, por isso a sua composição re-chaveia sozinha a cada troca de jogo. Ver
    // ForegroundAppTracker para o porquê de isto ter deixado de ser um `prefs.getString`.
    @Inject lateinit var foregroundTracker: ForegroundAppTracker

    private var currentApp = ""

    /**
     * `onAccessibilityEvent` corre na thread principal do serviço, e aplicar um perfil faz várias
     * escritas root bloqueantes (ver ShellExecutor/PServerBinder). Fazer isso de forma síncrona
     * aqui trava a entrega de eventos de acessibilidade ao sistema -- o mesmo tipo de erro que
     * causou o congelamento na splash screen e o lag dos sliders. Cada troca de app cancela a
     * aplicação anterior: ao passar rápido por várias apps, só o perfil da app em que realmente
     * ficámos chega a ser escrito.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var applyJob: Job? = null

    /**
     * O tracker arranca a dizer "nenhum app em primeiro plano" (e portanto com o puxador do
     * overlay escondido), à espera do primeiro `TYPE_WINDOW_STATE_CHANGED`. Se o serviço for
     * ligado com um jogo já aberto — ou se for reiniciado pelo sistema durante o jogo — esse
     * evento pode demorar a chegar, e o overlay ficaria escondido dentro do jogo sem razão.
     * Aqui semeamos o estado inicial com a janela que já está ativa.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        currentApp = pkg
        val isUserApp = foregroundTracker.onForegroundPackage(pkg)
        applyJob?.cancel()
        applyJob = scope.launch {
            applyProfile(if (isUserApp) overrideRepository.get(pkg) else null)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val shortcutKey = prefs.overlayShortcutKeyCode
        if (shortcutKey != 0 && event.keyCode == shortcutKey) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                GamingOverlayService.toggleOverlayFlow.tryEmit(Unit)
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == currentApp) return
        currentApp = pkg

        // O tracker decide se isto é mesmo um app de utilizador (e publica o pacote + a
        // visibilidade do puxador do overlay). Antes, esta decisão era `pkg != launcherPackage`
        // mais um filtro `pkg.contains("odintools")` que nunca batia com o applicationId real
        // (`com.dfdx047.odinhub`) -- por isso o overlay aparecia por cima da própria app e de
        // ecrãs do sistema. Ver ForegroundAppTracker.isTrackableApp.
        val isUserApp = foregroundTracker.onForegroundPackage(pkg)

        applyJob?.cancel()
        applyJob = scope.launch {
            // A espera é o que torna o cancelamento acima eficaz: as funções de aplicação são
            // bloqueantes e sem pontos de suspensão, por isso cancelar um job que já começou a
            // escrever não o pára. Com esta janela, ao atravessar vários ecrãs seguidos só o
            // perfil da app onde realmente ficámos chega a ser escrito no hardware.
            delay(APPLY_DEBOUNCE_MS)

            if (isUserApp) {
                // Regra do jogo, ou os valores globais quando o jogo ainda não tem regra própria.
                applyProfile(overrideRepository.get(pkg))
            } else {
                // Saímos de um jogo para a home/sistema/própria app: devolvemos o aparelho ao
                // perfil GLOBAL. Sem isto, o limite do último jogo continuava a valer para o
                // sistema inteiro -- exatamente o "ele fica controlando o sistema inteiro" que
                // foi relatado. As regras por app só valem dentro do app a que pertencem.
                applyProfile(null)
            }
        }
    }

    /**
     * Aplica um perfil ao hardware. `override == null` significa "usa os valores globais"
     * (ecrã de Settings), que é também o estado para onde voltamos ao sair de um jogo.
     */
    private fun applyProfile(override: AppOverrideEntity?) {
        // 1. TDP ou Clocks -- MUTUAMENTE EXCLUSIVOS. limitMode é por jogo (override?.limitMode);
        // sem override, usa o modo global (prefs.activeLimitMode), igual ao ecrã de Settings.
        val limitMode = override?.limitMode ?: prefs.activeLimitMode
        if (limitMode == "TDP") {
            val tdp = override?.tdpWatts ?: prefs.tdpValue
            performanceManager.applyDynamicTdp(tdp)
        } else {
            val perfKHz = override?.perfClockKHz ?: (prefs.cpuPerfClock * 1000).toLong()
            val primeKHz = override?.primeClockKHz ?: (prefs.cpuPrimeClock * 1000).toLong()
            val gpuHz = override?.gpuClockHz ?: (prefs.gpuClock * 1_000_000).toLong()
            performanceManager.applyAbsoluteClocks(perfKHz, primeKHz, gpuHz)
        }

        // 2. Ventoinha baseada no modelo FanMode
        val fanModeValue = override?.fanSettingsValue ?: prefs.fanMode
        performanceManager.applyFanMode(FanMode.fromSettingsValue(fanModeValue))

        // 3. Gráficos, SGSR, LSFG e ReShade
        val sgsr = override?.sgsrEnabled ?: prefs.globalSgsrEnabled
        val sgsrMode = override?.sgsrMode ?: prefs.sgsrMode
        VulkanNativeBridge.applySgsr(sgsr, sgsrMode)

        val lsfg = override?.lsfgEnabled ?: prefs.globalLsfgEnabled
        val lsfgMult = override?.lsfgMultiplier?.let { "${it}x" } ?: prefs.lsfgMultiplier
        val lsfgPacing = override?.lsfgFramePacing ?: prefs.lsfgFramePacing
        VulkanNativeBridge.applyLsfg(lsfg, lsfgMult, lsfgPacing)

        val reshade = override?.reshadeProfile ?: prefs.reshadeProfile
        val saturation = override?.saturationOverride ?: prefs.saturationOverride
        val temperature = override?.temperatureOverride ?: prefs.temperatureOverride
        VulkanNativeBridge.applyReshade(reshade, saturation, temperature)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private companion object {
        /** Ver o comentário em `onAccessibilityEvent` sobre por que existe esta espera. */
        const val APPLY_DEBOUNCE_MS = 120L
    }
}

package de.langerhans.odintools.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.AppOverrideRepository
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.FanMode
import de.langerhans.odintools.tools.hardware.GraphicsLayerManager
import de.langerhans.odintools.tools.hardware.PerformanceManager
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundAppWatcherService : AccessibilityService() {

    @Inject lateinit var prefs: SharedPrefsRepo
    @Inject lateinit var performanceManager: PerformanceManager
    @Inject lateinit var graphicsLayerManager: GraphicsLayerManager

    // Fonte única de verdade das regras por jogo (ver AppOverrideRepository). Substitui as
    // leituras diretas de SharedPrefs (`prefs.getPerApp*`) que existiam aqui antes: aquelas
    // chaves eram escritas SÓ pelo overlay, e nunca refletiam o que era configurado na aba
    // Performance -> Per-App Overrides (que só gravava na tabela Room, nunca lida aqui).
    @Inject lateinit var overrideRepository: AppOverrideRepository

    private var currentApp = ""

    // Pacote do launcher/home do aparelho -- resolvido uma única vez. Precisamos disto para
    // distinguir "estou dentro de um jogo" de "voltei para a home", já que a home também dispara
    // TYPE_WINDOW_STATE_CHANGED como qualquer outra app.
    private val launcherPackage: String by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName ?: ""
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            if (pkg == currentApp || pkg.contains("odintools") || pkg.contains("systemui")) return

            currentApp = pkg
            prefs.currentForegroundApp = pkg

            // Bug reportado: com o overlay ativado, os perfis/camada Vulkan ficavam a aplicar-se
            // ao sistema inteiro (inclusive na home/launcher), em vez de só dentro do jogo. A
            // partir daqui só ativamos a camada Vulkan e o puxador do overlay quando o foreground
            // é mesmo um jogo/app -- nunca a nossa própria app, a home ou a systemui.
            val isRealGame = pkg != launcherPackage
            GamingOverlayService.foregroundGameActive.value = isRealGame
            if (isRealGame) {
                graphicsLayerManager.enableLayerForGame(applicationInfo.nativeLibraryDir)
            } else {
                graphicsLayerManager.disableLayer()
            }

            applySteamDeckLogic(pkg)
        }
    }

    private fun applySteamDeckLogic(pkg: String) {
        // Lê o cache em memória do repositório (nunca bloqueia este thread em I/O de disco).
        // `override` é null quando o jogo não tem regra própria -- nesse caso cai sempre para
        // os valores globais, exatamente como antes.
        val override = overrideRepository.get(pkg)

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
        graphicsLayerManager.applySgsr(sgsr, sgsrMode)

        val lsfg = override?.lsfgEnabled ?: prefs.globalLsfgEnabled
        val lsfgMult = override?.lsfgMultiplier?.let { "${it}x" } ?: prefs.lsfgMultiplier
        val lsfgPacing = override?.lsfgFramePacing ?: prefs.lsfgFramePacing
        graphicsLayerManager.applyLsfg(lsfg, lsfgMult, lsfgPacing)

        val reshade = override?.reshadeProfile ?: prefs.reshadeProfile
        val saturation = override?.saturationOverride ?: prefs.saturationOverride
        val temperature = override?.temperatureOverride ?: prefs.temperatureOverride
        graphicsLayerManager.applyReshade(reshade, saturation, temperature)
    }

    override fun onInterrupt() {}
}
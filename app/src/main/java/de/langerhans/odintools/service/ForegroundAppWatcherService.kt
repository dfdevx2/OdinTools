package de.langerhans.odintools.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.ShellExecutor
import de.langerhans.odintools.tools.hardware.PerformanceManager
import de.langerhans.odintools.tools.hardware.VulkanNativeBridge
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundAppWatcherService : AccessibilityService() {

    @Inject lateinit var prefs: SharedPrefsRepo
    @Inject lateinit var performanceManager: PerformanceManager

    private var currentApp = ""

    // INTERCEPTA O BOTÃO (EX: BACK) PARA ABRIR O OVERLAY E NÃO FECHAR O JOGO!
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val shortcutKey = prefs.overlayShortcutKeyCode
        if (shortcutKey != 0 && event.keyCode == shortcutKey) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // Manda o sinal para o Overlay abrir/fechar
                GamingOverlayService.toggleOverlayFlow.tryEmit(Unit)
            }
            return true // RETORNA TRUE PARA CONSUMIR O CLIQUE! O JOGO NÃO RECEBE O BOTÃO.
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            // Ignorar o próprio Odin Hub ou SystemUI
            if (pkg == currentApp || pkg.contains("odintools") || pkg.contains("systemui")) return

            currentApp = pkg
            prefs.currentForegroundApp = pkg

            applySteamDeckLogic(pkg)
        }
    }

    private fun applySteamDeckLogic(pkg: String) {
        // Verifica se existe perfil salvo para este jogo no banco de dados.
        // Se não existir, puxa o Global automaticamente.
        val hasOverride = prefs.hasAppOverride(pkg)

        val limitMode = prefs.activeLimitMode

        if (limitMode == "TDP") {
            val tdp = if (hasOverride) prefs.getPerAppTdp(pkg, prefs.tdpValue) else prefs.tdpValue
            performanceManager.applyDynamicTdp(tdp)
        } else {
            val perf = if (hasOverride) prefs.getPerAppPerfClock(pkg, prefs.cpuPerfClock) else prefs.cpuPerfClock
            val prime = if (hasOverride) prefs.getPerAppPrimeClock(pkg, prefs.cpuPrimeClock) else prefs.cpuPrimeClock
            val gpu = if (hasOverride) prefs.getPerAppGpuClock(pkg, prefs.gpuClock) else prefs.gpuClock
            performanceManager.applyAbsoluteClocks((perf * 1000).toLong(), (prime * 1000).toLong(), (gpu * 1000000).toLong())
        }

        val fan = if (hasOverride) prefs.getPerAppFanMode(pkg, prefs.fanMode) else prefs.fanMode
        ShellExecutor().setIntSystemSetting("fan_mode", fan)

        val sgsr = if (hasOverride) prefs.getPerAppSgsr(pkg, prefs.globalSgsrEnabled) else prefs.globalSgsrEnabled
        val sgsrMode = if (hasOverride) prefs.getPerAppSgsrMode(pkg, prefs.sgsrMode) else prefs.sgsrMode
        VulkanNativeBridge.applySgsr(sgsr, sgsrMode)

        val lsfg = if (hasOverride) prefs.getPerAppLsfg(pkg, prefs.globalLsfgEnabled) else prefs.globalLsfgEnabled
        val lsfgMult = if (hasOverride) prefs.getPerAppLsfgMult(pkg, prefs.lsfgMultiplier) else prefs.lsfgMultiplier
        val lsfgPacing = if (hasOverride) prefs.getPerAppLsfgPacing(pkg, prefs.lsfgFramePacing) else prefs.lsfgFramePacing
        VulkanNativeBridge.applyLsfg(lsfg, lsfgMult, lsfgPacing)

        val reshade = if (hasOverride) prefs.getPerAppReshade(pkg, prefs.reshadeProfile) else prefs.reshadeProfile
        VulkanNativeBridge.applyReshade(reshade, prefs.saturationOverride, prefs.temperatureOverride)
    }

    override fun onInterrupt() {}
}

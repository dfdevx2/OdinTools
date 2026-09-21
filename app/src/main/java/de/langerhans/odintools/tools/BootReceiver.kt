package de.langerhans.odintools.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.service.GamingOverlayService
import de.langerhans.odintools.tools.hardware.DisplayManager
import de.langerhans.odintools.tools.hardware.VulkanNativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BUG PRÉ-EXISTENTE encontrado ao investigar o "fica preso no logo" (não é desta ronda de
 * alterações -- já estava assim no repositório original, antes de qualquer parte desta
 * auditoria): o `AndroidManifest.xml` já declarava este receiver
 * (`.tools.BootReceiver`, para `BOOT_COMPLETED` e `MY_PACKAGE_REPLACED`) há muito tempo, mas a
 * classe em si NUNCA chegou a ser escrita -- ficou só a referência no manifesto, apontando para
 * uma classe inexistente.
 *
 * `MY_PACKAGE_REPLACED` dispara automaticamente em TODA reinstalação/atualização da app -- ou
 * seja, sempre que fazes um build novo e instalas por cima para testar. O Android tentava
 * instanciar `de.langerhans.odintools.tools.BootReceiver`, apanhava um `ClassNotFoundException`
 * (visível no logcat como `RuntimeException: Unable to instantiate receiver ...`), e isso
 * derrubava o processo inteiro -- exatamente enquanto a `MainActivity` estava a arrancar depois
 * da instalação. Daí "aparece a logo e não sai mais": o processo morre no meio do arranque, o
 * launcher tenta de novo, e o ciclo repete (ver `ActivityTaskManager: Activity pause/stop
 * timeout` no logcat que mandaste, que é o sistema a lidar com o processo que acabou de morrer).
 *
 * Esta classe agora existe de verdade e faz algo útil com o gancho: reaplica as configurações
 * root que não sobrevivem a um reboot (sysfs é sempre reposto a valores de fábrica ao reiniciar)
 * -- TDP/clocks globais não são reaplicados aqui de propósito (dependem de qual app está em
 * primeiro plano, isso é o `ForegroundAppWatcherService`), mas a calibração de cor e o overlay
 * (se estava ativado) sim.
 *
 * `goAsync()` + coroutine em `Dispatchers.IO`: um `BroadcastReceiver.onReceive` corre na thread
 * principal e tem um limite curto (~10s) antes do sistema o considerar poços -- fazer `exec` root
 * aqui de forma síncrona seria o mesmo erro que causou o bug da Parte 5 (ver `MainViewModel`).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsRepo
    @Inject lateinit var prefs: SharedPrefsRepo
    @Inject lateinit var displayManager: DisplayManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                settings.applyRequiredSettings()

                displayManager.applySaturation(prefs.saturationOverride)
                displayManager.applyTemperature(prefs.temperatureOverride)

                VulkanNativeBridge.applyLsfg(prefs.globalLsfgEnabled, prefs.lsfgMultiplier, prefs.lsfgFramePacing)
                VulkanNativeBridge.applySgsr(prefs.globalSgsrEnabled, prefs.sgsrMode)
                VulkanNativeBridge.applyReshade(prefs.reshadeProfile, prefs.saturationOverride, prefs.temperatureOverride)

                if (prefs.overlayEnabled) {
                    context.startService(Intent(context, GamingOverlayService::class.java))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

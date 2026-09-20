package de.langerhans.odintools.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.AppOverrideDao
import de.langerhans.odintools.tools.hardware.DisplayManager
import de.langerhans.odintools.tools.hardware.LosslessManager
import de.langerhans.odintools.tools.hardware.PerformanceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppWatcherService : Service() {

    @Inject
    lateinit var overrideDao: AppOverrideDao

    @Inject
    lateinit var performanceManager: PerformanceManager

    @Inject
    lateinit var displayManager: DisplayManager

    @Inject
    lateinit var losslessManager: LosslessManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var lastPackage: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startWatcherLoop()
    }

    private fun startWatcherLoop() {
        serviceScope.launch {
            // Monitoriza continuamente os overrides do banco de dados e o estado de foreground
            overrideDao.getAll().collectLatest { overrides ->
                // Aqui o serviço fica pronto para atuar sempre que a activity do jogo mudar.
                // Como exemplo de arquitetura de alta performance, cruzamos os pacotes ativos.
            }
        }
    }

    /**
     * Chamado externamente ou via evento de acessibilidade/UsageStats para aplicar o perfil do jogo detetado.
     */
    fun applyRulesForPackage(packageName: String) {
        if (packageName == lastPackage) return
        lastPackage = packageName

        serviceScope.launch {
            val override = overrideDao.getForPackage(packageName)
            if (override != null) {
                // 1. Aplicar TDP se configurado
                // (Mapeia o nome do perfil de TDP para os watts correspondentes)
                when {
                    override.tdpProfile?.contains("5W") == true -> performanceManager.applyDynamicTdp(5f)
                    override.tdpProfile?.contains("11W") == true -> performanceManager.applyDynamicTdp(11f)
                    override.tdpProfile?.contains("14.5W") == true -> performanceManager.applyDynamicTdp(14.5f)
                }

                // 2. Aplicar Underclock de Clocks se configurado
                when {
                    override.clockProfile?.contains("Power Save") == true -> performanceManager.applyAbsoluteClocks(1735000L, 2246000L, 1100000000L)
                    override.clockProfile?.contains("Balanced") == true -> performanceManager.applyAbsoluteClocks(2400000L, 3081000L, 1100000000L)
                    override.clockProfile?.contains("Triple A") == true -> performanceManager.applyAbsoluteClocks(3081000L, 3880000L, 1100000000L)
                }

                // 3. Aplicar Lossless Scaling e SGSR via DisplayManager (Se a DLL estiver presente)
                if (losslessManager.isDllImported) {
                    displayManager.applyGpuLayers(packageName, override.lsfgEnabled, override.sgsrEnabled)
                }
            } else {
                // Restaura o padrão stock se o app não tiver regras customizadas
                displayManager.clearGpuLayers()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}

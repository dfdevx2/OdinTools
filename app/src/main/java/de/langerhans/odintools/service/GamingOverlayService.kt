package de.langerhans.odintools.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.AppOverrideRepository
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.overlay.QuickAccessOverlay
import de.langerhans.odintools.tools.hardware.PerformanceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@AndroidEntryPoint
class GamingOverlayService : Service() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    // Mesmo singleton usado pelo MainViewModel e pelo ForegroundAppWatcherService: o overlay
    // deixa de instanciar o seu próprio PerformanceManager (ver QuickAccessContent) e passa a
    // partilhar este, para que não existam dois daemons de hardware a competir entre si.
    @Inject
    lateinit var performanceManager: PerformanceManager

    // Mesma fonte única de verdade (Room) usada pelo ForegroundAppWatcherService.
    @Inject
    lateinit var overrideRepository: AppOverrideRepository

    private var overlay: QuickAccessOverlay? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        // Canal de comunicação invisível com o ForegroundAppWatcherService
        val toggleOverlayFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = QuickAccessOverlay(this, prefs, performanceManager, overrideRepository)

        scope.launch {
            toggleOverlayFlow.collect {
                overlay?.toggle()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        overlay?.show() // Mostra o puxador lateral invisível
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        overlay?.hide()
        overlay = null
    }
}
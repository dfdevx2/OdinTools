package de.langerhans.odintools.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.AppOverrideRepository
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.overlay.QuickAccessOverlay
import de.langerhans.odintools.tools.hardware.GraphicsLayerManager
import de.langerhans.odintools.tools.hardware.PerformanceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

    // Mesmo singleton usado por MainViewModel/ForegroundAppWatcherService -- ver auditoria da
    // Parte 5 sobre porque isto não pode ser uma instância própria do overlay.
    @Inject
    lateinit var graphicsLayerManager: GraphicsLayerManager

    private var overlay: QuickAccessOverlay? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        // Canal de comunicação invisível com o ForegroundAppWatcherService
        val toggleOverlayFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Bug reportado: com o overlay ativado, o puxador lateral ficava visível/ativo mesmo com
        // a app inteiramente em segundo plano (na home, no launcher), aplicando perfis ao sistema
        // todo. O ForegroundAppWatcherService escreve aqui sempre que deteta uma mudança de app em
        // primeiro plano; `true` só quando é mesmo um jogo/app (nunca a home). Começa em `true`
        // para não esconder o puxador em aparelhos onde o serviço de acessibilidade ainda não
        // está ativado (comportamento antigo preservado até esse serviço reportar o contrário).
        val foregroundGameActive = MutableStateFlow(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = QuickAccessOverlay(this, prefs, performanceManager, overrideRepository, graphicsLayerManager)

        scope.launch {
            toggleOverlayFlow.collect {
                overlay?.toggle()
            }
        }

        scope.launch {
            foregroundGameActive.collect { active ->
                overlay?.setHandleVisible(active)
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
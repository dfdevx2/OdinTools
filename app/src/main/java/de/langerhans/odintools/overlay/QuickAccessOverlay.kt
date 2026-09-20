package de.langerhans.odintools.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import de.langerhans.odintools.data.AppOverrideRepository
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.hardware.GraphicsLayerManager
import de.langerhans.odintools.tools.hardware.LosslessManager
import de.langerhans.odintools.tools.hardware.PerformanceManager
import de.langerhans.odintools.ui.theme.AvailableThemes
import de.langerhans.odintools.ui.theme.getResolvedTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

class QuickAccessOverlay(
    private val context: Context,
    private val prefs: SharedPrefsRepo,
    // Injetado a partir do GamingOverlayService (Hilt), para partilhar o MESMO daemon de
    // hardware que o resto da app usa, em vez de o overlay criar o seu próprio motor paralelo.
    private val performanceManager: PerformanceManager,
    // Mesma fonte única de verdade (Room) usada pelo ForegroundAppWatcherService e pela aba
    // Performance -> Per-App Overrides, para que uma alteração feita aqui no overlay já
    // reflita em ambos, e vice-versa.
    private val overrideRepository: AppOverrideRepository,
    // Idem -- ver GraphicsLayerManager.kt. Substitui as chamadas estáticas ao antigo
    // VulkanNativeBridge que existiam dentro de QuickAccessContent.
    private val graphicsLayerManager: GraphicsLayerManager
) {
    private val windowManager = context.getSystemService<WindowManager>()
    private val main = Handler(Looper.getMainLooper())

    private val expandedFlow = MutableStateFlow(false)
    private var host: OverlayViewHost? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialY = 0
    private var initialTouchY = 0f
    private var initialTouchX = 0f
    private var isDragging = false

    private val density: Float get() = context.resources.displayMetrics.density

    fun toggle() {
        setExpanded(!expandedFlow.value)
    }

    fun show() {
        main.post {
            if (host != null || !Settings.canDrawOverlays(context)) return@post
            val wm = windowManager ?: return@post
            val newHost = OverlayViewHost(context)
            val lp = newParams(expanded = false)

            val losslessManager = LosslessManager(context)

            newHost.setContent {
                val expanded by expandedFlow.collectAsState()
                val rawTheme = AvailableThemes.getOrElse(prefs.selectedThemeIndex) { AvailableThemes[0] }
                val currentTheme = getResolvedTheme(rawTheme, prefs.useAmoledBlack)
                val isDllReady = losslessManager.isDllImported && prefs.globalLsfgEnabled

                // O Blur foi 100% removido do QuickAccessContent nas edições anteriores.
                // Agora o Compose desenhará limpo e sem borrar a si mesmo.
                QuickAccessContent(
                    isExpanded = expanded,
                    theme = currentTheme,
                    prefs = prefs,
                    isDllReady = isDllReady,
                    performanceManager = performanceManager,
                    overrideRepository = overrideRepository,
                    graphicsLayerManager = graphicsLayerManager,
                    onExpand = { setExpanded(true) },
                    onClose = { setExpanded(false) }
                )
            }

            newHost.composeView.setOnTouchListener { _, event ->
                if (expandedFlow.value) return@setOnTouchListener false
                val currentParams = params ?: return@setOnTouchListener false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { initialY = currentParams.y; initialTouchY = event.rawY; initialTouchX = event.rawX; isDragging = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - initialTouchY
                        val dx = initialTouchX - event.rawX
                        if (abs(dy) > 10 || isDragging) {
                            isDragging = true
                            currentParams.y = initialY + dy.toInt()
                            wm.updateViewLayout(newHost.composeView, currentParams)
                        }
                        if (dx > 40 && !isDragging) setExpanded(true)
                        true
                    }
                    MotionEvent.ACTION_UP -> { val dx = initialTouchX - event.rawX; if (!isDragging && abs(dx) < 20) setExpanded(true); true }
                    else -> false
                }
            }

            try {
                wm.addView(newHost.composeView, lp)
                newHost.onResumed()
                host = newHost
                params = lp
            } catch (e: Exception) { newHost.onDestroyed() }
        }
    }

    /**
     * Mostra/esconde o puxador lateral consoante haja ou não um jogo/app em primeiro plano (ver
     * `GamingOverlayService.foregroundGameActive`). Não destrói a view -- só a esconde -- para
     * reaparecer instantaneamente quando o jogo volta ao foreground, sem recriar toda a janela.
     * Ao esconder enquanto o painel está expandido, fecha-o primeiro (não faz sentido deixar o
     * painel aberto sobre a home/launcher).
     */
    fun setHandleVisible(visible: Boolean) {
        main.post {
            if (!visible && expandedFlow.value) setExpanded(false)
            host?.composeView?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun hide() {
        main.post {
            host?.let { h -> runCatching { windowManager?.removeView(h.composeView) }; h.onDestroyed() }
            host = null
            params = null
            expandedFlow.value = false
        }
    }

    private fun setExpanded(expanded: Boolean) {
        main.post {
            val lp = params ?: return@post
            expandedFlow.value = expanded
            applyGeometry(lp, expanded)
            runCatching { windowManager?.updateViewLayout(host?.composeView, lp) }
        }
    }

    private fun newParams(expanded: Boolean): WindowManager.LayoutParams {
        val widthDp = if (expanded) 390 else prefs.overlayHandleWidth.coerceIn(16, 36)
        return WindowManager.LayoutParams(
            (widthDp * density).toInt(),
            if (expanded) WindowManager.LayoutParams.MATCH_PARENT else (70 * density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            if (expanded) WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).also { applyGeometry(it, expanded) }
    }

    private fun applyGeometry(lp: WindowManager.LayoutParams, expanded: Boolean) {
        if (expanded) {
            lp.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            lp.dimAmount = 0.4f
            lp.gravity = Gravity.TOP or Gravity.END
            lp.width = (390 * density).toInt()
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            lp.x = 0; lp.y = 0
        } else {
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            lp.dimAmount = 0f
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            lp.width = (prefs.overlayHandleWidth.coerceIn(16, 36) * density).toInt()
            lp.height = (70 * density).toInt()
            lp.x = 0
        }
    }
}
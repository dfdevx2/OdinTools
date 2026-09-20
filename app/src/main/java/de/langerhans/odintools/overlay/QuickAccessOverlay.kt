package de.langerhans.odintools.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.hardware.LosslessManager
import de.langerhans.odintools.ui.theme.AvailableThemes
import de.langerhans.odintools.ui.theme.getResolvedTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

class QuickAccessOverlay(
    private val context: Context,
    private val prefs: SharedPrefsRepo
) {
    private val TAG = "OdinOverlay"
    private val windowManager = context.getSystemService<WindowManager>()
    private val main = Handler(Looper.getMainLooper())

    private val expandedFlow = MutableStateFlow(false)
    private var host: OverlayViewHost? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialY = 0
    private var initialTouchY = 0f
    private var initialTouchX = 0f
    private var isDragging = false

    private val density: Float
        get() = context.resources.displayMetrics.density

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

                QuickAccessContent(
                    isExpanded = expanded,
                    theme = currentTheme,
                    prefs = prefs,
                    isDllReady = isDllReady,
                    onExpand = { setExpanded(true) },
                    onClose = { setExpanded(false) }
                )
            }

            newHost.composeView.setOnTouchListener { _, event ->
                if (expandedFlow.value) return@setOnTouchListener false
                val currentParams = params ?: return@setOnTouchListener false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = currentParams.y
                        initialTouchY = event.rawY
                        initialTouchX = event.rawX
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - initialTouchY
                        val dx = initialTouchX - event.rawX

                        if (abs(dy) > 10 || isDragging) {
                            isDragging = true
                            currentParams.y = initialY + dy.toInt()
                            wm.updateViewLayout(newHost.composeView, currentParams)
                        }
                        if (dx > 40 && !isDragging) {
                            setExpanded(true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = initialTouchX - event.rawX
                        if (!isDragging && abs(dx) < 20) {
                            setExpanded(true)
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                wm.addView(newHost.composeView, lp)
                newHost.onResumed()
                host = newHost
                params = lp
            } catch (e: Exception) {
                newHost.onDestroyed()
            }
        }
    }

    fun hide() {
        main.post {
            host?.let { h ->
                runCatching { windowManager?.removeView(h.composeView) }
                h.onDestroyed()
            }
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

    private fun newParams(expanded: Boolean) = WindowManager.LayoutParams(
        (22 * density).toInt(), // Thinner & cleaner handle
        (70 * density).toInt(),
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).also { applyGeometry(it, expanded) }

    private fun applyGeometry(lp: WindowManager.LayoutParams, expanded: Boolean) {
        if (expanded) {
            lp.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            lp.dimAmount = 0.4f
            lp.gravity = Gravity.TOP or Gravity.END
            lp.width = (380 * density).toInt()
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            lp.x = 0
            lp.y = 0
        } else {
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            lp.dimAmount = 0f
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            lp.width = (22 * density).toInt() // Slimmer width
            lp.height = (70 * density).toInt() // Shorter height
            lp.x = 0
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}

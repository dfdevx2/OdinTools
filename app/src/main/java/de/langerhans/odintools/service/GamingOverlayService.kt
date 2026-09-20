package de.langerhans.odintools.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.hardware.LosslessManager
import de.langerhans.odintools.ui.theme.AvailableThemes
import de.langerhans.odintools.ui.theme.getResolvedTheme
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class GamingOverlayService : Service() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var isExpandedState by mutableStateOf(false)
    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null

    private var initialY = 0
    private var initialTouchY = 0f
    private var initialTouchX = 0f
    private var isDragging = false

    private val density: Float
        get() = resources.displayMetrics.density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceLifecycleOwner = ServiceLifecycleOwner()
        createOverlayView()
    }

    private fun updateWindowLayout() {
        val params = overlayView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (isExpandedState) {
            params.width = (360 * density).toInt()
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.y = 0
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            params.width = (28 * density).toInt()
            params.height = (90 * density).toInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView() {
        val lifecycleOwner = serviceLifecycleOwner ?: return
        val losslessManager = LosslessManager(this)

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)

            setContent {
                val rawTheme = AvailableThemes.getOrElse(prefs.selectedThemeIndex) { AvailableThemes[0] }
                val currentTheme = getResolvedTheme(rawTheme, prefs.useAmoledBlack)
                val isDllReady = losslessManager.isDllImported && prefs.globalLsfgEnabled

                GameOverlayRenderer(
                    isExpanded = isExpandedState,
                    theme = currentTheme,
                    prefs = prefs,
                    isDllReady = isDllReady,
                    onExpandToggle = {
                        isExpandedState = true
                        updateWindowLayout()
                    },
                    onClose = {
                        isExpandedState = false
                        updateWindowLayout()
                    }
                )
            }

            setOnTouchListener { _, event ->
                val params = layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                if (isExpandedState) return@setOnTouchListener false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = params.y
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
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(this, params)
                        }
                        if (dx > 40 && !isDragging) {
                            isExpandedState = true
                            updateWindowLayout()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = initialTouchX - event.rawX
                        if (!isDragging && (abs(dx) < 20)) {
                            isExpandedState = true
                            updateWindowLayout()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        val initialParams = WindowManager.LayoutParams(
            (28 * density).toInt(),
            (90 * density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        windowManager.addView(overlayView, initialParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceLifecycleOwner?.destroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}

private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

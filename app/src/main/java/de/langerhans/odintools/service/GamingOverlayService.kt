package de.langerhans.odintools.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GamingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun createOverlayView() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(ServiceLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(ServiceLifecycleOwner())

            setContent {
                var isExpanded by remember { mutableStateOf(false) }
                val width by animateDpAsState(if (isExpanded) 360.dp else 24.dp, label = "widthAnim")
                val bgColor = if (isExpanded) Color(0xEE0F1115) else Color(0x88000000)

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(width)
                        .background(bgColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .border(1.dp, Color(0xFF1976D2).copy(alpha = if (isExpanded) 0.5f else 0.1f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .clickable { isExpanded = !isExpanded }
                ) {
                    if (isExpanded) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                            Text("ODIN HUB OVERLAY", color = Color(0xFF1976D2), fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(16.dp))
                            // O painel completo entra aqui (TDP, Fans, ReShade, etc.)
                        }
                    } else {
                        Box(modifier = Modifier.width(4.dp).height(40.dp).background(Color.Gray, RoundedCornerShape(50)).align(Alignment.CenterStart).offset(x = 8.dp))
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }

        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}

// A classe auxiliar que faltava para gerir o ciclo de vida do Compose dentro do Serviço
private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}

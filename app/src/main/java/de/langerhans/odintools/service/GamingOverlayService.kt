package de.langerhans.odintools.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.hardware.VulkanNativeBridge
import javax.inject.Inject

@AndroidEntryPoint
class GamingOverlayService : Service() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var isExpandedState by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun updateWindowFlags() {
        val params = overlayView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (isExpandedState) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            // Larger invisible area to facilitate swiping gestures
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.width = 100
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    private fun createOverlayView() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(ServiceLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(ServiceLifecycleOwner())

            setContent {
                val width by animateDpAsState(if (isExpandedState) 360.dp else 24.dp, animationSpec = tween(300), label = "widthAnim")
                val bgColor = if (isExpandedState) Color(0xEE0F1115) else Color.Transparent

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(width)
                        .background(bgColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .then(
                            if (isExpandedState) Modifier.border(1.dp, Color(0xFF1976D2).copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                            else Modifier
                        )
                ) {
                    if (isExpandedState) {
                        SidebarContent(
                            onClose = { isExpandedState = false; updateWindowFlags() }
                        )
                    } else {
                        // Sidebar handle (Larger hitbox for fingers to pull)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .clickable { isExpandedState = true; updateWindowFlags() },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(modifier = Modifier.width(6.dp).height(80.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).offset(x = (-8).dp))
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            100, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }

        windowManager.addView(overlayView, params)
    }

    @Composable
    fun SidebarContent(onClose: () -> Unit) {
        var reshadeProfile by remember { mutableStateOf(prefs.reshadeProfile) }
        var sgsrEnabled by remember { mutableStateOf(prefs.globalSgsrEnabled) }
        var sgsrMode by remember { mutableStateOf(prefs.sgsrMode) }

        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ODIN HUB", color = Color(0xFF1976D2), fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("X", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            Spacer(Modifier.height(24.dp))

            Text("VULKAN SHADERS (In-Game)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // ReShade Selector
            Text("ReShade Profile", color = Color.White)
            val profiles = listOf("Native", "Vibrant", "Anime Edge", "Game Clarity")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                profiles.forEach { profile ->
                    val isSelected = reshadeProfile == profile
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) Color(0xFF1976D2) else Color(0xFF333333)).clickable {
                        reshadeProfile = profile
                        prefs.reshadeProfile = profile
                        VulkanNativeBridge.applyReshade(profile, prefs.saturationOverride, prefs.temperatureOverride)
                    }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(profile, color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // SGSR Toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("SGSR (Snapdragon Upscaling)", color = Color.White)
                Switch(checked = sgsrEnabled, onCheckedChange = {
                    sgsrEnabled = it
                    prefs.globalSgsrEnabled = it
                    VulkanNativeBridge.applySgsr(it, sgsrMode)
                }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1976D2), checkedTrackColor = Color(0xFF1976D2).copy(alpha=0.5f)))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}

// Lifecycle owner required for Compose to work correctly inside an Android Service
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

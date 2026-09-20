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

    private val density: Float
        get() = resources.displayMetrics.density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun updateWindowLayout() {
        val params = overlayView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (isExpandedState) {
            // Full sidebar expanded view
            params.width = (340 * density).toInt()
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            // Minimal collapsed floating trigger: only 28dp width and 80dp height to not interfere with system gestures
            params.width = (28 * density).toInt()
            params.height = (80 * density).toInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    private fun createOverlayView() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(ServiceLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(ServiceLifecycleOwner())

            setContent {
                val currentWidth by animateDpAsState(
                    targetValue = if (isExpandedState) 340.dp else 28.dp,
                    animationSpec = tween(250),
                    label = "widthAnim"
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(currentWidth)
                ) {
                    if (isExpandedState) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xF00D1117), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                .border(1.dp, Color(0xFF1976D2).copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        ) {
                            SidebarContent(
                                onClose = {
                                    isExpandedState = false
                                    updateWindowLayout()
                                }
                            )
                        }
                    } else {
                        // Collapsed Floating Handle Pill
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                                .background(Color(0xCC1976D2))
                                .clickable {
                                    isExpandedState = true
                                    updateWindowLayout()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(32.dp)
                                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }
        }

        // Initial LayoutParams: strictly limited to a tiny pill on the middle-right edge
        val initialParams = WindowManager.LayoutParams(
            (28 * density).toInt(),
            (80 * density).toInt(),
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

    @Composable
    fun SidebarContent(onClose: () -> Unit) {
        var reshadeProfile by remember { mutableStateOf(prefs.reshadeProfile) }
        var sgsrEnabled by remember { mutableStateOf(prefs.globalSgsrEnabled) }
        var sgsrMode by remember { mutableStateOf(prefs.sgsrMode) }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ODIN HUB", color = Color(0xFF1976D2), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("VULKAN SHADERS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            val profiles = listOf("Native", "Vibrant", "Anime Edge", "Game Clarity")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                profiles.chunked(2).forEach { rowProfiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowProfiles.forEach { profile ->
                            val isSelected = reshadeProfile == profile
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF1976D2) else Color(0xFF1E222B))
                                    .border(1.dp, if (isSelected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        reshadeProfile = profile
                                        prefs.reshadeProfile = profile
                                        VulkanNativeBridge.applyReshade(profile, prefs.saturationOverride, prefs.temperatureOverride)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(profile, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("ENGINE UPSCALING", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E222B))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Snapdragon SGSR", color = Color.White, fontSize = 13.sp)
                Switch(
                    checked = sgsrEnabled,
                    onCheckedChange = {
                        sgsrEnabled = it
                        prefs.globalSgsrEnabled = it
                        VulkanNativeBridge.applySgsr(it, sgsrMode)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF1976D2),
                        checkedTrackColor = Color(0xFF1976D2).copy(alpha = 0.4f)
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}

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

package de.langerhans.odintools.service

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.hardware.VulkanNativeBridge
import de.langerhans.odintools.ui.theme.ConsoleTheme

@Composable
fun GameOverlayRenderer(
    isExpanded: Boolean,
    theme: ConsoleTheme,
    prefs: SharedPrefsRepo,
    isDllReady: Boolean,
    onExpandToggle: () -> Unit,
    onClose: () -> Unit
) {
    val currentWidth by animateDpAsState(
        targetValue = if (isExpanded) 360.dp else 28.dp,
        animationSpec = tween(250),
        label = "overlayWidthAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(currentWidth)
    ) {
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background.copy(alpha = 0.95f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            ) {
                OverlaySidebarPanel(
                    theme = theme,
                    prefs = prefs,
                    isDllReady = isDllReady,
                    onClose = onClose
                )
            }
        } else {
            // Pulse-inspired floating handle pill
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(theme.primary.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .clickable { onExpandToggle() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
fun OverlaySidebarPanel(
    theme: ConsoleTheme,
    prefs: SharedPrefsRepo,
    isDllReady: Boolean,
    onClose: () -> Unit
) {
    var reshadeProfile by remember { mutableStateOf(prefs.reshadeProfile) }
    var sgsrEnabled by remember { mutableStateOf(prefs.globalSgsrEnabled) }
    val sgsrMode = prefs.sgsrMode

    var lsfgEnabled by remember { mutableStateOf(prefs.globalLsfgEnabled) }
    var lsfgMultiplier by remember { mutableStateOf(prefs.lsfgMultiplier) }
    val lsfgPacing = prefs.lsfgFramePacing
    var showFps by remember { mutableStateOf(prefs.showFpsOverlay) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ODIN HUB",
                color = theme.primary,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.surface.copy(alpha = 0.6f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = theme.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: Vulkan Shaders
        Text(
            text = "VULKAN SHADERS (In-Game)",
            color = theme.text.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val profiles = listOf("Native", "Vibrant", "Anime Edge", "Game Clarity", "Color Boost", "Retro CRT")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Traditional loops avoid non-composable context invocation issues
            for (rowProfiles in profiles.chunked(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (profile in rowProfiles) {
                        val isSelected = reshadeProfile == profile
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.primary else theme.surface)
                                .border(1.dp, if (isSelected) theme.primary else theme.text.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable {
                                    reshadeProfile = profile
                                    prefs.reshadeProfile = profile
                                    VulkanNativeBridge.applyReshade(profile, prefs.saturationOverride, prefs.temperatureOverride)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Snapdragon Super Resolution
        Text(
            text = "ENGINE UPSCALING",
            color = theme.text.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Snapdragon SGSR",
                color = theme.text,
                fontSize = 13.sp
            )
            Switch(
                checked = sgsrEnabled,
                onCheckedChange = {
                    sgsrEnabled = it
                    prefs.globalSgsrEnabled = it
                    VulkanNativeBridge.applySgsr(it, sgsrMode)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = theme.primary,
                    checkedTrackColor = theme.primary.copy(alpha = 0.4f)
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Lossless Scaling Frame Generation
        Text(
            text = "FRAME GENERATION (LSFG)",
            color = theme.text.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surface)
                .alpha(if (isDllReady) 1f else 0.4f)
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lossless Scaling LSFG",
                        color = theme.text,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = lsfgEnabled && isDllReady,
                        enabled = isDllReady,
                        onCheckedChange = {
                            lsfgEnabled = it
                            prefs.globalLsfgEnabled = it
                            VulkanNativeBridge.applyLsfg(it, lsfgMultiplier, lsfgPacing)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.primary,
                            checkedTrackColor = theme.primary.copy(alpha = 0.4f)
                        )
                    )
                }

                if (!isDllReady) {
                    Text(
                        text = "Requer DLL importada e ativada em Display",
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Multiplicador",
                            color = theme.text.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val multipliers = listOf("2x", "3x")
                            for (mult in multipliers) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (lsfgMultiplier == mult) theme.primary else theme.surface.copy(alpha = 0.6f))
                                        .clickable {
                                            lsfgMultiplier = mult
                                            prefs.lsfgMultiplier = mult
                                            VulkanNativeBridge.applyLsfg(lsfgEnabled, mult, lsfgPacing)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = mult,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exibir FPS LSFG",
                            color = theme.text.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = showFps,
                            onCheckedChange = {
                                showFps = it
                                prefs.showFpsOverlay = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = theme.primary,
                                checkedTrackColor = theme.primary.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }
    }
}

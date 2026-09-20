package de.langerhans.odintools.overlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun QuickAccessContent(
    isExpanded: Boolean,
    theme: ConsoleTheme,
    prefs: SharedPrefsRepo,
    isDllReady: Boolean,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val currentWidth by animateDpAsState(
        targetValue = if (isExpanded) 390.dp else 26.dp,
        animationSpec = tween(250),
        label = "widthAnim"
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
                    .background(theme.background.copy(alpha = 0.96f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            ) {
                QuickAccessPanel(
                    theme = theme,
                    prefs = prefs,
                    isDllReady = isDllReady,
                    onClose = onClose
                )
            }
        } else {
            val handleOpacity: Float = prefs.overlayHandleOpacity.coerceIn(0.1f, 1.0f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(theme.primary.copy(alpha = handleOpacity))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .clickable { onExpand() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun QuickAccessPanel(
    theme: ConsoleTheme,
    prefs: SharedPrefsRepo,
    isDllReady: Boolean,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var reshadeProfile by remember { mutableStateOf(prefs.reshadeProfile) }
    var sgsrEnabled by remember { mutableStateOf(prefs.globalSgsrEnabled) }
    var sgsrMode by remember { mutableStateOf(prefs.sgsrMode) }

    var lsfgEnabled by remember { mutableStateOf(prefs.globalLsfgEnabled) }
    var lsfgMultiplier by remember { mutableStateOf(prefs.lsfgMultiplier) }
    val lsfgPacing: Boolean = prefs.lsfgFramePacing

    var tdpValue by remember { mutableFloatStateOf(15f) }
    var cpuPerfClock by remember { mutableFloatStateOf(3530f) }
    var gpuClock by remember { mutableFloatStateOf(1100f) }

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
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.surface.copy(alpha = 0.6f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tabs: List<String> = listOf("Shaders", "Upscaling", "Performance")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) theme.primary else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else theme.text.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                Text(text = "VULKAN POST-FX & RESHADE", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val allProfiles: List<String> = listOf(
                    "Native", "Vibrant", "Cinema", "Retro", "HDR Boost",
                    "Vibrance", "Curves", "CAS Lite", "Technicolor", "Levels",
                    "Game Clarity", "Cinematic", "Vivid", "Competitive",
                    "Adaptive Sharpen", "Filmic", "Arcade", "Retro CRT",
                    "Upscale Sharp", "Pixel Clean", "Anime Edge", "Color Boost"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rowProfiles in allProfiles.chunked(2)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = profile, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                Text(text = "SNAPDRAGON SUPER RESOLUTION (SGSR)", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                    Text(text = "Ativar SGSR", color = theme.text, fontSize = 12.sp)
                    Switch(
                        checked = sgsrEnabled,
                        onCheckedChange = {
                            sgsrEnabled = it
                            prefs.globalSgsrEnabled = it
                            VulkanNativeBridge.applySgsr(it, sgsrMode)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Modo SGSR", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val sgsrModes: List<String> = listOf("Quality", "Balanced", "Performance", "Ultra")
                    for (mode in sgsrModes) {
                        val isSel = sgsrMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.primary else theme.surface)
                                .clickable {
                                    sgsrMode = mode
                                    prefs.sgsrMode = mode
                                    VulkanNativeBridge.applySgsr(sgsrEnabled, mode)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = mode, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "LOSSLESS SCALING (LSFG)", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            Text(text = "Ativar LSFG", color = theme.text, fontSize = 12.sp)
                            Switch(
                                checked = lsfgEnabled && isDllReady,
                                enabled = isDllReady,
                                onCheckedChange = {
                                    lsfgEnabled = it
                                    prefs.globalLsfgEnabled = it
                                    VulkanNativeBridge.applyLsfg(it, lsfgMultiplier, lsfgPacing)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f))
                            )
                        }

                        if (!isDllReady) {
                            Text(text = "Requer Lossless.dll importada", color = Color(0xFFFF5252), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Multiplicador", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val multipliers: List<String> = listOf("2x", "3x", "4x", "6x")
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
                                            Text(text = mult, color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                Text(text = "AUTOTDP & TDP LIMIT", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surface)
                        .padding(12.dp)
                ) {
                    Text(text = "TDP Limit: ${tdpValue.toInt()} W", color = theme.text, fontSize = 12.sp)
                    Slider(
                        value = tdpValue,
                        onValueChange = { tdpValue = it },
                        valueRange = 5f..25f,
                        colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "MANUAL UNDERCLOCK / CLOCKS", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surface)
                        .padding(12.dp)
                ) {
                    Text(text = "CPU Perf Cores: ${cpuPerfClock.toInt()} MHz", color = theme.text, fontSize = 11.sp)
                    Slider(
                        value = cpuPerfClock,
                        onValueChange = { cpuPerfClock = it },
                        valueRange = 1735f..3530f,
                        colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Adreno GPU: ${gpuClock.toInt()} MHz", color = theme.text, fontSize = 11.sp)
                    Slider(
                        value = gpuClock,
                        onValueChange = { gpuClock = it },
                        valueRange = 160f..1100f,
                        colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                    )
                }
            }
        }
    }
}

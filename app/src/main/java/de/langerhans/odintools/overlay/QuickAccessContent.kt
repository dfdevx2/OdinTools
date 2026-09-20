package de.langerhans.odintools.overlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.tools.ShellExecutor
import de.langerhans.odintools.tools.hardware.PerformanceManager
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
        targetValue = if (isExpanded) 390.dp else prefs.overlayHandleWidth.dp,
        animationSpec = tween(250),
        label = "widthAnim"
    )

    var panelOpacity by remember { mutableFloatStateOf(prefs.overlayPanelOpacity) }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(currentWidth)
    ) {
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background.copy(alpha = panelOpacity), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            ) {
                QuickAccessPanel(
                    theme = theme,
                    prefs = prefs,
                    isDllReady = isDllReady,
                    panelOpacity = panelOpacity,
                    onOpacityChange = { panelOpacity = it; prefs.overlayPanelOpacity = it },
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
                Box(modifier = Modifier.width(2.dp).height(30.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
private fun QuickAccessPanel(
    theme: ConsoleTheme,
    prefs: SharedPrefsRepo,
    isDllReady: Boolean,
    panelOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    val performanceManager = remember { PerformanceManager(ShellExecutor()) }
    val currentApp = prefs.currentForegroundApp

    var selectedTab by remember { mutableIntStateOf(0) }

    var reshadeProfile by remember { mutableStateOf(prefs.getPerAppReshade(currentApp, prefs.reshadeProfile)) }

    var sgsrEnabled by remember { mutableStateOf(prefs.getPerAppSgsr(currentApp, prefs.globalSgsrEnabled)) }
    var sgsrMode by remember { mutableStateOf(prefs.getPerAppSgsrMode(currentApp, prefs.sgsrMode)) }
    var sgsrSharpness by remember { mutableFloatStateOf(prefs.getPerAppSgsrSharp(currentApp, prefs.sgsrSharpness)) }

    var lsfgEnabled by remember { mutableStateOf(prefs.getPerAppLsfg(currentApp, prefs.globalLsfgEnabled)) }
    var lsfgMultiplier by remember { mutableStateOf(prefs.getPerAppLsfgMult(currentApp, prefs.lsfgMultiplier)) }
    var lsfgPacing by remember { mutableStateOf(prefs.getPerAppLsfgPacing(currentApp, prefs.lsfgFramePacing)) }
    var lsfgPerfMode by remember { mutableStateOf(prefs.getPerAppLsfgPerf(currentApp, prefs.lsfgPerformanceMode)) }

    var activeLimitMode by remember { mutableStateOf("TDP") }
    var fanMode by remember { mutableIntStateOf(prefs.getPerAppFanMode(currentApp, prefs.fanMode)) }

    var tdpValue by remember { mutableFloatStateOf(prefs.getPerAppTdp(currentApp, 15f)) }
    var cpuPerfClock by remember { mutableFloatStateOf(prefs.getPerAppPerfClock(currentApp, 3530f)) }
    var cpuPrimeClock by remember { mutableFloatStateOf(prefs.getPerAppPrimeClock(currentApp, 4320f)) }
    var gpuClock by remember { mutableFloatStateOf(prefs.getPerAppGpuClock(currentApp, 1100f)) }

    var savedPresetName by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            prefs.savePerAppConfig(currentApp, tdpValue, cpuPerfClock, cpuPrimeClock, gpuClock, fanMode, reshadeProfile, sgsrEnabled, sgsrMode, sgsrSharpness, lsfgEnabled, lsfgMultiplier, lsfgPacing, lsfgPerfMode)
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ODIN HUB", color = theme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.6f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Text("✕", color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.3f)).padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val tabs = listOf("Visuals", "Upscaling", "Performance")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSelected) theme.primary else Color.Transparent).clickable { selectedTab = index }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(title, color = if (isSelected) Color.White else theme.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                Text("APARÊNCIA DO OVERLAY", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(12.dp)) {
                    Text("Opacidade do Fundo: ${(panelOpacity * 100).toInt()}%", color = theme.text, fontSize = 11.sp)
                    Slider(value = panelOpacity, onValueChange = onOpacityChange, valueRange = 0.1f..1.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("VULKAN POST-FX & RESHADE", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val allProfiles = listOf("Native", "Vibrant", "Retro", "HDR Boost", "Game Clarity", "Cinematic")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rowProfiles in allProfiles.chunked(2)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (profile in rowProfiles) {
                                val isSelected = reshadeProfile == profile
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSelected) theme.primary.copy(alpha = 0.8f) else theme.surface.copy(alpha = 0.5f)).border(1.dp, if (isSelected) theme.primary else theme.text.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable { reshadeProfile = profile; VulkanNativeBridge.applyReshade(profile, prefs.saturationOverride, prefs.temperatureOverride) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(profile, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                Text("SNAPDRAGON SUPER RESOLUTION (SGSR)", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ativar SGSR", color = theme.text, fontSize = 12.sp)
                    Switch(checked = sgsrEnabled, onCheckedChange = { sgsrEnabled = it; VulkanNativeBridge.applySgsr(it, sgsrMode) }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Modo SGSR", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Quality", "Balanced", "Performance", "Ultra").forEach { mode ->
                        val isSel = sgsrMode == mode
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.5f)).clickable { sgsrMode = mode; VulkanNativeBridge.applySgsr(sgsrEnabled, mode) }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(mode, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nitidez (Sharpness): ${"%.2f".format(sgsrSharpness)}", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                Slider(value = sgsrSharpness, onValueChange = { sgsrSharpness = it }, valueRange = 0.0f..1.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))

                Spacer(modifier = Modifier.height(16.dp))
                Text("LOSSLESS SCALING (LSFG)", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).alpha(if (isDllReady) 1f else 0.4f).padding(12.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Ativar LSFG", color = theme.text, fontSize = 12.sp)
                            Switch(checked = lsfgEnabled && isDllReady, enabled = isDllReady, onCheckedChange = { lsfgEnabled = it; VulkanNativeBridge.applyLsfg(it, lsfgMultiplier, lsfgPacing) }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                        }
                        if (!isDllReady) {
                            Text("Requer Lossless.dll no Hub", color = Color(0xFFFF5252), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Multiplicador", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("2x", "3x", "4x").forEach { mult ->
                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (lsfgMultiplier == mult) theme.primary else theme.surface.copy(alpha = 0.6f)).clickable { lsfgMultiplier = mult; VulkanNativeBridge.applyLsfg(lsfgEnabled, mult, lsfgPacing) }.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                            Text(mult, color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Sincronia (Frame Pacing)", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                                Switch(checked = lsfgPacing, onCheckedChange = { lsfgPacing = it; VulkanNativeBridge.applyLsfg(lsfgEnabled, lsfgMultiplier, it) }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Modo Performance", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                                Switch(checked = lsfgPerfMode, onCheckedChange = { lsfgPerfMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
            }
            2 -> {
                Text("CONTROLE DA VENTOINHA", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val fanModes = listOf(0 to "Smart", 1 to "Quiet", 2 to "Sport")
                    for ((modeValue, modeName) in fanModes) {
                        val isSel = fanMode == modeValue
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else Color.Transparent).clickable { fanMode = modeValue; ShellExecutor().setIntSystemSetting("fan_mode", modeValue) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(modeName, color = if (isSel) Color.White else theme.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("MODO DE LIMITAÇÃO", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.3f)).border(1.dp, theme.text.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (activeLimitMode == "TDP") theme.primary else Color.Transparent).clickable { activeLimitMode = "TDP" }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("TDP", color = if (activeLimitMode == "TDP") Color.White else theme.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (activeLimitMode == "CLOCK") theme.primary else Color.Transparent).clickable { activeLimitMode = "CLOCK" }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Clocks", color = if (activeLimitMode == "CLOCK") Color.White else theme.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (activeLimitMode == "TDP") {
                    Text("PERFIS DE TDP", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val tdpProfiles = listOf("Power Save" to 5f, "Balanced" to 10f, "Triple A" to 15f, "Stock" to 25f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((profName, watts) in tdpProfiles) {
                            val isSel = tdpValue == watts
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.5f)).clickable { tdpValue = watts; performanceManager.applyDynamicTdp(watts) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text(profName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    val userTdpProfiles = prefs.getAllCustomProfiles().filter { it.type == "TDP" }
                    if (userTdpProfiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (prof in userTdpProfiles) {
                                val isSel = tdpValue == prof.v1
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.5f)).clickable { tdpValue = prof.v1; performanceManager.applyDynamicTdp(prof.v1) }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(prof.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(12.dp)) {
                        Text("TDP Limit: ${tdpValue.toInt()} W", color = theme.text, fontSize = 12.sp)
                        Slider(value = tdpValue, onValueChange = { tdpValue = it; performanceManager.applyDynamicTdp(it) }, valueRange = 5f..25f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = savedPresetName, onValueChange = { savedPresetName = it }, label = { Text("Nome do Preset", fontSize = 10.sp) }, modifier = Modifier.weight(1f).height(50.dp), textStyle = TextStyle(fontSize = 12.sp, color = theme.text))
                        Button(onClick = { if (savedPresetName.isNotBlank()) { prefs.saveCustomProfile(savedPresetName, "TDP", tdpValue, 0f, 0f, 0f); savedPresetName = "" } }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary), modifier = Modifier.height(50.dp)) { Text("Salvar", fontSize = 11.sp, color = Color.White) }
                    }
                } else {
                    Text("PERFIS DE UNDERCLOCK", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val clockProfiles = listOf("Power Save", "Balanced", "Triple A", "Stock")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (profName in clockProfiles) {
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(theme.surface.copy(alpha = 0.5f)).clickable {
                                when (profName) { "Power Save" -> { cpuPerfClock = 1735f; cpuPrimeClock = 2246f; gpuClock = 160f }; "Balanced" -> { cpuPerfClock = 2400f; cpuPrimeClock = 3000f; gpuClock = 500f }; "Triple A" -> { cpuPerfClock = 3000f; cpuPrimeClock = 3800f; gpuClock = 800f }; "Stock" -> { cpuPerfClock = 3530f; cpuPrimeClock = 4320f; gpuClock = 1100f } }
                                performanceManager.applyAbsoluteClocks((cpuPerfClock * 1000).toLong(), (cpuPrimeClock * 1000).toLong(), (gpuClock * 1000000).toLong())
                            }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text(profName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    val userClockProfiles = prefs.getAllCustomProfiles().filter { it.type == "CLOCK" }
                    if (userClockProfiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (prof in userClockProfiles) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.surface.copy(alpha = 0.5f)).clickable { cpuPerfClock = prof.v2; cpuPrimeClock = prof.v3; gpuClock = prof.v4; performanceManager.applyAbsoluteClocks((prof.v2 * 1000).toLong(), (prof.v3 * 1000).toLong(), (prof.v4 * 1000000).toLong()) }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(prof.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(12.dp)) {
                        Text("CPU Perf Cores: ${cpuPerfClock.toInt()} MHz", color = theme.text, fontSize = 11.sp)
                        Slider(value = cpuPerfClock, onValueChange = { cpuPerfClock = it; performanceManager.applyAbsoluteClocks((it * 1000).toLong(), (cpuPrimeClock * 1000).toLong(), (gpuClock * 1000000).toLong()) }, valueRange = 1735f..3530f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("CPU Prime Cores: ${cpuPrimeClock.toInt()} MHz", color = theme.text, fontSize = 11.sp)
                        Slider(value = cpuPrimeClock, onValueChange = { cpuPrimeClock = it; performanceManager.applyAbsoluteClocks((cpuPerfClock * 1000).toLong(), (it * 1000).toLong(), (gpuClock * 1000000).toLong()) }, valueRange = 2246f..4320f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Adreno GPU: ${gpuClock.toInt()} MHz", color = theme.text, fontSize = 11.sp)
                        Slider(value = gpuClock, onValueChange = { gpuClock = it; performanceManager.applyAbsoluteClocks((cpuPerfClock * 1000).toLong(), (cpuPrimeClock * 1000).toLong(), (it * 1000000).toLong()) }, valueRange = 160f..1100f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = savedPresetName, onValueChange = { savedPresetName = it }, label = { Text("Nome do Preset", fontSize = 10.sp) }, modifier = Modifier.weight(1f).height(50.dp), textStyle = TextStyle(fontSize = 12.sp, color = theme.text))
                        Button(onClick = { if (savedPresetName.isNotBlank()) { prefs.saveCustomProfile(savedPresetName, "CLOCK", 0f, cpuPerfClock, cpuPrimeClock, gpuClock); savedPresetName = "" } }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary), modifier = Modifier.height(50.dp)) { Text("Salvar", fontSize = 11.sp, color = Color.White) }
                    }
                }
            }
        }
    }
}

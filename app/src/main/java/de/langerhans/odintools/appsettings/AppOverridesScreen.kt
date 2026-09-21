package de.langerhans.odintools.appsettings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush
import de.langerhans.odintools.models.ClusterClockPresets
import de.langerhans.odintools.models.CombinedClockProfiles
import de.langerhans.odintools.models.FanMode
import de.langerhans.odintools.ui.theme.AvailableThemes
import de.langerhans.odintools.ui.theme.ConsoleTheme
import de.langerhans.odintools.ui.theme.getResolvedTheme

@Composable
fun AppOverridesScreen(
    viewModel: AppOverrideViewModel = hiltViewModel(),
    navigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    var expandedLsfgMult by remember { mutableStateOf(false) }
    var expandedSgsrMode by remember { mutableStateOf(false) }
    var expandedReshade by remember { mutableStateOf(false) }

    val sgsrModes = listOf("Quality", "Balanced", "Performance", "Ultra")
    val isTdpMode = uiState.limitMode == "TDP"

    // Este ecra usava cores fixas (0xFF0F1115 / 0xFF1976D2) e era o unico da app a ignorar o
    // motor de temas -- por isso destoava do resto da interface. Agora segue o ConsoleTheme
    // escolhido pelo utilizador (e o modo AMOLED), tal como o ecra principal e o overlay.
    val rawTheme = AvailableThemes.getOrElse(uiState.selectedThemeIndex) { AvailableThemes[0] }
    val theme = getResolvedTheme(rawTheme, uiState.useAmoledBlack)
    val accentColor = theme.primary
    val mutedText = theme.text.copy(alpha = 0.6f)

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(theme.background.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.85f))
                )
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("REGRAS POR APLICATIVO", color = theme.text.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(uiState.appName, color = accentColor, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
                        Text(uiState.packageName, color = mutedText, fontSize = 12.sp)
                    }
                    TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); navigateBack() }) {
                        Text("Voltar", color = theme.primary, fontFamily = theme.fontFamily)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { Text("⚡ PERFORMANCE & HARDWARE", color = theme.text, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp) }

            item {
                // TDP e Clock são MUTUAMENTE EXCLUSIVOS -- mesmo modelo do overlay e do ecrã de
                // Settings global. Este jogo usa OU um limite de TDP OU clocks manuais, nunca os
                // dois; o toggle abaixo troca `limitMode`, tal como no overlay.
                AppOverrideCard(title = "Modo de Limite", subtitle = if (isTdpMode) "TDP" else "Clocks Manuais", enabled = true, onClick = null, theme = theme) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isTdpMode) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLimitMode("TDP") }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("TDP", color = theme.text, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (!isTdpMode) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLimitMode("CLOCK") }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("Clocks", color = theme.text, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (isTdpMode) {
                item {
                    AppOverrideCard(title = "Limite de TDP", subtitle = "${uiState.tdpWatts.toInt()} W", enabled = true, onClick = null, theme = theme) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Power Save" to 5f, "Balanced" to 10f, "Triple A" to 15f, "Stock" to 25f).forEach { (label, watts) ->
                                    val isSel = uiState.tdpWatts == watts
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateTdp(watts) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(label, color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Slider(value = uiState.tdpWatts, onValueChange = { viewModel.updateTdp(it) }, valueRange = 5f..25f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
                        }
                    }
                }
            } else {
                item {
                    // Presets discretos por cluster, ao estilo ClusterTune -- os MESMOS usados no
                    // overlay e no ecrã de Settings global (ver ClockPresets.kt). A GPU é sempre
                    // um controlo à parte: os perfis combinados abaixo nunca lhe tocam.
                    AppOverrideCard(title = "Clocks por Cluster", subtitle = "Perf ${uiState.perfClockMHz.toInt()} MHz | Prime ${uiState.primeClockMHz.toInt()} MHz | GPU ${uiState.gpuClockMHz.toInt()} MHz", enabled = true, onClick = null, theme = theme) {
                        Column {
                            Text("Perfis combinados (Perf + Prime -- GPU é independente)", color = mutedText, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CombinedClockProfiles.all.forEach { prof ->
                                    val isSel = uiState.perfClockMHz == prof.perfClockMHz && uiState.primeClockMHz == prof.primeClockMHz
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateClocks(prof.perfClockMHz, prof.primeClockMHz, uiState.gpuClockMHz) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(prof.label, color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Cluster Perf (Cluster 0): ${uiState.perfClockMHz.toInt()} MHz", color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ClusterClockPresets.perfPresets.forEach { preset ->
                                    val isSel = uiState.perfClockMHz == preset.clockMHz
                                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateClocks(preset.clockMHz, uiState.primeClockMHz, uiState.gpuClockMHz) }.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(preset.label, color = theme.text, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Cluster Prime (Cluster 1): ${uiState.primeClockMHz.toInt()} MHz", color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ClusterClockPresets.primePresets.forEach { preset ->
                                    val isSel = uiState.primeClockMHz == preset.clockMHz
                                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateClocks(uiState.perfClockMHz, preset.clockMHz, uiState.gpuClockMHz) }.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(preset.label, color = theme.text, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Adreno GPU (independente): ${uiState.gpuClockMHz.toInt()} MHz", color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ClusterClockPresets.gpuPresets.forEach { preset ->
                                    val isSel = uiState.gpuClockMHz == preset.clockMHz
                                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateClocks(uiState.perfClockMHz, uiState.primeClockMHz, preset.clockMHz) }.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(preset.label, color = theme.text, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppOverrideCard(title = "Resfriamento (Ventoinha)", subtitle = FanMode.fromSettingsValue(uiState.fanSettingsValue).shortLabel, enabled = true, onClick = null, theme = theme) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (FanMode.selectable + FanMode.Stock).forEach { mode ->
                            val isSel = uiState.fanSettingsValue == mode.settingsValue
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) accentColor else theme.text.copy(alpha = 0.08f)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateFanMode(mode.settingsValue) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(mode.shortLabel, color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { Text("📺 ECRÃ, UPSCALING & FRAME GEN", color = theme.text, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp) }

            item {
                AppOverrideCard(title = "Lossless Scaling (Frame Gen)", subtitle = "Multiplicação de quadros", enabled = true, onClick = null, theme = theme) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Ativar LSFG neste App", color = theme.text)
                            AppOverrideToggle(checked = uiState.lsfgEnabled, accent = accentColor) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLsfg(it, uiState.lsfgMultiplier, uiState.lsfgFramePacing, uiState.lsfgPerformanceMode, uiState.lsfgQuality) }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Multiplicador", color = theme.text)
                            Box {
                                Text(uiState.lsfgMultiplier, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if(uiState.lsfgEnabled){ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); expandedLsfgMult = true } }.alpha(if(uiState.lsfgEnabled) 1f else 0.5f))
                                DropdownMenu(expanded = expandedLsfgMult, onDismissRequest = { expandedLsfgMult = false }, modifier = Modifier.background(theme.surface)) {
                                    listOf("2x", "3x", "4x").forEach { mult ->
                                        DropdownMenuItem(text = { Text(mult, color = theme.text) }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLsfg(uiState.lsfgEnabled, mult, uiState.lsfgFramePacing, uiState.lsfgPerformanceMode, uiState.lsfgQuality); expandedLsfgMult = false })
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Frame Pacing", color = theme.text)
                            AppOverrideToggle(checked = uiState.lsfgFramePacing, accent = accentColor) { if(uiState.lsfgEnabled){ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLsfg(uiState.lsfgEnabled, uiState.lsfgMultiplier, it, uiState.lsfgPerformanceMode, uiState.lsfgQuality) } }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Modo Performance LSFG", color = theme.text)
                            AppOverrideToggle(checked = uiState.lsfgPerformanceMode, accent = accentColor) { if(uiState.lsfgEnabled){ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLsfg(uiState.lsfgEnabled, uiState.lsfgMultiplier, uiState.lsfgFramePacing, it, uiState.lsfgQuality) } }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Qualidade: ${(uiState.lsfgQuality * 100).toInt()}%", color = theme.text)
                        Slider(value = uiState.lsfgQuality, onValueChange = { viewModel.updateLsfg(uiState.lsfgEnabled, uiState.lsfgMultiplier, uiState.lsfgFramePacing, uiState.lsfgPerformanceMode, it) }, valueRange = 0.5f..1.0f, enabled = uiState.lsfgEnabled, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
                    }
                }
            }

            item {
                AppOverrideCard(title = "Snapdragon Super Resolution", subtitle = "Upscaling espacial dedicado", enabled = true, onClick = null, theme = theme) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Ativar SGSR", color = theme.text)
                            AppOverrideToggle(checked = uiState.sgsrEnabled, accent = accentColor) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateSgsr(it, uiState.sgsrMode, uiState.sgsrSharpness) }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Modo de Qualidade", color = theme.text)
                            Box {
                                Text(uiState.sgsrMode, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if(uiState.sgsrEnabled){ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); expandedSgsrMode = true } }.alpha(if(uiState.sgsrEnabled) 1f else 0.5f))
                                DropdownMenu(expanded = expandedSgsrMode, onDismissRequest = { expandedSgsrMode = false }, modifier = Modifier.background(theme.surface)) {
                                    sgsrModes.forEach { mode ->
                                        DropdownMenuItem(text = { Text(mode, color = theme.text) }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateSgsr(uiState.sgsrEnabled, mode, uiState.sgsrSharpness); expandedSgsrMode = false })
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Nitidez: ${(uiState.sgsrSharpness * 100).toInt()}%", color = theme.text)
                        Slider(value = uiState.sgsrSharpness, onValueChange = { viewModel.updateSgsr(uiState.sgsrEnabled, uiState.sgsrMode, it) }, valueRange = 0.0f..1.0f, enabled = uiState.sgsrEnabled, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
                    }
                }
            }

            item {
                AppOverrideCard(title = "ReShade & Calibração de Cor", subtitle = uiState.reshadeProfile, enabled = true, onClick = null, theme = theme) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Perfil ReShade", color = theme.text)
                            Box {
                                Text(uiState.reshadeProfile, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); expandedReshade = true })
                                DropdownMenu(expanded = expandedReshade, onDismissRequest = { expandedReshade = false }, modifier = Modifier.background(theme.surface)) {
                                    uiState.availableReshadeProfiles.forEach { profile ->
                                        DropdownMenuItem(text = { Text(profile, color = theme.text) }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateDisplayColor(profile, uiState.saturationOverride, uiState.temperatureOverride); expandedReshade = false })
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Saturação: ${"%.1f".format(uiState.saturationOverride)}", color = theme.text)
                        Slider(value = uiState.saturationOverride, onValueChange = { viewModel.updateDisplayColor(uiState.reshadeProfile, it, uiState.temperatureOverride) }, valueRange = 0.0f..2.0f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Temperatura: ${uiState.temperatureOverride.toInt()}K", color = theme.text)
                        Slider(value = uiState.temperatureOverride, onValueChange = { viewModel.updateDisplayColor(uiState.reshadeProfile, uiState.saturationOverride, it) }, valueRange = 4000f..9000f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (uiState.isSaved) {
                        Button(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.deleteOverride(); navigateBack() },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Remover", color = theme.text, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.saveOverride(); navigateBack() },
                        modifier = Modifier.weight(1.5f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar Regra", color = theme.text, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Cartão deste ecrã, agora alinhado com o `ConsoleCard` do ecrã principal: mesma forma de 8 dp,
 * mesma superfície translúcida do tema e mesma faixa escura por baixo do conteúdo. Antes tinha
 * cantos de 12 dp e cores fixas em branco, o que fazia este ecrã destoar visivelmente do resto.
 */
@Composable
fun AppOverrideCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    theme: ConsoleTheme,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().border(1.dp, theme.text.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).alpha(if (enabled) 1f else 0.3f)
            .then(if (enabled && onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.text, fontFamily = theme.fontFamily)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = theme.text.copy(alpha = 0.6f), fontFamily = theme.fontFamily)
            }
            HorizontalDivider(color = theme.text.copy(alpha = 0.1f), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}

@Composable
fun AppOverrideToggle(checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(targetValue = if (checked) 24.dp else 4.dp, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "toggleMove")
    val bgColor by animateColorAsState(targetValue = if (checked) accent.copy(alpha = 0.3f) else Color.Transparent, label = "toggleBg")
    val thumbColor by animateColorAsState(targetValue = if (checked) accent else Color.Gray, label = "toggleThumb")

    Box(
        modifier = Modifier.width(44.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).background(bgColor).border(1.dp, thumbColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.padding(start = thumbOffset).size(16.dp).clip(RoundedCornerShape(4.dp)).background(thumbColor))
    }
}
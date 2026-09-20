package de.langerhans.odintools.appsettings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AppOverridesScreen(
    viewModel: AppOverrideViewModel = hiltViewModel(),
    navigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    var expandedTdp by remember { mutableStateOf(false) }
    var expandedClock by remember { mutableStateOf(false) }
    var expandedFan by remember { mutableStateOf(false) }

    val fanProfiles = listOf("Nenhum", "Silent (Silencioso)", "Smart (Balanceado)", "Sport (Desempenho Máximo)", "Stock (Padrão)")

    // LÓGICA DE EXCLUSIVIDADE: Um bloqueia o outro
    val isClockLocked = uiState.tdpProfile != "Nenhum"
    val isTdpLocked = uiState.clockProfile != "Nenhum"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "REGRAS POR APLICATIVO",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = uiState.appName,
                            color = Color(0xFF1976D2),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 28.sp
                        )
                        Text(
                            text = uiState.packageName,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); navigateBack() }) {
                        Text(text = "Voltar", color = Color.LightGray)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text("TDP (Potência)", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                OverrideGlassCard(
                    title = "Perfil de TDP",
                    subtitle = uiState.tdpProfile,
                    enabled = !isTdpLocked,
                    onClick = {
                        if (!isTdpLocked) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expandedTdp = true
                        }
                    }
                ) {
                    DropdownMenu(
                        expanded = expandedTdp,
                        onDismissRequest = { expandedTdp = false },
                        modifier = Modifier.background(Color(0xFF1A1D24))
                    ) {
                        uiState.availableTdpProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile, color = Color.White) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.updateTdpProfile(profile)
                                    expandedTdp = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text("Frequências (Underclock)", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                OverrideGlassCard(
                    title = "Perfil de Clocks",
                    subtitle = uiState.clockProfile,
                    enabled = !isClockLocked,
                    onClick = {
                        if (!isClockLocked) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expandedClock = true
                        }
                    }
                ) {
                    DropdownMenu(
                        expanded = expandedClock,
                        onDismissRequest = { expandedClock = false },
                        modifier = Modifier.background(Color(0xFF1A1D24))
                    ) {
                        uiState.availableClockProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile, color = Color.White) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.updateClockProfile(profile)
                                    expandedClock = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text("Resfriamento Ativo", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                OverrideGlassCard(
                    title = "Velocidade da Ventoinha",
                    subtitle = uiState.fanProfile,
                    enabled = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expandedFan = true
                    }
                ) {
                    DropdownMenu(
                        expanded = expandedFan,
                        onDismissRequest = { expandedFan = false },
                        modifier = Modifier.background(Color(0xFF1A1D24))
                    ) {
                        fanProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile, color = Color.White) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.updateFanProfile(profile)
                                    expandedFan = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.isSaved) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.deleteOverride()
                                navigateBack()
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Remover", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.saveOverride()
                            navigateBack()
                        },
                        modifier = Modifier.weight(1.5f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Salvar Perfil Ativo", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun OverrideGlassCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val alphaValue = if (enabled) 1f else 0.3f
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .alpha(alphaValue)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 14.sp, color = Color.Gray)
            content()
        }
    }
}

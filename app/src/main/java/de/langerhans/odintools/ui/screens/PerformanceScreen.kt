package de.langerhans.odintools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.langerhans.odintools.models.CustomTdpProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    navigateBack: () -> Unit,
    savedCustomTdfs: List<CustomTdpProfile>,
    onSaveCustomTdp: (String, Float) -> Unit,
    onDeleteCustomTdp: (String) -> Unit,
    onSelectTdp: (Float?, String) -> Unit
) {
    var isAutoTdpEnabled by remember { mutableStateOf(false) }
    var selectedProfileName by remember { mutableStateOf("Balanced (11W)") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var customNameInput by remember { mutableStateOf("") }
    var customWattsInput by remember { mutableStateOf(11f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabeçalho da Página
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gerenciamento de Performance",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = navigateBack) {
                        Text(text = "Voltar", color = Color.LightGray)
                    }
                }
            }

            // Bloco 1: Master Switch do AutoTDP
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AutoTDP Dinâmico",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "PT: Monitora os quadros por segundo (FPS) e ajusta automaticamente os clocks para manter a fluidez com menor consumo de bateria.\n\nEN: Monitors FPS and automatically trims CPU/GPU clocks to hold your target framerate with minimal battery drain.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = isAutoTdpEnabled,
                                onCheckedChange = { isAutoTdpEnabled = it }
                            )
                        }
                    }
                }
            }

            // Bloco 2: Perfis Fixos de TDP
            item {
                Text(
                    text = "Perfis de TDP (Potência Energética)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TdpProfileOption(
                        title = "Power Save (5W)",
                        description = "Mínimo seguro para jogos leves e máxima economia de bateria.",
                        isSelected = selectedProfileName == "Power Save (5W)",
                        onClick = {
                            selectedProfileName = "Power Save (5W)"
                            onSelectTdp(5f, "Power Save (5W)")
                        }
                    )
                    TdpProfileOption(
                        title = "Balanced (11W)",
                        description = "O ponto ideal para emuladores de PS2, GameCube e jogos nativos.",
                        isSelected = selectedProfileName == "Balanced (11W)",
                        onClick = {
                            selectedProfileName = "Balanced (11W)"
                            onSelectTdp(11f, "Balanced (11W)")
                        }
                    )
                    TdpProfileOption(
                        title = "Triple A (14.5W)",
                        description = "Desempenho máximo controlado para jogos pesados de PC e Switch.",
                        isSelected = selectedProfileName == "Triple A (14.5W)",
                        onClick = {
                            selectedProfileName = "Triple A (14.5W)"
                            onSelectTdp(14.5f, "Triple A (14.5W)")
                        }
                    )
                    TdpProfileOption(
                        title = "Stock (Padrão AYN)",
                        description = "Desativa os limites do aplicativo e usa o gerenciamento original.",
                        isSelected = selectedProfileName == "Stock",
                        onClick = {
                            selectedProfileName = "Stock"
                            onSelectTdp(null, "Stock")
                        }
                    )
                }
            }

            // Bloco 3: Seção de Perfis Personalizados Salvos pelo Usuário
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Perfis Personalizados",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "+ Criar Perfil", color = Color.White)
                    }
                }
            }

            items(savedCustomTdfs) { profile ->
                GlassCard(
                    modifier = Modifier.clickable {
                        selectedProfileName = profile.name
                        onSelectTdp(profile.watts, profile.name)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = profile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "Limite de TDP: ${profile.watts} W", color = Color.Gray, fontSize = 13.sp)
                        }
                        TextButton(onClick = { onDeleteCustomTdp(profile.id) }) {
                            Text(text = "Excluir", color = Color(0xFFEF5350))
                        }
                    }
                }
            }
        }

        // Diálogo Flutuante em Glassmorphism para Criar Perfil Customizado
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                containerColor = Color(0xFF1A1D24),
                title = { Text(text = "Criar Novo Perfil de TDP", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = customNameInput,
                            onValueChange = { customNameInput = it },
                            label = { Text("Nome do Perfil (Ex: Hollow Knight)") },
                            singleLine = true
                        )
                        Text(text = "Potência: ${customWattsInput.toInt()} W", color = Color.LightGray)
                        Slider(
                            value = customWattsInput,
                            onValueChange = { customWattsInput = it },
                            valueRange = 5f..25f,
                            steps = 20
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customNameInput.isNotBlank()) {
                                onSaveCustomTdp(customNameInput, customWattsInput)
                                customNameInput = ""
                                showCreateDialog = false
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1AFFFFFF))
    ) {
        content()
    }
}

@Composable
fun TdpProfileOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, color = Color.Gray, fontSize = 12.sp)
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

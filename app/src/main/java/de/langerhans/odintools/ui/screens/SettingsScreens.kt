package de.langerhans.odintools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import de.langerhans.odintools.R
import de.langerhans.odintools.main.MainUiModel
import de.langerhans.odintools.main.MainViewModel
import de.langerhans.odintools.ui.composables.*

@Composable
fun SettingsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    navigateToOverrideList: () -> Unit
) {
    val uiState: MainUiModel by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Cores base estilo Armoury Crate (Vermelho e Preto)
    val bgColor = Color(0xFF0D0D0D)
    val surfaceColor = Color(0xFF1A1A1A)
    val accentColor = Color(0xFFE5002B)
    val textColor = Color(0xFFE0E0E0)

    // Diálogos de Sistema Mantidos
    if (uiState.showPServerNotAvailableDialog) PServerNotAvailableDialog()
    else if (uiState.showIncompatibleDeviceDialog) NotAnOdinDialog { viewModel.incompatibleDeviceDialogDismissed() }

    if (uiState.showSaturationDialog) {
        SaturationPreferenceDialog(
            initialValue = uiState.currentSaturation,
            onCancel = { viewModel.saturationDialogDismissed() },
            onSave = { viewModel.saveSaturation(it) },
        )
    }

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            ConsoleBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                surfaceColor = surfaceColor,
                accentColor = accentColor,
                textColor = textColor
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when (selectedTab) {
                0 -> HubPanel(uiState, viewModel, navigateToOverrideList, surfaceColor, accentColor, textColor)
                1 -> ConfigPanel(surfaceColor, accentColor, textColor)
                2 -> AboutPanel(surfaceColor, accentColor, textColor)
            }
        }
    }
}

@Composable
fun HubPanel(
    uiState: MainUiModel,
    viewModel: MainViewModel,
    navigateToOverrideList: () -> Unit,
    surfaceColor: Color,
    accentColor: Color,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CONSOLE HUB",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = accentColor,
            letterSpacing = 2.sp
        )

        ArmouryCard(title = "Desempenho & Aplicativos", surfaceColor = surfaceColor, accentColor = accentColor) {
            SwitchableTriggerPreference(
                icon = R.drawable.ic_app_settings,
                title = R.string.appOverrides,
                description = R.string.appOverridesDescription,
                state = uiState.appOverridesEnabled,
                onClick = navigateToOverrideList,
            ) { viewModel.appOverridesEnabled(it) }
        }

        ArmouryCard(title = "Hardware & Sistema", surfaceColor = surfaceColor, accentColor = accentColor) {
            TriggerPreference(
                icon = R.drawable.ic_palette,
                title = R.string.saturation,
                description = R.string.saturationDescription,
            ) { viewModel.saturationClicked() }

            SwitchPreference(
                icon = R.drawable.ic_home,
                title = R.string.singlePressHome,
                description = R.string.singlePressHomeDescription,
                state = uiState.singlePressHomeEnabled,
            ) { viewModel.updateSinglePressHomePreference(it) }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ConfigPanel(surfaceColor: Color, accentColor: Color, textColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CONFIGURAÇÕES",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = accentColor,
            letterSpacing = 2.sp
        )

        ArmouryCard(title = "Personalização Visual", surfaceColor = surfaceColor, accentColor = accentColor) {
            // Placeholders para as funções que vamos conectar na próxima etapa
            PreferenceItemStub(title = "Tema do App", subtitle = "Toque para alterar o tema visual (Ex: Armoury, Steam, Xbox)")
            PreferenceItemStub(title = "Idioma", subtitle = "Português (PT-BR)")
        }
    }
}

@Composable
fun AboutPanel(surfaceColor: Color, accentColor: Color, textColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SOBRE O PROJETO",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = accentColor,
            letterSpacing = 2.sp
        )

        ArmouryCard(title = "OdinTools Remastered", surfaceColor = surfaceColor, accentColor = accentColor) {
            PreferenceItemStub(title = "Desenvolvedor", subtitle = "Seu Nome Aqui (Forked de langerhans)")
            PreferenceItemStub(title = "Código Fonte", subtitle = "github.com/seu-usuario/OdinTools")
            PreferenceItemStub(title = "Versão Atual", subtitle = "1.3.1 (Android 13/15 Support)")
        }

        Button(
            onClick = { /* Lógica de Update Futura */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("VERIFICAR ATUALIZAÇÕES", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun ArmouryCard(title: String, surfaceColor: Color, accentColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            content()
        }
    }
}

@Composable
fun PreferenceItemStub(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Ação Futura */ }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = title, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
fun ConsoleBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    surfaceColor: Color,
    accentColor: Color,
    textColor: Color
) {
    NavigationBar(
        containerColor = surfaceColor,
        tonalElevation = 8.dp,
        modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Build, contentDescription = "Hub") },
            label = { Text("Hub", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = accentColor,
                indicatorColor = accentColor,
                unselectedIconColor = textColor.copy(alpha = 0.5f),
                unselectedTextColor = textColor.copy(alpha = 0.5f)
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Config") },
            label = { Text("Config", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = accentColor,
                indicatorColor = accentColor,
                unselectedIconColor = textColor.copy(alpha = 0.5f),
                unselectedTextColor = textColor.copy(alpha = 0.5f)
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Info, contentDescription = "Sobre") },
            label = { Text("Sobre", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = accentColor,
                indicatorColor = accentColor,
                unselectedIconColor = textColor.copy(alpha = 0.5f),
                unselectedTextColor = textColor.copy(alpha = 0.5f)
            )
        )
    }
}

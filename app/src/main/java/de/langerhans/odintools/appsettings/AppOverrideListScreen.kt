package de.langerhans.odintools.appsettings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import de.langerhans.odintools.R

@Composable
fun AppOverrideListScreen(viewModel: AppOverrideListViewModel = hiltViewModel(), navigateToOverrides: (packageName: String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showAppSelectDialog) {
        AppPickerDialog(
            apps = uiState.overrideCandidates,
            onAppSelected = {
                viewModel.dismissAppSelectDialog()
                navigateToOverrides(it)
            },
            onDismiss = { viewModel.dismissAppSelectDialog() }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "JOGOS COM REGRAS ATIVAS",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2).copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.addClicked() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(painterResource(id = R.drawable.ic_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Text("Adicionar Novo Jogo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }

            items(uiState.overrideList) { app ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).clickable { navigateToOverrides(app.packageName) }
                ) {
                    AppItem(app.packageName, app.appName, app.appIcon, 48.dp, app.subtitle) {}
                }
            }
        }
    }
}

@Composable
fun AppItem(packageName: String, label: String, icon: Drawable, iconSize: Dp = 48.dp, subLabel: String? = "", onClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Image(painter = rememberDrawablePainter(drawable = icon), contentDescription = null, Modifier.size(iconSize))
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (!subLabel.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subLabel, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AppPickerDialog(apps: List<AppUiModel>, onAppSelected: (String) -> Unit, onDismiss: () -> Unit) {
    var searchText by rememberSaveable { mutableStateOf("") }
    val filteredApps = apps.filter {
        it.appName.contains(searchText, ignoreCase = true) || it.packageName.contains(searchText, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1D24),
        title = { Text("Selecione um Jogo", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Procurar...", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(filteredApps) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app.packageName) }.padding(vertical = 8.dp)
                        ) {
                            Image(painter = rememberDrawablePainter(drawable = app.appIcon), contentDescription = null, Modifier.size(36.dp))
                            Text(app.appName, color = Color.LightGray, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray) }
        }
    )
}

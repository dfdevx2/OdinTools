package de.langerhans.odintools.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.langerhans.odintools.tools.PermissionChecker

@Composable
fun PermissionOnboardingWrapper(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estados locais que monitoram as permissões em tempo real
    var hasUsage by remember { mutableStateOf(PermissionChecker.hasUsageAccess(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionChecker.hasOverlayAccess(context)) }

    // Ouve o ciclo de vida da Activity: reavalia as permissões assim que o usuário volta do menu do Android
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsage = PermissionChecker.hasUsageAccess(context)
                hasOverlay = PermissionChecker.hasOverlayAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allGranted = hasUsage && hasOverlay

    if (!allGranted) {
        // Tela de Configuração Inicial mantendo a paleta escura e o estilo Glassmorphism
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1115)),
            contentAlignment = Alignment.Center
        ) {
            // Cartão com efeito translúcido (Glassmorphism)
            Box(
                modifier = Modifier
                    .width(620.dp)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x1AFFFFFF)) // Fundo translúcido estilo vidro fosco
                    .padding(32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Bem-vindo ao Odin Hub",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Para que o monitoramento de jogos e o controle de TDP operem perfeitamente em segundo plano, precisamos ativar as permissões essenciais abaixo:",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Item 1: Acesso a Dados de Uso (Fundamental para o AutoTDP por app)
                    PermissionCardItem(
                        title = "1. Acesso a Dados de Uso",
                        description = "Necessário para identificar quando você abre um jogo e disparar o perfil correto.",
                        isGranted = hasUsage,
                        onClick = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        }
                    )

                    // Item 2: Sobreposição de Tela (Fundamental para OSD e painel flutuante)
                    PermissionCardItem(
                        title = "2. Sobreposição sobre Outros Apps",
                        description = "Necessário para exibir controles flutuantes e notificações em tempo real.",
                        isGranted = hasOverlay,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    } else {
        // Se todas as permissões estiverem ativas, libera a interface principal que você construiu
        content()
    }
}

@Composable
fun PermissionCardItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0DFFFFFF))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) Color(0xFF2E7D32) else Color(0xFF1976D2)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (isGranted) "Ativado" else "Configurar",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
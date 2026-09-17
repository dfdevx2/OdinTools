package de.langerhans.odintools.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppTheme { LIGHT, DARK, AMOLED, DYNAMIC, XBOX, STEAM, SNES }
enum class AppLanguage { PT, EN }

@Composable
fun WelcomeScreen(onStartHub: () -> Unit) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(AppTheme.DARK) }
    var currentLanguage by remember { mutableStateOf(AppLanguage.PT) }

    val dynamicBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context).background else Color(0xFF1F2937)
    val dynamicAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context).primary else Color(0xFF3B82F6)

    val backgroundColor by animateColorAsState(
        targetValue = when (currentTheme) {
            AppTheme.LIGHT -> Color(0xFFF3F4F6)
            AppTheme.DARK -> Color(0xFF1F2937)
            AppTheme.AMOLED -> Color.Black
            AppTheme.DYNAMIC -> dynamicBg
            AppTheme.XBOX -> Color(0xFF101E13)
            AppTheme.STEAM -> Color(0xFF171A21)
            AppTheme.SNES -> Color(0xFF1F1C26)
        },
        animationSpec = tween(durationMillis = 500),
        label = "BgColorAnimation"
    )

    val accentColor by animateColorAsState(
        targetValue = when (currentTheme) {
            AppTheme.XBOX -> Color(0xFF107C10)
            AppTheme.STEAM -> Color(0xFF66C0F4)
            AppTheme.SNES -> Color(0xFF7A68A8)
            AppTheme.DYNAMIC -> dynamicAccent
            else -> Color(0xFF3B82F6)
        },
        animationSpec = tween(durationMillis = 300),
        label = "AccentColorAnimation"
    )

    val textColor = if (currentTheme == AppTheme.LIGHT) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (currentLanguage == AppLanguage.PT) "Bem-vindo ao OdinTools" else "Welcome to OdinTools",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (currentLanguage == AppLanguage.PT) "Escolha seu setup inicial" else "Choose your initial setup",
            fontSize = 16.sp,
            color = textColor.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = if (currentLanguage == AppLanguage.PT) "TEMA VISUAL" else "VISUAL THEME",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConsoleCard("Claro", currentTheme == AppTheme.LIGHT, accentColor, textColor) { currentTheme = AppTheme.LIGHT }
            ConsoleCard("Escuro", currentTheme == AppTheme.DARK, accentColor, textColor) { currentTheme = AppTheme.DARK }
            ConsoleCard("AMOLED", currentTheme == AppTheme.AMOLED, accentColor, textColor) { currentTheme = AppTheme.AMOLED }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConsoleCard("Xbox", currentTheme == AppTheme.XBOX, accentColor, textColor) { currentTheme = AppTheme.XBOX }
            ConsoleCard("Steam", currentTheme == AppTheme.STEAM, accentColor, textColor) { currentTheme = AppTheme.STEAM }
            ConsoleCard("SNES", currentTheme == AppTheme.SNES, accentColor, textColor) { currentTheme = AppTheme.SNES }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConsoleCard("Dinâmico", currentTheme == AppTheme.DYNAMIC, accentColor, textColor) { currentTheme = AppTheme.DYNAMIC }
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (currentLanguage == AppLanguage.PT) "IDIOMA" else "LANGUAGE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConsoleCard("Português", currentLanguage == AppLanguage.PT, accentColor, textColor) { currentLanguage = AppLanguage.PT }
            ConsoleCard("English", currentLanguage == AppLanguage.EN, accentColor, textColor) { currentLanguage = AppLanguage.EN }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onStartHub() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Text(
                text = if (currentLanguage == AppLanguage.PT) "INICIAR HUB" else "START HUB",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (currentTheme == AppTheme.LIGHT) Color.White else Color.Black
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun RowScope.ConsoleCard(
    title: String,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(if (isPressed) 2.dp else if (isSelected) 8.dp else 4.dp, label = "elevation")

    Card(
        modifier = Modifier
            .weight(1f)
            .height(72.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick() }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) accentColor else textColor.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) accentColor else textColor.copy(alpha = 0.8f)
            )
        }
    }
}

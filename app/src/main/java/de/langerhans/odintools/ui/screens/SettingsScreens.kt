@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package de.langerhans.odintools.ui.screens

import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import de.langerhans.odintools.R
import de.langerhans.odintools.main.MainUiModel
import de.langerhans.odintools.main.MainViewModel
import de.langerhans.odintools.tools.SettingsRepo
import de.langerhans.odintools.tools.hardware.LosslessManager
import de.langerhans.odintools.ui.composables.*
import de.langerhans.odintools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: MainViewModel = hiltViewModel(), navigateToOverrideList: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showWelcomeSetup by rememberSaveable { mutableStateOf(viewModel.isFirstRun()) }
    var showBootAnimation by rememberSaveable { mutableStateOf(false) }

    var currentThemeIndex by rememberSaveable { mutableIntStateOf(1) }
    var useAmoledBlack by rememberSaveable { mutableStateOf(false) }
    var currentLanguage by rememberSaveable { mutableStateOf("Português (PT-BR)") }
    val isEn = currentLanguage == "English (US)"

    val rawTheme = AvailableThemes.getOrElse(currentThemeIndex) { AvailableThemes[0] }
    val finalTheme = getResolvedTheme(rawTheme, useAmoledBlack)

    var liveWallpaperType by rememberSaveable { mutableStateOf("Static") }
    var blurEnabled by rememberSaveable { mutableStateOf(true) }
    var blurIntensity by rememberSaveable { mutableFloatStateOf(0.4f) }
    var wallpaperOpacity by rememberSaveable { mutableFloatStateOf(0.85f) }

    var selectedStaticRes by rememberSaveable { mutableIntStateOf(R.drawable.static_wallpaper_1) }
    var selectedCustomUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCustomUri = selectedCustomUriString?.let { Uri.parse(it) }
    var selectedWallpaperName by rememberSaveable { mutableStateOf(if (isEn) "Preset 1" else "Predefinição 1") }

    fun playSfx(resId: Int) {
        runCatching { MediaPlayer.create(context, resId)?.apply { setVolume(0.8f, 0.8f); setOnCompletionListener { release() }; start() } }
    }

    if (showWelcomeSetup) {
        OdinHubWelcomeScreen(
            theme = finalTheme,
            isEn = isEn,
            currentThemeIndex = currentThemeIndex,
            amoledBlack = useAmoledBlack,
            onThemeChange = { currentThemeIndex = it },
            onAmoledToggle = { useAmoledBlack = it },
            onLanguageChange = { currentLanguage = it },
            onFinish = {
                viewModel.finishWelcomeSetup()
                showWelcomeSetup = false
                showBootAnimation = true
            }
        )
        return
    }

    Crossfade(targetState = showBootAnimation, animationSpec = tween(800), label = "BootTransition") { isBooting ->
        if (isBooting) {
            VideoBootScreen(theme = finalTheme, onVideoEnded = { showBootAnimation = false; playSfx(R.raw.sfx_select) })
        } else {
            Box(modifier = Modifier.fillMaxSize().background(finalTheme.background)) {

                Box(modifier = Modifier.fillMaxSize().alpha(wallpaperOpacity)) {
                    if (liveWallpaperType == "Live (MP4)") {
                        val activeVideoUri = if (selectedCustomUri != null && selectedCustomUri.toString().startsWith("content://")) selectedCustomUri else Uri.parse("android.resource://${context.packageName}/${R.raw.live_wallpaper}")
                        LiveWallpaperRenderer(uri = activeVideoUri)
                        if (blurEnabled) {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.2f + (blurIntensity * 0.5f)), Color.Black.copy(alpha = 0.4f + (blurIntensity * 0.5f))))))
                        }
                    } else {
                        StaticWallpaperRenderer(resId = if (selectedCustomUri == null) selectedStaticRes else null, uri = if (selectedCustomUri != null) selectedCustomUri else null, blurModifier = if (blurEnabled) Modifier.blur((blurIntensity * 40).dp) else Modifier)
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(finalTheme.background.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.85f)))))

                Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    Text("ODIN HUB", fontSize = 28.sp, fontFamily = finalTheme.fontFamily, fontWeight = FontWeight.Black, color = finalTheme.primary, letterSpacing = 2.sp, modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp))
                    ConsoleMenuBar(selectedTab = selectedTab, theme = finalTheme, isEn = isEn) { if (selectedTab != it) { playSfx(R.raw.sfx_nav); selectedTab = it; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }

                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeIn() togetherWith slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut() },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                        label = "tab_anim"
                    ) { targetTab ->
                        when (targetTab) {
                            0 -> PerformancePanel(uiState, viewModel, finalTheme, isEn, navigateToOverrideList) { playSfx(R.raw.sfx_select) }
                            1 -> DisplayPanel(uiState, viewModel, finalTheme, isEn) { playSfx(R.raw.sfx_select) }
                            2 -> ControlsPanel(uiState, viewModel, finalTheme, isEn) { playSfx(R.raw.sfx_select) }
                            3 -> SystemPanel(uiState, viewModel, finalTheme, isEn, amoledBlack = useAmoledBlack, liveWallpaperType = liveWallpaperType, blurEnabled = blurEnabled, blurIntensity = blurIntensity, wallpaperOpacity = wallpaperOpacity, selectedWallpaperName = selectedWallpaperName, currentThemeIndex = currentThemeIndex, currentLanguage = currentLanguage, onThemeChange = { currentThemeIndex = it }, onLanguageChange = { currentLanguage = it }, onAmoledToggle = { useAmoledBlack = it }, onLiveWallpaperTypeChange = { liveWallpaperType = it }, onBlurToggle = { blurEnabled = it }, onBlurIntensityChange = { blurIntensity = it }, onWallpaperOpacityChange = { wallpaperOpacity = it }, onPresetStaticSelected = { res, name -> selectedCustomUriString = null; selectedStaticRes = res; selectedWallpaperName = name }, onPresetVideoSelected = { selectedCustomUriString = null; selectedWallpaperName = if (isEn) "Default Live Wallpaper" else "Live Wallpaper Padrão" }, onCustomUriSelected = { uri, name -> selectedCustomUriString = uri.toString(); selectedWallpaperName = name }, onReplayBoot = { showWelcomeSetup = true }) { playSfx(R.raw.sfx_select) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OdinHubWelcomeScreen(
    theme: ConsoleTheme, isEn: Boolean, currentThemeIndex: Int, amoledBlack: Boolean,
    onThemeChange: (Int) -> Unit, onAmoledToggle: (Boolean) -> Unit, onLanguageChange: (String) -> Unit, onFinish: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var expandedTheme by remember { mutableStateOf(false) }
    var expandedLang by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(420.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (isEn) "WELCOME TO ODIN HUB" else "BEM-VINDO AO ODIN HUB", fontSize = 24.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Black, color = theme.primary)

            ConsoleCard(if (isEn) "Language" else "Idioma", if (isEn) "English (US)" else "Português (PT-BR)", theme, playClick = { expandedLang = true }) {
                DropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }, modifier = Modifier.background(theme.surface)) {
                    listOf("Português (PT-BR)", "English (US)").forEach { lang ->
                        DropdownMenuItem(text = { Text(lang, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { onLanguageChange(lang); expandedLang = false; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) })
                    }
                }
            }

            ConsoleCard(if (isEn) "Theme" else "Tema", AvailableThemes[currentThemeIndex].name, theme, playClick = { expandedTheme = true }) {
                DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }, modifier = Modifier.background(theme.surface)) {
                    AvailableThemes.forEachIndexed { index, consoleTheme ->
                        DropdownMenuItem(text = { Text(consoleTheme.name, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { onThemeChange(index); expandedTheme = false; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) })
                    }
                }
            }

            ConsoleCard(if (isEn) "AMOLED Black" else "Preto AMOLED", if (isEn) "Absolute dark mode" else "Modo escuro absoluto", theme) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEn) "Force AMOLED Black" else "Forçar Preto AMOLED", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = amoledBlack, theme = theme, onCheckedChange = { onAmoledToggle(it); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) })
                }
            }

            Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onFinish() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                Text(if (isEn) "START" else "INICIAR", color = Color.White, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LiveWallpaperRenderer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL; volume = 0f } }
    LaunchedEffect(uri) { exoPlayer.setMediaItem(MediaItem.fromUri(uri)); exoPlayer.prepare(); exoPlayer.playWhenReady = true }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, modifier = Modifier.fillMaxSize())
}

@Composable
fun StaticWallpaperRenderer(resId: Int?, uri: Uri?, blurModifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(resId, uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(resId, uri) {
        withContext(Dispatchers.IO) {
            try {
                if (resId != null) {
                    val drawable = ContextCompat.getDrawable(context, resId)
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        bitmap = drawable.bitmap.asImageBitmap()
                    } else if (drawable != null) {
                        val source = ImageDecoder.createSource(context.resources, resId)
                        bitmap = ImageDecoder.decodeBitmap(source).asImageBitmap()
                    }
                } else if (uri != null) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    bitmap = ImageDecoder.decodeBitmap(source).asImageBitmap()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    bitmap?.let { Image(bitmap = it, contentDescription = "Static Wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(blurModifier)) }
}

@Composable
fun VideoBootScreen(theme: ConsoleTheme, onVideoEnded: () -> Unit) {
    val context = LocalContext.current
    val videoUri = "android.resource://${context.packageName}/${R.raw.boot_video}"
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener { override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) onVideoEnded() } })
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, modifier = Modifier.matchParentSize())
    }
}

@Composable
fun ConsoleMenuBar(selectedTab: Int, theme: ConsoleTheme, isEn: Boolean, onTabSelected: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ConsoleTabItem(0, "PERFORMANCE", R.drawable.ic_sliders, selectedTab, theme, onTabSelected)
        ConsoleTabItem(1, "DISPLAY", R.drawable.ic_palette, selectedTab, theme, onTabSelected)
        ConsoleTabItem(2, if (isEn) "CONTROLS" else "CONTROLES", R.drawable.ic_gamepad, selectedTab, theme, onTabSelected)
        ConsoleTabItem(3, if (isEn) "SYSTEM" else "SISTEMA", R.drawable.ic_app_settings, selectedTab, theme, onTabSelected)
    }
}

@Composable
fun ConsoleTabItem(index: Int, title: String, iconResId: Int, selectedTab: Int, theme: ConsoleTheme, onClick: (Int) -> Unit) {
    val isSelected = selectedTab == index
    val color by animateColorAsState(if (isSelected) theme.primary else theme.text.copy(alpha = 0.4f), label = "tabColor")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick(index) }.padding(8.dp)) {
        Icon(painterResource(iconResId), contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = color, fontSize = 12.sp, fontFamily = theme.fontFamily, fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 1.sp)
        Box(modifier = Modifier.height(3.dp).width(24.dp).clip(RoundedCornerShape(50)).background(if (isSelected) theme.primary else Color.Transparent))
    }
}

@Composable
fun PerformancePanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, isEn: Boolean, navigateToOverrideList: () -> Unit, playClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val isTdpMode = uiState.activeLimitMode == "TDP"
    val isClockMode = uiState.activeLimitMode == "CLOCK"

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader(if (isEn) "Hardware Limitation Mode" else "Modo de Limitação de Hardware", theme)
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.3f)).border(1.dp, theme.text.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(4.dp)) {
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isTdpMode) theme.primary.copy(alpha = 0.8f) else Color.Transparent).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLimitMode("TDP"); playClick() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (isEn) "Lock by TDP" else "Limitar por TDP", color = if (isTdpMode) Color.White else theme.text.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontFamily = theme.fontFamily)
            }
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isClockMode) theme.primary.copy(alpha = 0.8f) else Color.Transparent).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateLimitMode("CLOCK"); playClick() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (isEn) "Lock by Clocks" else "Limitar por Clocks", color = if (isClockMode) Color.White else theme.text.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontFamily = theme.fontFamily)
            }
        }

        ConsoleCard(if (isEn) "Dynamic AutoTDP Control" else "Controle Dinâmico AutoTDP", "Limit: ${uiState.tdpValue.toInt()} W", theme, enabled = isTdpMode) {
            Slider(value = uiState.tdpValue, onValueChange = { viewModel.updateTdp(it) }, valueRange = 5f..25f, enabled = isTdpMode, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
        }

        ConsoleCard(if (isEn) "Discrete Manual Clocks" else "Controle Manual de Clocks", "Perf Cores: ${uiState.cpuPerfClock.toInt()} MHz", theme, enabled = isClockMode) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(if (isEn) "Perf Cores (6x): ${uiState.cpuPerfClock.toInt()} MHz" else "Núcleos de Performance (6x): ${uiState.cpuPerfClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = uiState.cpuPerfClock, onValueChange = { viewModel.updateManualClocks(it, uiState.cpuPrimeClock, uiState.gpuClock) }, valueRange = 1735f..3530f, enabled = isClockMode, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Spacer(modifier = Modifier.height(12.dp))
                Text(if (isEn) "Prime Cores (2x): ${uiState.cpuPrimeClock.toInt()} MHz" else "Núcleos Prime (2x): ${uiState.cpuPrimeClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = uiState.cpuPrimeClock, onValueChange = { viewModel.updateManualClocks(uiState.cpuPerfClock, it, uiState.gpuClock) }, valueRange = 2246f..4320f, enabled = isClockMode, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Adreno GPU: ${uiState.gpuClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = uiState.gpuClock, onValueChange = { viewModel.updateManualClocks(uiState.cpuPerfClock, uiState.cpuPrimeClock, it) }, valueRange = 160f..1100f, enabled = isClockMode, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }

        ConsoleSectionHeader(if (isEn) "Game Rules & Per-App Overrides" else "Regras por Jogo e Aplicativo", theme)
        ConsoleCard(if (isEn) "Per-App Overrides" else "Overrides por Jogo", if (isEn) "Configure specific TDP & clock rules for emulators" else "Vincule perfis de TDP, Clocks, Tela e Fan a emuladores específicos", theme, playClick = playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEn) "Enable Overrides" else "Habilitar Overrides por App", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.appOverridesEnabled, theme = theme, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.appOverridesEnabled(it); playClick() })
            }
            TriggerPreference(icon = R.drawable.ic_app_settings, title = R.string.appOverrides, description = R.string.appOverridesDescription) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); playClick(); navigateToOverrideList() }
        }
    }
}

@Composable
fun DisplayPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, isEn: Boolean, playClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val profiles = listOf("Native", "Vibrant", "Cinema", "Retro", "HDR Boost", "Anime Edge", "Game Clarity")
    var expandedProfile by remember { mutableStateOf(false) }
    var expandedLsfgMult by remember { mutableStateOf(false) }
    var expandedSgsrMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader(if (isEn) "Upscaling & Frame Generation" else "Upscaling e Geração de Quadros", theme)

        ConsoleCard("Lossless Scaling (Global)", if (uiState.isDllImported) "Injeção Vulkan LSFG Pronta" else "ATENÇÃO: Lossless.dll ausente. Importe em Sistema.", theme) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEn) "Enable LSFG Globally" else "Ativar LSFG Globalmente", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = uiState.globalLsfgEnabled, theme = theme, onCheckedChange = { if (uiState.isDllImported) { viewModel.updateGlobalLsfg(it); playClick() } })
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Multiplicador", color = theme.text, fontFamily = theme.fontFamily)
                    Box {
                        Text(uiState.lsfgMultiplier, color = theme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if (uiState.globalLsfgEnabled) { expandedLsfgMult = true; playClick() } }.alpha(if(uiState.globalLsfgEnabled) 1f else 0.5f))
                        DropdownMenu(expanded = expandedLsfgMult, onDismissRequest = { expandedLsfgMult = false }, modifier = Modifier.background(theme.surface)) {
                            listOf("2x", "3x", "4x").forEach { mult -> DropdownMenuItem(text = { Text(mult, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { viewModel.updateLsfgOptions(mult, uiState.lsfgFramePacing); expandedLsfgMult = false; playClick() }) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sincronia (Frame Pacing)", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = uiState.lsfgFramePacing, theme = theme, onCheckedChange = { viewModel.updateLsfgOptions(uiState.lsfgMultiplier, it); playClick() })
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Exibir Contador de FPS LSFG", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = uiState.showFpsOverlay, theme = theme, onCheckedChange = { viewModel.toggleFpsOverlay(it); playClick() })
                }
            }
        }

        ConsoleCard("Snapdragon Super Resolution", "Spatial upscaling via GPU", theme) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEn) "Enable SGSR Globally" else "Ativar SGSR Globalmente", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = uiState.globalSgsrEnabled, theme = theme, onCheckedChange = { viewModel.updateGlobalSgsr(it); playClick() })
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Modo SGSR", color = theme.text, fontFamily = theme.fontFamily)
                    Box {
                        Text(uiState.sgsrMode, color = theme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if(uiState.globalSgsrEnabled) { expandedSgsrMode = true; playClick() } }.alpha(if(uiState.globalSgsrEnabled) 1f else 0.5f))
                        DropdownMenu(expanded = expandedSgsrMode, onDismissRequest = { expandedSgsrMode = false }, modifier = Modifier.background(theme.surface)) {
                            listOf("Quality", "Balanced", "Performance", "Ultra").forEach { mode -> DropdownMenuItem(text = { Text(mode, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { viewModel.updateSgsrOptions(mode); expandedSgsrMode = false; playClick() }) }
                        }
                    }
                }
            }
        }

        ConsoleSectionHeader(if (isEn) "ReShade & Color Calibration" else "ReShade e Calibração Vulkan", theme)
        ConsoleCard("Global Image Profiles", uiState.reshadeProfile, theme, playClick = { expandedProfile = true; playClick() }) {
            DropdownMenu(expanded = expandedProfile, onDismissRequest = { expandedProfile = false }, modifier = Modifier.background(theme.surface)) {
                profiles.forEach { profile -> DropdownMenuItem(text = { Text(profile, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { viewModel.applyReshadeProfile(profile); expandedProfile = false; playClick() }) }
            }
        }
        ConsoleCard("Ajustes Manuais", "Saturação & Temperatura", theme) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Saturação: ${"%.1f".format(uiState.currentSaturation)}", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = uiState.currentSaturation, onValueChange = { viewModel.saveSaturation(it) }, valueRange = 0.0f..2.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Spacer(Modifier.height(8.dp))
                Text("Temperatura: ${uiState.currentTemperature.toInt()}K", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = uiState.currentTemperature, onValueChange = { viewModel.saveTemperature(it) }, valueRange = 4000f..9000f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }
    }
}

@Composable
fun ControlsPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, isEn: Boolean, playClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader(if (isEn) "Mapping & Shortcuts" else "Mapeamento e Atalhos", theme)
        ConsoleCard(if (isEn) "System Buttons" else "Botões de Sistema", if (isEn) "General behavior" else "Comportamento geral", theme, playClick = playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEn) "Single Press Home" else "Toque Único no Home", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.singlePressHomeEnabled, theme = theme, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.updateSinglePressHomePreference(it); playClick() })
            }
        }
        ConsoleCard(if (isEn) "Back Buttons (Macro)" else "Botões Traseiros (Macro)", if (isEn) "Map M1 & M2" else "Mapear M1 e M2", theme, playClick = playClick) {
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m1Button, description = R.string.remapButtonDescription) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M1_VALUE) }
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m2Button, description = R.string.remapButtonDescription) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M2_VALUE) }
        }
    }
}

@Composable
fun SystemPanel(
    uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, isEn: Boolean, amoledBlack: Boolean,
    liveWallpaperType: String, blurEnabled: Boolean, blurIntensity: Float, wallpaperOpacity: Float, selectedWallpaperName: String,
    currentThemeIndex: Int, currentLanguage: String, onThemeChange: (Int) -> Unit, onLanguageChange: (String) -> Unit,
    onAmoledToggle: (Boolean) -> Unit, onLiveWallpaperTypeChange: (String) -> Unit, onBlurToggle: (Boolean) -> Unit,
    onBlurIntensityChange: (Float) -> Unit, onWallpaperOpacityChange: (Float) -> Unit, onPresetStaticSelected: (Int, String) -> Unit,
    onPresetVideoSelected: () -> Unit, onCustomUriSelected: (Uri, String) -> Unit, onReplayBoot: () -> Unit, playClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val losslessManager = remember { LosslessManager(context) }
    var expandedLang by rememberSaveable { mutableStateOf(false) }
    var expandedTheme by rememberSaveable { mutableStateOf(false) }
    var expandedWallType by rememberSaveable { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { onCustomUriSelected(it, "Custom Image") } }
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { onCustomUriSelected(it, "Custom Video") } }

    val losslessPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val success = losslessManager.importDll(it)
            viewModel.refreshDllStatus()
            Toast.makeText(context, if (success) "Lossless.dll importada com sucesso!" else "Falha na importação", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader(if (isEn) "Overlay & Integration" else "Overlay e Integração", theme)
        ConsoleCard("Side Menu / Gaming Overlay", "Barra lateral em tempo real sobre os jogos", theme) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ativar Sidebar", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.overlayEnabled, theme = theme, onCheckedChange = { viewModel.toggleOverlay(it); playClick() })
            }
        }

        ConsoleSectionHeader(if (isEn) "Lossless Scaling Engine" else "Motor Lossless Scaling", theme)
        ConsoleCard("Lossless.dll Integration", if (uiState.isDllImported) "✅ Ficheiro Integrado no Sistema" else "❌ Ficheiro Ausente", theme) {
            Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); losslessPickerLauncher.launch("*/*"); playClick() }, modifier = Modifier.padding(16.dp).fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                Text(if (uiState.isDllImported) "Substituir Lossless.dll..." else "Importar Lossless.dll da Steam...", color = Color.White, fontFamily = theme.fontFamily)
            }
        }

        ConsoleSectionHeader(if (isEn) "Language & Region" else "Idioma e Região", theme)
        ConsoleCard(if (isEn) "System Language" else "Idioma do Sistema", currentLanguage, theme, playClick = { expandedLang = true; playClick() }) {
            DropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }, modifier = Modifier.background(theme.surface)) {
                listOf("Português (PT-BR)", "English (US)").forEach { lang ->
                    DropdownMenuItem(text = { Text(lang, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onLanguageChange(lang); expandedLang = false; playClick() })
                }
            }
        }

        ConsoleSectionHeader(if (isEn) "UI Customization" else "Personalização UI", theme)
        ConsoleCard(if (isEn) "Console Theme" else "Tema do Console", AvailableThemes[currentThemeIndex].name, theme, playClick = { expandedTheme = true; playClick() }) {
            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }, modifier = Modifier.background(theme.surface)) {
                AvailableThemes.forEachIndexed { index, consoleTheme ->
                    DropdownMenuItem(text = { Text(consoleTheme.name, color = if (currentThemeIndex == index) theme.primary else theme.text, fontFamily = theme.fontFamily) }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onThemeChange(index); expandedTheme = false; playClick() })
                }
            }
        }
        ConsoleCard(if (isEn) "AMOLED Black" else "Preto AMOLED", if (isEn) "Absolute dark background" else "Fundo escuro absoluto (Adaptativo)", theme, playClick = playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEn) "Force AMOLED Black" else "Forçar Preto AMOLED", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = amoledBlack, theme = theme, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAmoledToggle(it); playClick() })
            }
        }

        ConsoleSectionHeader(if (isEn) "Live Wallpaper & Background" else "Live Wallpaper & Fundo", theme)
        ConsoleCard(if (isEn) "Wallpaper Type" else "Tipo de Wallpaper", liveWallpaperType, theme, playClick = { expandedWallType = true; playClick() }) {
            DropdownMenu(expanded = expandedWallType, onDismissRequest = { expandedWallType = false }, modifier = Modifier.background(theme.surface)) {
                listOf("Static", "Live (MP4)").forEach { type ->
                    DropdownMenuItem(text = { Text(type, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onLiveWallpaperTypeChange(type); expandedWallType = false; playClick() })
                }
            }
        }
        ConsoleCard(if (isEn) "Blur & Opacity Adjustments" else "Ajustes de Blur e Opacidade", if (isEn) "Control background visibility" else "Controlar visibilidade do fundo", theme, playClick = null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEn) "Enable Blur Effect" else "Habilitar Efeito de Desfoque", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = blurEnabled, theme = theme, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onBlurToggle(it); playClick() })
                }
                Text(if (isEn) "Blur Intensity" else "Intensidade do Desfoque", color = theme.text.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(value = blurIntensity, onValueChange = { onBlurIntensityChange(it) }, enabled = blurEnabled, valueRange = 0.0f..1.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Text(if (isEn) "Wallpaper Opacity" else "Opacidade do Wallpaper", color = theme.text.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Slider(value = wallpaperOpacity, onValueChange = { onWallpaperOpacityChange(it) }, valueRange = 0.0f..1.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }
        ConsoleCard(if (isEn) "Wallpaper Selection" else "Seleção de Wallpaper", selectedWallpaperName, theme, playClick = playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (liveWallpaperType == "Static") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPresetStaticSelected(R.drawable.static_wallpaper_1, "Preset 1"); playClick() }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) { Text("Wallpaper 1", color = Color.White, fontFamily = theme.fontFamily) }
                        Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPresetStaticSelected(R.drawable.static_wallpaper_2, "Preset 2"); playClick() }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) { Text("Wallpaper 2", color = Color.White, fontFamily = theme.fontFamily) }
                    }
                } else {
                    Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPresetVideoSelected(); playClick() }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) { Text("Live Wallpaper Padrão", color = Color.White, fontFamily = theme.fontFamily) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); if (liveWallpaperType == "Static") imagePickerLauncher.launch("image/*") else videoPickerLauncher.launch("video/*"); playClick() }, colors = ButtonDefaults.buttonColors(containerColor = theme.surface)) { Text("Escolher do Dispositivo...", color = theme.text, fontFamily = theme.fontFamily) }
            }
        }
        ConsoleSectionHeader("Sobre o Sistema", theme)
        ConsoleCard("Odin Hub", "Versão 0.5", theme, playClick = playClick) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Rever Tela de Boas-Vindas", color = theme.primary, fontFamily = theme.fontFamily, modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onReplayBoot() })
            }
        }
    }
}

@Composable
fun ConsoleSectionHeader(title: String, theme: ConsoleTheme) {
    Text(title.uppercase(), fontSize = 14.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = theme.text.copy(alpha = 0.5f), letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp).padding(top = 8.dp))
}

@Composable
fun ConsoleCard(title: String, subtitle: String, theme: ConsoleTheme, enabled: Boolean = true, playClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isFocused) { if (isFocused && enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    val scale by animateFloatAsState(targetValue = if (isFocused && enabled) 1.02f else 1.0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "cardScale")
    val glow by animateDpAsState(targetValue = if (isFocused && enabled) 16.dp else 0.dp, animationSpec = tween(200), label = "cardGlow")

    val glassSurface = theme.surface.copy(alpha = if (enabled) 0.4f else 0.1f)
    val subtleBorder = theme.text.copy(alpha = if (enabled) 0.15f else 0.05f)
    val borderColor by animateColorAsState(targetValue = if (isFocused && enabled) theme.primary else subtleBorder, label = "cardBorder")
    val contentAlpha by animateFloatAsState(targetValue = if (enabled) 1f else 0.3f, label = "contentAlpha")

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = glassSurface),
        modifier = Modifier.fillMaxWidth().scale(scale).shadow(glow, RoundedCornerShape(8.dp), spotColor = theme.primary, ambientColor = theme.primary).border(1.dp, borderColor, RoundedCornerShape(8.dp)).alpha(contentAlpha)
            .then(if (playClick != null && enabled) Modifier.focusable(interactionSource = interactionSource).clickable(interactionSource = interactionSource, indication = null) { playClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(title, fontSize = 16.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = if (isFocused && enabled) theme.primary else theme.text, modifier = Modifier.padding(horizontal = 16.dp))
            if (subtitle.isNotEmpty()) { Text(subtitle, fontSize = 12.sp, fontFamily = theme.fontFamily, color = theme.text.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) } else { Spacer(modifier = Modifier.height(8.dp)) }
            HorizontalDivider(color = theme.text.copy(alpha = 0.1f), thickness = 1.dp)
            Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f))) { content() }
        }
    }
}

@Composable
fun ConsoleToggle(checked: Boolean, theme: ConsoleTheme, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(targetValue = if (checked) 24.dp else 4.dp, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "toggleMove")
    val bgColor by animateColorAsState(targetValue = if (checked) theme.primary.copy(alpha = 0.3f) else theme.background, label = "toggleBg")
    val thumbColor by animateColorAsState(targetValue = if (checked) theme.primary else theme.text.copy(alpha = 0.5f), label = "toggleThumb")

    Box(
        modifier = Modifier.width(44.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).background(bgColor).border(1.dp, thumbColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.padding(start = thumbOffset).size(16.dp).clip(RoundedCornerShape(4.dp)).background(thumbColor))
    }
}

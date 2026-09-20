@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package de.langerhans.odintools.ui.screens

import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
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
import de.langerhans.odintools.ui.WelcomeScreen
import de.langerhans.odintools.ui.composables.*
import de.langerhans.odintools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: MainViewModel = hiltViewModel(), navigateToOverrideList: () -> Unit) {
    val uiState: MainUiModel by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isFirstRun by remember { mutableStateOf(true) }

    var showWelcomeSetup by remember { mutableStateOf(isFirstRun) }
    var showBootAnimation by remember { mutableStateOf(false) }

    var currentThemeIndex by remember { mutableIntStateOf(1) }
    var useAmoledBlack by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf("Português (PT-BR)") }
    val isEn = currentLanguage == "English (US)"

    // Correção: Usa o primeiro tema da lista como fallback seguro em vez de DefaultTheme
    val rawTheme = AvailableThemes.getOrElse(currentThemeIndex) { AvailableThemes[0] }
    val finalTheme = getResolvedTheme(rawTheme, useAmoledBlack)

    var bgmEnabled by remember { mutableStateOf(true) }
    var bgmVolume by remember { mutableFloatStateOf(0.3f) }
    var sfxEnabled by remember { mutableStateOf(true) }
    var sfxVolume by remember { mutableFloatStateOf(0.8f) }

    val context = LocalContext.current
    val defaultStaticRes = R.drawable.static_wallpaper_1
    val defaultVideoUri = remember { Uri.parse("android.resource://${context.packageName}/${R.raw.live_wallpaper}") }

    var liveWallpaperType by remember { mutableStateOf("Static") }
    var blurEnabled by remember { mutableStateOf(true) }
    var blurIntensity by remember { mutableFloatStateOf(0.4f) }
    var wallpaperOpacity by remember { mutableFloatStateOf(0.85f) }

    var selectedStaticRes by remember { mutableIntStateOf(defaultStaticRes) }
    var selectedCustomUri by remember { mutableStateOf<Uri?>(null) }
    var selectedWallpaperName by remember { mutableStateOf(if (isEn) "Preset 1" else "Predefinição 1") }

    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Helper seguro para tocar efeitos sonoros
    fun playSfx(resId: Int) {
        if (sfxEnabled) {
            runCatching {
                MediaPlayer.create(context, resId)?.apply {
                    setVolume(sfxVolume, sfxVolume)
                    setOnCompletionListener { release() }
                    start()
                }
            }
        }
    }

    val bgmPlayer = remember {
        runCatching { MediaPlayer.create(context, R.raw.bgm_1)?.apply { isLooping = true } }.getOrNull()
    }

    LaunchedEffect(bgmVolume) {
        bgmPlayer?.setVolume(bgmVolume, bgmVolume)
    }

    LaunchedEffect(bgmEnabled, showWelcomeSetup, showBootAnimation) {
        if (bgmPlayer != null) {
            if (bgmEnabled && !showWelcomeSetup && !showBootAnimation) {
                if (!bgmPlayer.isPlaying) bgmPlayer.start()
            } else {
                if (bgmPlayer.isPlaying) bgmPlayer.pause()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (bgmPlayer != null) {
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    if (bgmPlayer.isPlaying) bgmPlayer.pause()
                } else if (event == Lifecycle.Event.ON_RESUME) {
                    if (bgmEnabled && !showWelcomeSetup && !showBootAnimation) bgmPlayer.start()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { bgmPlayer?.release() }
    }

    if (uiState.showSaveProfileDialog) {
        var profileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveProfileDialog() },
            title = { Text(if (isEn) "Save Custom Profile" else "Salvar Perfil de TDP Personalizado", fontFamily = finalTheme.fontFamily) },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(if (isEn) "Profile Name" else "Nome do Perfil", fontFamily = finalTheme.fontFamily) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveCustomProfile(profileName); playSfx(R.raw.sfx_select) }) {
                    Text(if (isEn) "Save" else "Salvar", color = finalTheme.primary, fontFamily = finalTheme.fontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveProfileDialog() }) {
                    Text(if (isEn) "Cancel" else "Cancelar", color = finalTheme.text, fontFamily = finalTheme.fontFamily)
                }
            },
            containerColor = finalTheme.surface,
            titleContentColor = finalTheme.text,
            textContentColor = finalTheme.text
        )
    }

    if (showWelcomeSetup) {
        WelcomeScreen(
            theme = finalTheme,
            currentThemeIndex = currentThemeIndex,
            currentLanguage = currentLanguage,
            bgmEnabled = bgmEnabled,
            bgmVolume = bgmVolume,
            sfxEnabled = sfxEnabled,
            sfxVolume = sfxVolume,
            onThemeChange = { currentThemeIndex = it },
            onLanguageChange = { currentLanguage = it },
            onBgmToggle = { bgmEnabled = it },
            onBgmVolume = { bgmVolume = it },
            onSfxToggle = { sfxEnabled = it },
            onSfxVolume = { sfxVolume = it },
            onFinish = {
                showWelcomeSetup = false
                showBootAnimation = true
            }
        )
        return
    }

    if (showBootAnimation) {
        VideoBootScreen(
            theme = finalTheme,
            onVideoEnded = {
                showBootAnimation = false
                isFirstRun = false
                playSfx(R.raw.sfx_select)
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(finalTheme.background))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(wallpaperOpacity)
        ) {
            if (liveWallpaperType == "Live (MP4)") {
                val activeVideoUri = if (selectedCustomUri != null && selectedCustomUri.toString().startsWith("content://")) {
                    selectedCustomUri!!
                } else {
                    defaultVideoUri
                }
                LiveWallpaperRenderer(uri = activeVideoUri)

                if (blurEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.2f + (blurIntensity * 0.5f)),
                                        Color.Black.copy(alpha = 0.4f + (blurIntensity * 0.5f))
                                    )
                                )
                            )
                    )
                }
            } else {
                StaticWallpaperRenderer(
                    resId = if (selectedCustomUri == null) selectedStaticRes else null,
                    uri = if (selectedCustomUri != null) selectedCustomUri else null,
                    blurModifier = if (blurEnabled) Modifier.blur((blurIntensity * 40).dp) else Modifier
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            finalTheme.background.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playSfx(R.raw.sfx_nav)
                                selectedTab = (selectedTab + 1).coerceAtMost(3)
                                true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playSfx(R.raw.sfx_nav)
                                selectedTab = (selectedTab - 1).coerceAtLeast(0)
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Text(
                    text = "ODIN HUB",
                    fontSize = 28.sp,
                    fontFamily = finalTheme.fontFamily,
                    fontWeight = FontWeight.Black,
                    color = finalTheme.primary,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp)
                )
                ConsoleMenuBar(selectedTab = selectedTab, theme = finalTheme, isEn = isEn) {
                    if (selectedTab != it) { playSfx(R.raw.sfx_nav); selectedTab = it }
                }
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(tween(300))
                        } else {
                            slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(tween(300))
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                    label = "tab_animation"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> PerformancePanel(uiState, viewModel, finalTheme, isEn, navigateToOverrideList) { playSfx(R.raw.sfx_select) }
                        1 -> DisplayPanel(finalTheme, isEn) { playSfx(R.raw.sfx_select) }
                        2 -> ControlsPanel(uiState, viewModel, finalTheme, isEn) { playSfx(R.raw.sfx_select) }
                        3 -> SystemPanel(
                            theme = finalTheme, currentThemeIndex = currentThemeIndex, currentLanguage = currentLanguage, isEn = isEn,
                            amoledBlack = useAmoledBlack, bgmEnabled = bgmEnabled, bgmVolume = bgmVolume, sfxEnabled = sfxEnabled, sfxVolume = sfxVolume,
                            liveWallpaperType = liveWallpaperType, blurEnabled = blurEnabled, blurIntensity = blurIntensity, wallpaperOpacity = wallpaperOpacity,
                            selectedWallpaperName = selectedWallpaperName,
                            playClick = { playSfx(R.raw.sfx_select) }, onThemeChange = { currentThemeIndex = it }, onLanguageChange = { currentLanguage = it },
                            onAmoledToggle = { useAmoledBlack = it; playSfx(R.raw.sfx_select) },
                            onBgmToggle = { bgmEnabled = it; playSfx(R.raw.sfx_select) }, onBgmVolume = { bgmVolume = it },
                            onSfxToggle = { sfxEnabled = it; playSfx(R.raw.sfx_select) }, onSfxVolume = { sfxVolume = it },
                            onReplayBoot = { showWelcomeSetup = true; playSfx(R.raw.sfx_select) },
                            onLiveWallpaperTypeChange = { type ->
                                liveWallpaperType = type
                                if (type == "Live (MP4)") {
                                    selectedCustomUri = null
                                    selectedWallpaperName = if (isEn) "Default Live Wallpaper" else "Live Wallpaper Padrão"
                                } else {
                                    selectedCustomUri = null
                                    selectedStaticRes = R.drawable.static_wallpaper_1
                                    selectedWallpaperName = if (isEn) "Preset 1" else "Predefinição 1"
                                }
                            },
                            onBlurToggle = { blurEnabled = it; playSfx(R.raw.sfx_select) }, onBlurIntensityChange = { blurIntensity = it },
                            onWallpaperOpacityChange = { wallpaperOpacity = it },
                            onPresetStaticSelected = { resId, name ->
                                selectedCustomUri = null
                                selectedStaticRes = resId
                                selectedWallpaperName = name
                            },
                            onPresetVideoSelected = {
                                selectedCustomUri = null
                                selectedWallpaperName = if (isEn) "Default Live Wallpaper" else "Live Wallpaper Padrão"
                            },
                            onCustomUriSelected = { uri, name ->
                                selectedCustomUri = uri
                                selectedWallpaperName = name
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveWallpaperRenderer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
        }
    }
    LaunchedEffect(uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize()
    )
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = "Static Wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(blurModifier))
    }
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
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onVideoEnded()
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.matchParentSize()
        )
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick(index) }.padding(8.dp)
    ) {
        Icon(painterResource(iconResId), contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = color, fontSize = 12.sp, fontFamily = theme.fontFamily, fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold, letterSpacing = 1.sp)
        Box(modifier = Modifier.height(3.dp).width(24.dp).clip(RoundedCornerShape(50)).background(if (isSelected) theme.primary else Color.Transparent))
    }
}

@Composable
fun PerformancePanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, isEn: Boolean, navigateToOverrideList: () -> Unit, playClick: () -> Unit) {
    // Chave Mestra de Bloqueio (TDP ou CLOCK)
    var activeLimitMode by remember { mutableStateOf("TDP") }

    var expandedTdpProfile by remember { mutableStateOf(false) }
    var expandedClockProfile by remember { mutableStateOf(false) }
    var expandedFanProfile by remember { mutableStateOf(false) }

    var selectedClockProfileName by remember { mutableStateOf("Stock (Padrão AYN)") }
    var selectedFanProfileName by remember { mutableStateOf("Smart (Balanceado)") }

    var showClockSaveDialog by remember { mutableStateOf(false) }
    var customClockNameInput by remember { mutableStateOf("") }

    val fanProfiles = listOf("Silent (Silencioso)", "Smart (Balanceado)", "Sport (Desempenho Máximo)", "Stock (Padrão)")
    val clockPresets = listOf("Power Save (Underclock Seguro)", "Balanced (Intermediário)", "Triple A (Alto Desempenho)", "Stock (Padrão AYN)")
    val tdpPresets = listOf("Power Save (5W)", "Balanced (11W)", "Triple A (14.5W)", "Stock (Padrão AYN)")

    val allTdpProfiles = tdpPresets + uiState.savedCustomProfiles
    var savedCustomClockProfiles by remember { mutableStateOf(listOf<String>()) }

    val isTdpMode = activeLimitMode == "TDP"
    val isClockMode = activeLimitMode == "CLOCK"

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // --- 1. MOTOR E KSU ---
        ConsoleSectionHeader(if (isEn) "Engine & Optimization" else "Motor e Otimização", theme)
        ConsoleCard(if (isEn) "KSU Module Integration" else "Módulo KSU", if (isEn) "Toggle if Odin Hub KSU module is installed" else "Ative se instalou o Módulo KSU (Remove overhead)", theme, playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (uiState.useRootTarget) "Módulo KSU (Sem Overhead)" else "Modo Pulse (Loop PServer)", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.useRootTarget, theme = theme, onCheckedChange = { viewModel.updateUseRoot(it); playClick() })
            }
        }

        // --- 2. CONTROLE DE VENTOINHA (FAN) ---
        ConsoleSectionHeader(if (isEn) "Cooling & Fan Control" else "Controle de Ventoinha (Cooler)", theme)
        ConsoleCard(if (isEn) "Fan Speed Profiles" else "Perfis de Ventoinha", selectedFanProfileName, theme, { expandedFanProfile = true; playClick() }) {
            DropdownMenu(expanded = expandedFanProfile, onDismissRequest = { expandedFanProfile = false }, modifier = Modifier.background(theme.surface)) {
                fanProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile, color = theme.text, fontFamily = theme.fontFamily) },
                        onClick = {
                            selectedFanProfileName = profile
                            expandedFanProfile = false
                            playClick()
                        }
                    )
                }
            }
        }

        // --- SELETOR DE MODO DE LIMITAÇÃO ---
        ConsoleSectionHeader(if (isEn) "Hardware Limitation Mode" else "Modo de Limitação de Hardware", theme)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, theme.text.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isTdpMode) theme.primary.copy(alpha = 0.8f) else Color.Transparent)
                    .clickable {
                        activeLimitMode = "TDP"
                        // Reset de Segurança: Volta os clocks para o padrão máximo da máquina
                        viewModel.updateManualClocks(3530f, 4320f, 1100f)
                        playClick()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isEn) "Lock by TDP" else "Limitar por TDP", color = if (isTdpMode) Color.White else theme.text.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontFamily = theme.fontFamily)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isClockMode) theme.primary.copy(alpha = 0.8f) else Color.Transparent)
                    .clickable {
                        activeLimitMode = "CLOCK"
                        // Reset de Segurança: Desativa o limite de TDP
                        viewModel.updatePerformanceProfile("Stock")
                        playClick()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isEn) "Lock by Clocks" else "Limitar por Clocks", color = if (isClockMode) Color.White else theme.text.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontFamily = theme.fontFamily)
            }
        }

        // --- 3. AUTOTDP E PERFIS DE TDP ---
        ConsoleCard(
            title = if (isEn) "Dynamic AutoTDP Control" else "Controle Dinâmico AutoTDP",
            subtitle = if (isEn) "Monitors FPS and automatically trims TDP. Disabled when Clock Mode is active." else "Monitora o FPS e ajusta o TDP dinamicamente. Fica desativado se o Modo Clock estiver ativo.",
            theme = theme,
            playClick = { if (isTdpMode) playClick() },
            enabled = isTdpMode
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(if (isEn) "Limit: ${uiState.tdpValue.toInt()} W" else "Limite de TDP: ${uiState.tdpValue.toInt()} W", color = theme.text, fontFamily = theme.fontFamily)
                Slider(
                    value = uiState.tdpValue,
                    onValueChange = { viewModel.updateTdp(it) },
                    valueRange = 5f..25f,
                    steps = 20,
                    enabled = isTdpMode,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                )
            }
        }

        ConsoleCard(
            title = if (isEn) "TDP Preset Selection" else "Seleção de Perfil de TDP",
            subtitle = uiState.performanceProfile,
            theme = theme,
            playClick = { if (isTdpMode) { expandedTdpProfile = true; playClick() } },
            enabled = isTdpMode
        ) {
            DropdownMenu(expanded = expandedTdpProfile && isTdpMode, onDismissRequest = { expandedTdpProfile = false }, modifier = Modifier.background(theme.surface)) {
                allTdpProfiles.distinct().forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile, color = theme.text, fontFamily = theme.fontFamily) },
                        onClick = {
                            viewModel.updatePerformanceProfile(profile)
                            if (profile.contains("5W")) viewModel.updateTdp(5f)
                            else if (profile.contains("11W")) viewModel.updateTdp(11f)
                            else if (profile.contains("14.5W")) viewModel.updateTdp(14.5f)
                            expandedTdpProfile = false
                            playClick()
                        }
                    )
                }
            }

            Button(
                onClick = { if (isTdpMode) { viewModel.showSaveProfileDialog(); playClick() } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                enabled = isTdpMode,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
            ) {
                Text(if (isEn) "Save Current TDP as Custom Profile..." else "Salvar TDP Atual como Perfil Personalizado...", fontFamily = theme.fontFamily, color = Color.White)
            }
        }

        // --- 4. TRAVAMENTO MANUAL DE CLOCKS E PERFIS DE UNDERCLOCK ---
        ConsoleCard(
            title = if (isEn) "Clock Profile Presets" else "Perfis de Frequência / Underclock",
            subtitle = selectedClockProfileName,
            theme = theme,
            playClick = { if (isClockMode) { expandedClockProfile = true; playClick() } },
            enabled = isClockMode
        ) {
            DropdownMenu(expanded = expandedClockProfile && isClockMode, onDismissRequest = { expandedClockProfile = false }, modifier = Modifier.background(theme.surface)) {
                (clockPresets + savedCustomClockProfiles).distinct().forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile, color = theme.text, fontFamily = theme.fontFamily) },
                        onClick = {
                            selectedClockProfileName = profile
                            if (profile.contains("Power Save")) {
                                viewModel.updateManualClocks(2000f, 2400f, 500f)
                            } else if (profile.contains("Balanced")) {
                                viewModel.updateManualClocks(2800f, 3400f, 750f)
                            } else if (profile.contains("Triple A")) {
                                viewModel.updateManualClocks(3530f, 4320f, 1100f)
                            }
                            expandedClockProfile = false
                            playClick()
                        }
                    )
                }
            }

            Button(
                onClick = { if (isClockMode) { showClockSaveDialog = true; playClick() } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                enabled = isClockMode,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
            ) {
                Text(if (isEn) "Save Current Clocks as Custom Profile..." else "Salvar Clocks Atuais como Perfil...", fontFamily = theme.fontFamily, color = Color.White)
            }
        }

        ConsoleCard(
            title = if (isEn) "Discrete Manual Clocks Slider" else "Sliding de Frequência por Cluster",
            subtitle = if (isEn) "Individual precise steps per architecture" else "Passos discretos otimizados por arquitetura",
            theme = theme,
            playClick = { if (isClockMode) playClick() },
            enabled = isClockMode
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(if (isEn) "Perf Cores (6x): ${uiState.cpuPerfClock.toInt()} MHz" else "Núcleos de Performance (6x): ${uiState.cpuPerfClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(
                    value = uiState.cpuPerfClock,
                    onValueChange = { viewModel.updateManualClocks(it, uiState.cpuPrimeClock, uiState.gpuClock) },
                    valueRange = 1735f..3530f,
                    steps = 15,
                    enabled = isClockMode,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(if (isEn) "Prime Cores (2x): ${uiState.cpuPrimeClock.toInt()} MHz" else "Núcleos Prime (2x): ${uiState.cpuPrimeClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(
                    value = uiState.cpuPrimeClock,
                    onValueChange = { viewModel.updateManualClocks(uiState.cpuPerfClock, it, uiState.gpuClock) },
                    valueRange = 2246f..4320f,
                    steps = 15,
                    enabled = isClockMode,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Adreno GPU: ${uiState.gpuClock.toInt()} MHz", color = theme.text, fontFamily = theme.fontFamily)
                Slider(
                    value = uiState.gpuClock,
                    onValueChange = { viewModel.updateManualClocks(uiState.cpuPerfClock, uiState.cpuPrimeClock, it) },
                    valueRange = 160f..1100f,
                    steps = 18,
                    enabled = isClockMode,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                )
            }
        }

        // --- 5. OVERRIDES POR JOGO ---
        ConsoleSectionHeader(if (isEn) "Game Rules & Per-App Overrides" else "Regras por Jogo e Aplicativo", theme)
        ConsoleCard(if (isEn) "Per-App Overrides" else "Overrides por Jogo", if (isEn) "Configure specific TDP & clock rules for emulators" else "Vincule perfis de TDP, Clocks e Fan a emuladores específicos", theme, playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEn) "Enable Overrides" else "Habilitar Overrides por App", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.appOverridesEnabled, theme = theme, onCheckedChange = { viewModel.appOverridesEnabled(it); playClick() })
            }
            TriggerPreference(icon = R.drawable.ic_app_settings, title = R.string.appOverrides, description = R.string.appOverridesDescription) { playClick(); navigateToOverrideList() }
        }
    }

    if (showClockSaveDialog) {
        AlertDialog(
            onDismissRequest = { showClockSaveDialog = false },
            title = { Text(if (isEn) "Save Custom Clock Profile" else "Salvar Perfil de Clock Personalizado", fontFamily = theme.fontFamily) },
            text = {
                OutlinedTextField(
                    value = customClockNameInput,
                    onValueChange = { customClockNameInput = it },
                    label = { Text(if (isEn) "Profile Name (e.g. PS2 Heavy)" else "Nome do Perfil (Ex: PS2 Pesado)", fontFamily = theme.fontFamily) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customClockNameInput.isNotBlank()) {
                        savedCustomClockProfiles = savedCustomClockProfiles + customClockNameInput
                        selectedClockProfileName = customClockNameInput
                        customClockNameInput = ""
                        showClockSaveDialog = false
                        playClick()
                    }
                }) {
                    Text(if (isEn) "Save" else "Salvar", color = theme.primary, fontFamily = theme.fontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClockSaveDialog = false }) {
                    Text(if (isEn) "Cancel" else "Cancelar", color = theme.text, fontFamily = theme.fontFamily)
                }
            },
            containerColor = theme.surface,
            titleContentColor = theme.text,
            textContentColor = theme.text
        )
    }
}

@Composable
fun DisplayPanel(theme: ConsoleTheme, isEn: Boolean, playClick: () -> Unit) {
    var satValue by remember { mutableFloatStateOf(1.0f) }
    var tempValue by remember { mutableFloatStateOf(6500f) }
    var expandedProfile by remember { mutableStateOf(false) }
    val profiles = if (isEn) listOf("Native", "Vibrant", "Cinema", "Retro") else listOf("Nativo", "Vibrante", "Cinema", "Retrô")
    var selectedProfile by remember { mutableStateOf(profiles[0]) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader(if (isEn) "Screen Calibration" else "Calibração de Tela", theme)
        ConsoleCard(if (isEn) "Global Image Profiles" else "Perfis de Imagem Global", selectedProfile, theme, { expandedProfile = true; playClick() }) {
            DropdownMenu(expanded = expandedProfile, onDismissRequest = { expandedProfile = false }, modifier = Modifier.background(theme.surface)) {
                profiles.forEach { profile -> DropdownMenuItem(text = { Text(profile, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { selectedProfile = profile; expandedProfile = false; playClick() }) }
            }
        }
        ConsoleCard(if (isEn) "Manual Adjustments" else "Ajustes Manuais", if (isEn) "Saturation & Temperature" else "Saturação e Temperatura", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text((if (isEn) "Saturation: " else "Saturação: ") + "${"%.1f".format(satValue)}", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = satValue, onValueChange = { satValue = it }, valueRange = 0.0f..2.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Spacer(modifier = Modifier.height(8.dp))
                Text((if (isEn) "Temperature: " else "Temperatura: ") + "${tempValue.toInt()}K", color = theme.text, fontFamily = theme.fontFamily)
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 4000f..9000f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }
    }
}

@Composable
fun ControlsPanel(uiState: MainUiModel, viewModel: MainViewModel, theme: ConsoleTheme, isEn: Boolean, playClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader(if (isEn) "Mapping & Shortcuts" else "Mapeamento e Atalhos", theme)
        ConsoleCard(if (isEn) "System Buttons" else "Botões de Sistema", if (isEn) "General behavior" else "Comportamento geral", theme, playClick) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEn) "Single Press Home" else "Toque Único no Home", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = uiState.singlePressHomeEnabled, theme = theme, onCheckedChange = { viewModel.updateSinglePressHomePreference(it); playClick() })
            }
        }
        ConsoleCard(if (isEn) "Back Buttons (Macro)" else "Botões Traseiros (Macro)", if (isEn) "Map M1 & M2" else "Mapear M1 e M2", theme, playClick) {
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m1Button, description = R.string.remapButtonDescription) { playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M1_VALUE) }
            TriggerPreference(icon = R.drawable.ic_gamepad, title = R.string.m2Button, description = R.string.remapButtonDescription) { playClick(); viewModel.remapButtonClicked(SettingsRepo.KEY_CUSTOM_M2_VALUE) }
        }
    }
}

@Composable
fun SystemPanel(
    theme: ConsoleTheme, currentThemeIndex: Int, currentLanguage: String, amoledBlack: Boolean, isEn: Boolean,
    bgmEnabled: Boolean, bgmVolume: Float, sfxEnabled: Boolean, sfxVolume: Float,
    liveWallpaperType: String, blurEnabled: Boolean, blurIntensity: Float, wallpaperOpacity: Float, selectedWallpaperName: String,
    playClick: () -> Unit, onThemeChange: (Int) -> Unit, onLanguageChange: (String) -> Unit, onAmoledToggle: (Boolean) -> Unit,
    onBgmToggle: (Boolean) -> Unit, onBgmVolume: (Float) -> Unit, onSfxToggle: (Boolean) -> Unit, onSfxVolume: (Float) -> Unit,
    onReplayBoot: () -> Unit, onLiveWallpaperTypeChange: (String) -> Unit, onBlurToggle: (Boolean) -> Unit,
    onBlurIntensityChange: (Float) -> Unit, onWallpaperOpacityChange: (Float) -> Unit,
    onPresetStaticSelected: (Int, String) -> Unit, onPresetVideoSelected: () -> Unit, onCustomUriSelected: (Uri, String) -> Unit
) {
    var expandedLang by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf(false) }
    var expandedWallType by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onCustomUriSelected(it, if (isEn) "Custom Image" else "Imagem Personalizada") }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onCustomUriSelected(it, if (isEn) "Custom Video (.MP4)" else "Vídeo Personalizado (.MP4)") }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsoleSectionHeader(if (isEn) "Language & Region" else "Idioma e Região", theme)
        ConsoleCard(if (isEn) "System Language" else "Idioma do Sistema", currentLanguage, theme, { expandedLang = true; playClick() }) {
            DropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }, modifier = Modifier.background(theme.surface)) {
                listOf("Português (PT-BR)", "English (US)").forEach { lang ->
                    DropdownMenuItem(text = { Text(lang, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { onLanguageChange(lang); expandedLang = false; playClick() })
                }
            }
        }
        ConsoleSectionHeader(if (isEn) "UI Customization" else "Personalização UI", theme)
        ConsoleCard(if (isEn) "Console Theme" else "Tema do Console", AvailableThemes[currentThemeIndex].name, theme, { expandedTheme = true; playClick() }) {
            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }, modifier = Modifier.background(theme.surface)) {
                AvailableThemes.forEachIndexed { index, consoleTheme ->
                    DropdownMenuItem(text = { Text(consoleTheme.name, color = if (currentThemeIndex == index) theme.primary else theme.text, fontFamily = theme.fontFamily) }, onClick = { onThemeChange(index); expandedTheme = false; playClick() })
                }
            }
        }
        ConsoleCard(if (isEn) "AMOLED Black" else "Preto AMOLED", if (isEn) "Absolute dark background" else "Fundo escuro absoluto (Adaptativo)", theme, { onAmoledToggle(!amoledBlack); playClick() }) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEn) "Force AMOLED Black" else "Forçar Preto AMOLED", color = theme.text, fontFamily = theme.fontFamily)
                ConsoleToggle(checked = amoledBlack, theme = theme, onCheckedChange = { onAmoledToggle(it); playClick() })
            }
        }
        ConsoleSectionHeader(if (isEn) "Audio Mixer" else "Mixer de Áudio", theme)
        ConsoleCard(if (isEn) "Background Music (BGM)" else "Música de Fundo (BGM)", "Volume: ${(bgmVolume * 100).toInt()}%", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEn) "Enable BGM" else "Habilitar BGM", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = bgmEnabled, theme = theme, onCheckedChange = { onBgmToggle(it); playClick() })
                }
                Slider(value = bgmVolume, onValueChange = { onBgmVolume(it) }, enabled = bgmEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }
        ConsoleCard(if (isEn) "Sound Effects (SFX)" else "Efeitos Sonoros (SFX)", "Volume: ${(sfxVolume * 100).toInt()}%", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEn) "Enable SFX" else "Habilitar SFX", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = sfxEnabled, theme = theme, onCheckedChange = { onSfxToggle(it); playClick() })
                }
                Slider(value = sfxVolume, onValueChange = { onSfxVolume(it) }, enabled = sfxEnabled, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }
        ConsoleSectionHeader(if (isEn) "Live Wallpaper & Background" else "Live Wallpaper & Fundo", theme)
        ConsoleCard(if (isEn) "Wallpaper Type" else "Tipo de Wallpaper", liveWallpaperType, theme, { expandedWallType = true; playClick() }) {
            DropdownMenu(expanded = expandedWallType, onDismissRequest = { expandedWallType = false }, modifier = Modifier.background(theme.surface)) {
                listOf("Static", "Live (MP4)").forEach { type ->
                    DropdownMenuItem(text = { Text(type, color = theme.text, fontFamily = theme.fontFamily) }, onClick = { onLiveWallpaperTypeChange(type); expandedWallType = false; playClick() })
                }
            }
        }
        ConsoleCard(if (isEn) "Blur & Opacity Adjustments" else "Ajustes de Blur e Opacidade", if (isEn) "Control background visibility" else "Controlar visibilidade do fundo", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEn) "Enable Blur Effect" else "Habilitar Efeito de Desfoque", color = theme.text, fontFamily = theme.fontFamily)
                    ConsoleToggle(checked = blurEnabled, theme = theme, onCheckedChange = { onBlurToggle(it); playClick() })
                }
                Text(if (isEn) "Blur Intensity" else "Intensidade do Desfoque", color = theme.text.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(value = blurIntensity, onValueChange = { onBlurIntensityChange(it) }, enabled = blurEnabled, valueRange = 0.0f..1.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                Text(if (isEn) "Wallpaper Opacity" else "Opacidade do Wallpaper", color = theme.text.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Slider(value = wallpaperOpacity, onValueChange = { onWallpaperOpacityChange(it) }, valueRange = 0.0f..1.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
            }
        }
        ConsoleCard(if (isEn) "Wallpaper Selection" else "Seleção de Wallpaper", selectedWallpaperName, theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (liveWallpaperType == "Static") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onPresetStaticSelected(R.drawable.static_wallpaper_1, if (isEn) "Preset 1" else "Predefinição 1")
                            playClick()
                        }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                            Text(if (isEn) "Wallpaper 1" else "Wallpaper 1", fontFamily = theme.fontFamily, color = Color.White)
                        }
                        Button(onClick = {
                            onPresetStaticSelected(R.drawable.static_wallpaper_2, if (isEn) "Preset 2" else "Predefinição 2")
                            playClick()
                        }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                            Text(if (isEn) "Wallpaper 2" else "Wallpaper 2", fontFamily = theme.fontFamily, color = Color.White)
                        }
                    }
                } else {
                    Button(onClick = {
                        onPresetVideoSelected()
                        playClick()
                    }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                        Text(if (isEn) "Default Live Wallpaper" else "Live Wallpaper Padrão", fontFamily = theme.fontFamily, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = {
                    if (liveWallpaperType == "Static") imagePickerLauncher.launch("image/*") else videoPickerLauncher.launch("video/*")
                    playClick()
                }, colors = ButtonDefaults.buttonColors(containerColor = theme.surface)) {
                    Text(if (isEn) "Choose from Device..." else "Escolher do Dispositivo...", fontFamily = theme.fontFamily, color = theme.text)
                }
            }
        }
        ConsoleSectionHeader(if (isEn) "About System" else "Sobre o Sistema", theme)
        ConsoleCard("Odin Hub", "Versão 0.5", theme, playClick) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(if (isEn) "Replay Welcome Screen" else "Rever Tela de Boas-Vindas", color = theme.primary, fontFamily = theme.fontFamily, modifier = Modifier.clickable { onReplayBoot() })
            }
        }
    }
}

@Composable
fun ConsoleSectionHeader(title: String, theme: ConsoleTheme) {
    Text(title.uppercase(), fontSize = 14.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = theme.text.copy(alpha = 0.5f), letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp).padding(top = 8.dp))
}

@Composable
fun ConsoleCard(title: String, subtitle: String, theme: ConsoleTheme, playClick: () -> Unit = {}, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isFocused) {
        if (isFocused && enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val scale by animateFloatAsState(targetValue = if (isFocused && enabled) 1.02f else 1.0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "cardScale")
    val glow by animateDpAsState(targetValue = if (isFocused && enabled) 16.dp else 0.dp, animationSpec = tween(200), label = "cardGlow")

    val glassSurface = theme.surface.copy(alpha = if (enabled) 0.4f else 0.1f)
    val subtleBorder = theme.text.copy(alpha = if (enabled) 0.15f else 0.05f)
    val borderColor by animateColorAsState(targetValue = if (isFocused && enabled) theme.primary else subtleBorder, label = "cardBorder")
    val contentAlpha by animateFloatAsState(targetValue = if (enabled) 1f else 0.3f, label = "contentAlpha")

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = glassSurface),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(glow, RoundedCornerShape(8.dp), spotColor = theme.primary, ambientColor = theme.primary)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .alpha(contentAlpha)
            .then(
                if (enabled) {
                    Modifier
                        .focusable(interactionSource = interactionSource)
                        .clickable(interactionSource = interactionSource, indication = null) { playClick() }
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(title, fontSize = 16.sp, fontFamily = theme.fontFamily, fontWeight = FontWeight.Bold, color = if (isFocused && enabled) theme.primary else theme.text, modifier = Modifier.padding(horizontal = 16.dp))
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 12.sp, fontFamily = theme.fontFamily, color = theme.text.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            HorizontalDivider(color = theme.text.copy(alpha = 0.1f), thickness = 1.dp)
            Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f))) {
                content()
            }
        }
    }
}

@Composable
fun ConsoleToggle(checked: Boolean, theme: ConsoleTheme, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(targetValue = if (checked) 24.dp else 4.dp, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "toggleMove")
    val bgColor by animateColorAsState(targetValue = if (checked) theme.primary.copy(alpha = 0.3f) else theme.background, label = "toggleBg")
    val thumbColor by animateColorAsState(targetValue = if (checked) theme.primary else theme.text.copy(alpha = 0.5f), label = "toggleThumb")

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, thumbColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.padding(start = thumbOffset).size(16.dp).clip(RoundedCornerShape(4.dp)).background(thumbColor))
    }
}

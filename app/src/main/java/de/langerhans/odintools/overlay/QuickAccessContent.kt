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
import androidx.compose.runtime.rememberCoroutineScope
import de.langerhans.odintools.data.AppOverrideEntity
import de.langerhans.odintools.data.AppOverrideRepository
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.FanMode
import de.langerhans.odintools.models.ReshadeProfiles
import de.langerhans.odintools.tools.hardware.GraphicsLayerManager
import de.langerhans.odintools.tools.hardware.PerformanceManager
import de.langerhans.odintools.ui.theme.ConsoleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun QuickAccessContent(
    isExpanded: Boolean,
    theme: ConsoleTheme,
    prefs: SharedPrefsRepo,
    isDllReady: Boolean,
    performanceManager: PerformanceManager,
    overrideRepository: AppOverrideRepository,
    graphicsLayerManager: GraphicsLayerManager,
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
                    performanceManager = performanceManager,
                    overrideRepository = overrideRepository,
                    graphicsLayerManager = graphicsLayerManager,
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
    performanceManager: PerformanceManager,
    overrideRepository: AppOverrideRepository,
    graphicsLayerManager: GraphicsLayerManager,
    panelOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    // CAUSA RAIZ (auditoria): esta função criava a sua PRÓPRIA instância de PerformanceManager
    // (`remember { PerformanceManager(ShellExecutor()) }`), completamente à parte do singleton
    // gerido pelo Hilt que o MainViewModel/ForegroundAppWatcherService/OdinHubService usam.
    // Como PerformanceManager arranca um daemon em segundo plano que reescreve os clocks a cada
    // 1s a partir do seu PRÓPRIO estado interno, isto criava DOIS daemons independentes a
    // competir pelos mesmos nós de sysfs: sempre que o utilizador ajustava o TDP/clocks no
    // overlay (o painel que se usa a meio do jogo — o caso de uso mais crítico), a escrita
    // "colava" por um instante e depois era imediatamente sobrescrita pelo outro daemon (o
    // singleton "oficial", com o seu próprio estado desatualizado) no tick seguinte. Isto
    // explica exatamente o sintoma "os valores mudam mas não fixam de forma fiável". A correção
    // é receber o singleton injetado (ver GamingOverlayService/QuickAccessOverlay) em vez de
    // instanciar um novo.
    val currentApp = prefs.currentForegroundApp
    val scope = rememberCoroutineScope()

    // CAUSA RAIZ do lag/travamento ao arrastar o slider de TDP ou trocar de preset de clock
    // rapidamente: `performanceManager.applyDynamicTdp`/`applyAbsoluteClocks` fazem um `exec`
    // root SÍNCRONO (bloqueante) por chamada. Um `Slider.onValueChange` dispara dezenas de vezes
    // por segundo durante o arrasto -- cada uma bloqueando a thread de UI do Compose até o `su`
    // terminar, daí o "trava mesmo". `applyTdpLive`/`applyClocksLive` abaixo movem essa escrita
    // para uma coroutine em Dispatchers.IO E cancelam o job anterior antes de lançar o novo, para
    // não empilhar dezenas de `setprop`/escritas de sysfs concorrentes na fila do root enquanto o
    // dedo ainda está a arrastar -- só a escrita mais recente chega a correr.
    var tdpJob by remember { mutableStateOf<Job?>(null) }
    var clockJob by remember { mutableStateOf<Job?>(null) }
    fun applyTdpLive(watts: Float) {
        tdpJob?.cancel()
        tdpJob = scope.launch(Dispatchers.IO) { performanceManager.applyDynamicTdp(watts) }
    }
    fun applyClocksLive(perfMHz: Float, primeMHz: Float, gpuMHz: Float) {
        clockJob?.cancel()
        clockJob = scope.launch(Dispatchers.IO) {
            performanceManager.applyAbsoluteClocks((perfMHz * 1000).toLong(), (primeMHz * 1000).toLong(), (gpuMHz * 1_000_000).toLong())
        }
    }

    // CAUSA RAIZ (auditoria): tudo aqui era lido/escrito só no SharedPrefs (`prefs.getPerApp*`
    // / `prefs.savePerAppConfig`), um armazenamento TOTALMENTE separado da tabela Room que a
    // aba Performance -> Per-App Overrides usa. Configurar um jogo ali não tinha nenhum efeito
    // no overlay (e vice-versa) porque eram dois "bancos de dados" que nunca se falavam. Agora
    // ambos leem/escrevem o mesmo AppOverrideRepository (Room). `remember(currentApp)` garante
    // que, se o overlay ficar aberto e o utilizador trocar de jogo, o estado é recarregado para
    // o jogo novo em vez de continuar a mostrar valores do jogo anterior.
    val existingOverride = remember(currentApp) { overrideRepository.get(currentApp) }

    var selectedTab by remember { mutableIntStateOf(0) }

    var reshadeProfile by remember(currentApp) { mutableStateOf(existingOverride?.reshadeProfile ?: prefs.reshadeProfile) }

    var sgsrEnabled by remember(currentApp) { mutableStateOf(existingOverride?.sgsrEnabled ?: prefs.globalSgsrEnabled) }
    var sgsrMode by remember(currentApp) { mutableStateOf(existingOverride?.sgsrMode ?: prefs.sgsrMode) }
    var sgsrSharpness by remember(currentApp) { mutableFloatStateOf(existingOverride?.sgsrSharpness ?: prefs.sgsrSharpness) }

    var lsfgEnabled by remember(currentApp) { mutableStateOf(existingOverride?.lsfgEnabled ?: prefs.globalLsfgEnabled) }
    var lsfgMultiplier by remember(currentApp) { mutableStateOf(existingOverride?.lsfgMultiplier?.let { "${it}x" } ?: prefs.lsfgMultiplier) }
    var lsfgPacing by remember(currentApp) { mutableStateOf(existingOverride?.lsfgFramePacing ?: prefs.lsfgFramePacing) }
    var lsfgPerfMode by remember(currentApp) { mutableStateOf(existingOverride?.lsfgPerformanceMode ?: prefs.lsfgPerformanceMode) }

    // Antes, isto arrancava sempre fixo em "TDP", independentemente do modo realmente ativo
    // (global ou por jogo) -- por isso bastava abrir o overlay e tocar num preset/slider da
    // aba errada para reativar silenciosamente o TDP mesmo com o utilizador a usar Clocks.
    // Agora carrega o modo que está de facto persistido para este jogo (ou o global, se o
    // jogo não tiver override próprio), exatamente como o ecrã de Settings.
    var activeLimitMode by remember(currentApp) { mutableStateOf(existingOverride?.limitMode ?: prefs.activeLimitMode) }
    var fanMode by remember(currentApp) { mutableIntStateOf(existingOverride?.fanSettingsValue ?: prefs.fanMode) }

    var tdpValue by remember(currentApp) { mutableFloatStateOf(existingOverride?.tdpWatts ?: prefs.tdpValue) }
    var cpuPerfClock by remember(currentApp) { mutableFloatStateOf(existingOverride?.perfClockKHz?.let { it / 1000f } ?: prefs.cpuPerfClock) }
    var cpuPrimeClock by remember(currentApp) { mutableFloatStateOf(existingOverride?.primeClockKHz?.let { it / 1000f } ?: prefs.cpuPrimeClock) }
    var gpuClock by remember(currentApp) { mutableFloatStateOf(existingOverride?.gpuClockHz?.let { it / 1_000_000f } ?: prefs.gpuClock) }

    var savedPresetName by remember { mutableStateOf("") }

    // Grava o estado atual do painel como a regra deste jogo. Chamado a cada alteração
    // "definitiva" (toque num preset, soltar um slider, mudar de modo) -- não a cada tick de
    // arrasto de slider, para não martelar o Room -- e também no onDispose, como rede de
    // segurança para quando o painel fecha de forma inesperada (troca de app, serviço morto),
    // que era o único momento em que o código anterior gravava alguma coisa.
    fun persistOverride() {
        val entity = AppOverrideEntity(
            packageName = currentApp,
            limitMode = activeLimitMode,
            tdpWatts = tdpValue,
            perfClockKHz = (cpuPerfClock * 1000).toLong(),
            primeClockKHz = (cpuPrimeClock * 1000).toLong(),
            gpuClockHz = (gpuClock * 1_000_000).toLong(),
            fanSettingsValue = fanMode,
            tdpProfile = existingOverride?.tdpProfile,
            clockProfile = existingOverride?.clockProfile,
            fanProfile = existingOverride?.fanProfile,
            lsfgEnabled = lsfgEnabled,
            lsfgMultiplier = lsfgMultiplier.replace("x", "").toIntOrNull() ?: 2,
            lsfgPerformanceMode = lsfgPerfMode,
            lsfgFramePacing = lsfgPacing,
            // O overlay não expõe um slider de qualidade LSFG (só a aba Performance ->
            // Per-App Overrides tem); preserva o que já estava gravado em vez de o resetar
            // para o valor por omissão a cada escrita feita a partir daqui.
            lsfgQuality = existingOverride?.lsfgQuality ?: 1.0f,
            sgsrEnabled = sgsrEnabled,
            sgsrMode = sgsrMode,
            sgsrSharpness = sgsrSharpness,
            reshadeProfile = reshadeProfile,
            // Idem: sem sliders de saturação/temperatura no overlay, preserva o que a aba
            // Performance -> Per-App Overrides já tinha configurado para este jogo.
            saturationOverride = existingOverride?.saturationOverride ?: prefs.saturationOverride,
            temperatureOverride = existingOverride?.temperatureOverride ?: prefs.temperatureOverride,
        )
        scope.launch { overrideRepository.upsert(entity) }
    }

    DisposableEffect(currentApp) {
        onDispose { persistOverride() }
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
                // Antes: lista fixa com nomes que nem batiam certo com os effectId reais do
                // shader (ver ReshadeProfiles.kt). Agora usa a lista curada -- os ~20 efeitos
                // que já existem em window_postfx.frag, mas só os 10 que fazem sentido expor.
                val allProfiles = ReshadeProfiles.labels
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rowProfiles in allProfiles.chunked(2)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (profile in rowProfiles) {
                                val isSelected = reshadeProfile == profile
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSelected) theme.primary.copy(alpha = 0.8f) else theme.surface.copy(alpha = 0.5f)).border(1.dp, if (isSelected) theme.primary else theme.text.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable { reshadeProfile = profile; scope.launch(Dispatchers.IO) { graphicsLayerManager.applyReshade(profile, prefs.saturationOverride, prefs.temperatureOverride) }; persistOverride() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
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
                    Switch(checked = sgsrEnabled, onCheckedChange = { sgsrEnabled = it; scope.launch(Dispatchers.IO) { graphicsLayerManager.applySgsr(it, sgsrMode) }; persistOverride() }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Modo SGSR", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Quality", "Balanced", "Performance", "Ultra").forEach { mode ->
                        val isSel = sgsrMode == mode
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.5f)).clickable { sgsrMode = mode; scope.launch(Dispatchers.IO) { graphicsLayerManager.applySgsr(sgsrEnabled, mode) }; persistOverride() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(mode, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nitidez (Sharpness): ${"%.2f".format(sgsrSharpness)}", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                Slider(value = sgsrSharpness, onValueChange = { sgsrSharpness = it }, onValueChangeFinished = { scope.launch(Dispatchers.IO) { graphicsLayerManager.applySgsrSharpness(sgsrSharpness) }; persistOverride() }, valueRange = 0.0f..1.0f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))

                Spacer(modifier = Modifier.height(16.dp))
                Text("LOSSLESS SCALING (LSFG)", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).alpha(if (isDllReady) 1f else 0.4f).padding(12.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Ativar LSFG", color = theme.text, fontSize = 12.sp)
                            Switch(checked = lsfgEnabled && isDllReady, enabled = isDllReady, onCheckedChange = { lsfgEnabled = it; scope.launch(Dispatchers.IO) { graphicsLayerManager.applyLsfg(it, lsfgMultiplier, lsfgPacing) }; persistOverride() }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                        }
                        if (!isDllReady) {
                            Text("Requer Lossless.dll no Hub", color = Color(0xFFFF5252), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Multiplicador", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("2x", "3x", "4x").forEach { mult ->
                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (lsfgMultiplier == mult) theme.primary else theme.surface.copy(alpha = 0.6f)).clickable { lsfgMultiplier = mult; scope.launch(Dispatchers.IO) { graphicsLayerManager.applyLsfg(lsfgEnabled, mult, lsfgPacing) }; persistOverride() }.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                            Text(mult, color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Sincronia (Frame Pacing)", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                                Switch(checked = lsfgPacing, onCheckedChange = { lsfgPacing = it; scope.launch(Dispatchers.IO) { graphicsLayerManager.applyLsfg(lsfgEnabled, lsfgMultiplier, it) }; persistOverride() }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Modo Performance", color = theme.text.copy(alpha = 0.7f), fontSize = 11.sp)
                                Switch(checked = lsfgPerfMode, onCheckedChange = { lsfgPerfMode = it; persistOverride() }, colors = SwitchDefaults.colors(checkedThumbColor = theme.primary, checkedTrackColor = theme.primary.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
            }
            2 -> {
                Text("CONTROLE DA VENTOINHA", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Usa os settingsValue reais de FanMode (1/4/5) e o performanceManager
                    // partilhado, em vez de índices 0/1/2 escritos à mão com um ShellExecutor()
                    // novo a cada toque: essa combinação fazia com que o modo escolhido aqui
                    // fosse reinterpretado como outro (ou como "Stock") assim que o serviço de
                    // acessibilidade reaplicava os perfis ao trocar de app.
                    for (mode in FanMode.selectable) {
                        val isSel = fanMode == mode.settingsValue
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else Color.Transparent).clickable { fanMode = mode.settingsValue; scope.launch(Dispatchers.IO) { performanceManager.applyFanMode(mode) }; persistOverride() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(mode.shortLabel, color = if (isSel) Color.White else theme.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("MODO DE LIMITAÇÃO", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.3f)).border(1.dp, theme.text.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (activeLimitMode == "TDP") theme.primary else Color.Transparent).clickable { activeLimitMode = "TDP"; scope.launch(Dispatchers.IO) { performanceManager.applyDynamicTdp(tdpValue) }; persistOverride() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("TDP", color = if (activeLimitMode == "TDP") Color.White else theme.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (activeLimitMode == "CLOCK") theme.primary else Color.Transparent).clickable { activeLimitMode = "CLOCK"; scope.launch(Dispatchers.IO) { performanceManager.applyAbsoluteClocks((cpuPerfClock * 1000).toLong(), (cpuPrimeClock * 1000).toLong(), (gpuClock * 1000000).toLong()) }; persistOverride() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Clocks", color = if (activeLimitMode == "CLOCK") Color.White else theme.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (activeLimitMode == "TDP") {
                    Text("PERFIS DE TDP", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val tdpProfiles = listOf("Power Save" to 5f, "Balanced" to 10f, "Triple A" to 15f, "Stock" to 25f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((profName, watts) in tdpProfiles) {
                            val isSel = tdpValue == watts
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.5f)).clickable { tdpValue = watts; scope.launch(Dispatchers.IO) { performanceManager.applyDynamicTdp(watts) }; persistOverride() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text(profName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    val userTdpProfiles = prefs.getAllCustomProfiles().filter { it.type == "TDP" }
                    if (userTdpProfiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (prof in userTdpProfiles) {
                                val isSel = tdpValue == prof.v1
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.5f)).clickable { tdpValue = prof.v1; scope.launch(Dispatchers.IO) { performanceManager.applyDynamicTdp(prof.v1) }; persistOverride() }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(prof.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(12.dp)) {
                        Text("TDP Limit: ${tdpValue.toInt()} W", color = theme.text, fontSize = 12.sp)
                        Slider(value = tdpValue, onValueChange = { tdpValue = it; applyTdpLive(it) }, onValueChangeFinished = { persistOverride() }, valueRange = 5f..25f, colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = savedPresetName, onValueChange = { savedPresetName = it }, label = { Text("Nome do Preset", fontSize = 10.sp) }, modifier = Modifier.weight(1f).height(50.dp), textStyle = TextStyle(fontSize = 12.sp, color = theme.text))
                        Button(onClick = { if (savedPresetName.isNotBlank()) { prefs.saveCustomProfile(savedPresetName, "TDP", tdpValue, 0f, 0f, 0f); savedPresetName = "" } }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary), modifier = Modifier.height(50.dp)) { Text("Salvar", fontSize = 11.sp, color = Color.White) }
                    }
                } else {
                    Text("PERFIS DE UNDERCLOCK (Perf + Prime -- GPU é independente)", color = theme.text.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    // Perfis combinados: só tocam Cluster 0 (Perf) e Cluster 1 (Prime). A GPU
                    // NUNCA é alterada por estes botões -- fica sempre no que o utilizador definir
                    // manualmente no bloco "Adreno GPU" abaixo (ver ClockPresets.kt).
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (prof in de.langerhans.odintools.models.CombinedClockProfiles.all) {
                            val isSel = cpuPerfClock == prof.perfClockMHz && cpuPrimeClock == prof.primeClockMHz
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.5f)).clickable {
                                cpuPerfClock = prof.perfClockMHz
                                cpuPrimeClock = prof.primeClockMHz
                                applyClocksLive(cpuPerfClock, cpuPrimeClock, gpuClock)
                                persistOverride()
                            }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text(prof.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    val userClockProfiles = prefs.getAllCustomProfiles().filter { it.type == "CLOCK" }
                    if (userClockProfiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (prof in userClockProfiles) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.surface.copy(alpha = 0.5f)).clickable { cpuPerfClock = prof.v2; cpuPrimeClock = prof.v3; gpuClock = prof.v4; applyClocksLive(prof.v2, prof.v3, prof.v4); persistOverride() }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(prof.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surface.copy(alpha = 0.5f)).padding(12.dp)) {
                        // Presets discretos por cluster (estilo ClusterTune) em vez de sliders MHz
                        // livres: o 8 Elite só aceita as frequências (OPPs) da sua tabela de
                        // cpufreq -- um valor MHz "no meio" escolhido por um slider contínuo é
                        // frequentemente rejeitado/arredondado pelo kernel de forma imprevisível,
                        // o que explica o bug relatado ("os clocks não fazem efeito nenhum").
                        Text("Cluster Perf (Cluster 0): ${cpuPerfClock.toInt()} MHz", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (preset in de.langerhans.odintools.models.ClusterClockPresets.perfPresets) {
                                val isSel = cpuPerfClock == preset.clockMHz
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.6f)).clickable {
                                    cpuPerfClock = preset.clockMHz
                                    applyClocksLive(cpuPerfClock, cpuPrimeClock, gpuClock)
                                    persistOverride()
                                }.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(preset.label, color = theme.text, fontSize = 10.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Cluster Prime (Cluster 1): ${cpuPrimeClock.toInt()} MHz", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (preset in de.langerhans.odintools.models.ClusterClockPresets.primePresets) {
                                val isSel = cpuPrimeClock == preset.clockMHz
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.6f)).clickable {
                                    cpuPrimeClock = preset.clockMHz
                                    applyClocksLive(cpuPerfClock, cpuPrimeClock, gpuClock)
                                    persistOverride()
                                }.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(preset.label, color = theme.text, fontSize = 10.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Adreno GPU (independente): ${gpuClock.toInt()} MHz", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (preset in de.langerhans.odintools.models.ClusterClockPresets.gpuPresets) {
                                val isSel = gpuClock == preset.clockMHz
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.primary else theme.surface.copy(alpha = 0.6f)).clickable {
                                    gpuClock = preset.clockMHz
                                    applyClocksLive(cpuPerfClock, cpuPrimeClock, gpuClock)
                                    persistOverride()
                                }.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(preset.label, color = theme.text, fontSize = 10.sp)
                                }
                            }
                        }
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
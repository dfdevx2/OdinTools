package de.langerhans.odintools.appsettings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.data.AppOverrideEntity
import de.langerhans.odintools.data.AppOverrideRepository
import de.langerhans.odintools.models.ClusterClockPresets
import de.langerhans.odintools.models.CombinedClockProfiles
import de.langerhans.odintools.models.FanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Perfis de TDP nomeados usados por este ecrã -- os MESMOS valores (5/10/15/25 W) que o overlay
 * (`QuickAccessContent.tdpProfiles`) e o painel global de Settings usam para "Power Save" /
 * "Balanced" / "Triple A" / "Stock". Mantidos aqui em vez de duplicados nesses dois ficheiros
 * seria o ideal a longo prazo, mas para já replicamos os MESMOS números de propósito: o objetivo
 * desta ronda é que os três ecrãs concordem sempre no que cada nome de perfil significa em watts
 * reais -- o bug original (Parte 2 da auditoria) era precisamente perfis com o mesmo nome a
 * significarem coisas diferentes (ou nada) em cada ecrã.
 */
private val NAMED_TDP_PROFILES = listOf("Power Save" to 5f, "Balanced" to 10f, "Triple A" to 15f, "Stock" to 25f)

data class AppOverrideUiState(
    val packageName: String = "",
    val appName: String = "",

    // TDP e Clock são MUTUAMENTE EXCLUSIVOS aqui, exatamente como no overlay e no ecrã de
    // Settings global (ver AppOverrideEntity.limitMode / ForegroundAppWatcherService). Antes desta
    // reformulação este ecrã tinha o seu próprio esquema de exclusividade ("Nenhum" bloqueia o
    // outro cartão), incompatível com o resto da app.
    val limitMode: String = "TDP",
    val tdpWatts: Float = 15f,
    val perfClockMHz: Float = 3530f,
    val primeClockMHz: Float = 4320f,
    val gpuClockMHz: Float = 1100f,

    // -1 = FanMode.Stock (sem override -- usa o modo global). Ver FanMode.kt.
    val fanSettingsValue: Int = FanMode.Stock.settingsValue,

    val lsfgEnabled: Boolean = false,
    val lsfgMultiplier: String = "2x",
    val lsfgFramePacing: Boolean = true,
    val lsfgPerformanceMode: Boolean = false,
    val lsfgQuality: Float = 1.0f,

    val sgsrEnabled: Boolean = false,
    val sgsrMode: String = "Quality",
    val sgsrSharpness: Float = 0.5f,

    val reshadeProfile: String = "Native",
    val saturationOverride: Float = 1.0f,
    val temperatureOverride: Float = 6500f,

    val isSaved: Boolean = false,
    val availableReshadeProfiles: List<String> = listOf("Native", "Vibrant", "Retro", "HDR Boost", "Game Clarity", "Cinematic")
)

@HiltViewModel
class AppOverrideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    // Fonte única de verdade partilhada com o overlay e o ForegroundAppWatcherService (ver
    // AppOverrideRepository) -- este ecrã e o overlay passam a ser "parceiros" na mesma linha do
    // Room em vez de dois caminhos de escrita desligados um do outro.
    private val overrideRepository: AppOverrideRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppOverrideUiState())
    val uiState: StateFlow<AppOverrideUiState> = _uiState.asStateFlow()

    init {
        val packageName = savedStateHandle.get<String>("packageName") ?: ""
        _uiState.update { it.copy(packageName = packageName) }

        loadAppDetails(packageName)
        loadOverride(packageName)
    }

    private fun loadAppDetails(packageName: String) {
        val pm = context.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
        _uiState.update { it.copy(appName = appName) }
    }

    /**
     * Lê do cache em memória do repositório -- o MESMO valor que o overlay/ForegroundAppWatcherService
     * veem. Se o utilizador tiver acabado de configurar este jogo pelo overlay, este ecrã já abre
     * com esses valores em vez de "Nenhum"/valores por omissão.
     */
    private fun loadOverride(packageName: String) {
        val entity = overrideRepository.get(packageName) ?: return
        _uiState.update {
            it.copy(
                limitMode = entity.limitMode,
                tdpWatts = entity.tdpWatts ?: it.tdpWatts,
                perfClockMHz = entity.perfClockKHz?.let { khz -> khz / 1000f } ?: it.perfClockMHz,
                primeClockMHz = entity.primeClockKHz?.let { khz -> khz / 1000f } ?: it.primeClockMHz,
                gpuClockMHz = entity.gpuClockHz?.let { hz -> hz / 1_000_000f } ?: it.gpuClockMHz,
                fanSettingsValue = entity.fanSettingsValue ?: it.fanSettingsValue,

                lsfgEnabled = entity.lsfgEnabled,
                lsfgMultiplier = "${entity.lsfgMultiplier}x",
                lsfgFramePacing = entity.lsfgFramePacing,
                lsfgPerformanceMode = entity.lsfgPerformanceMode,
                lsfgQuality = entity.lsfgQuality,

                sgsrEnabled = entity.sgsrEnabled,
                sgsrMode = entity.sgsrMode,
                sgsrSharpness = entity.sgsrSharpness,

                reshadeProfile = entity.reshadeProfile,
                saturationOverride = entity.saturationOverride,
                temperatureOverride = entity.temperatureOverride,

                isSaved = true
            )
        }
    }

    fun updateLimitMode(mode: String) = _uiState.update { it.copy(limitMode = mode) }
    fun updateTdp(watts: Float) = _uiState.update { it.copy(tdpWatts = watts) }

    /** GPU é sempre passada separadamente -- ver comentário em ClockPresets.kt sobre porquê. */
    fun updateClocks(perfMHz: Float, primeMHz: Float, gpuMHz: Float) =
        _uiState.update { it.copy(perfClockMHz = perfMHz, primeClockMHz = primeMHz, gpuClockMHz = gpuMHz) }

    fun updateFanMode(settingsValue: Int) = _uiState.update { it.copy(fanSettingsValue = settingsValue) }

    fun updateLsfg(enabled: Boolean, multiplier: String, framePacing: Boolean, performanceMode: Boolean, quality: Float) {
        _uiState.update { it.copy(lsfgEnabled = enabled, lsfgMultiplier = multiplier, lsfgFramePacing = framePacing, lsfgPerformanceMode = performanceMode, lsfgQuality = quality) }
    }

    fun updateSgsr(enabled: Boolean, mode: String, sharpness: Float) {
        _uiState.update { it.copy(sgsrEnabled = enabled, sgsrMode = mode, sgsrSharpness = sharpness) }
    }

    fun updateDisplayColor(reshade: String, saturation: Float, temperature: Float) {
        _uiState.update { it.copy(reshadeProfile = reshade, saturationOverride = saturation, temperatureOverride = temperature) }
    }

    /**
     * Nome de exibição do preset ativo, só para a lista de jogos (ver AppOverrideMapper) -- os
     * valores numéricos continuam a ser a fonte de verdade real; isto é só um rótulo bonito.
     * Devolve "Custom" quando os valores não coincidem exatamente com nenhum perfil nomeado (ex:
     * o utilizador ajustou o TDP manualmente para um valor fora dos presets).
     */
    private fun tdpProfileLabel(watts: Float): String =
        NAMED_TDP_PROFILES.firstOrNull { it.second == watts }?.first ?: "Custom (${watts.toInt()}W)"

    private fun clockProfileLabel(perfMHz: Float, primeMHz: Float): String =
        CombinedClockProfiles.all.firstOrNull { it.perfClockMHz == perfMHz && it.primeClockMHz == primeMHz }?.label
            ?: "Custom"

    fun saveOverride() {
        viewModelScope.launch {
            val current = _uiState.value
            val multInt = current.lsfgMultiplier.replace("x", "").toIntOrNull() ?: 2
            val isTdp = current.limitMode == "TDP"

            val entity = AppOverrideEntity(
                packageName = current.packageName,
                limitMode = current.limitMode,
                tdpWatts = current.tdpWatts,
                perfClockKHz = (current.perfClockMHz * 1000).toLong(),
                primeClockKHz = (current.primeClockMHz * 1000).toLong(),
                gpuClockHz = (current.gpuClockMHz * 1_000_000).toLong(),
                fanSettingsValue = current.fanSettingsValue,

                // Rótulos só para a subtitle da lista (AppOverrideMapper) -- só o modo ATIVO
                // ganha um rótulo descritivo, o outro fica "Stock" para não sugerir que os dois
                // modos estão em vigor ao mesmo tempo (não estão -- ver limitMode).
                tdpProfile = if (isTdp) tdpProfileLabel(current.tdpWatts) else "Stock",
                clockProfile = if (!isTdp) clockProfileLabel(current.perfClockMHz, current.primeClockMHz) else "Stock",
                fanProfile = FanMode.fromSettingsValue(current.fanSettingsValue).shortLabel,

                lsfgEnabled = current.lsfgEnabled,
                lsfgMultiplier = multInt,
                lsfgPerformanceMode = current.lsfgPerformanceMode,
                lsfgFramePacing = current.lsfgFramePacing,
                lsfgQuality = current.lsfgQuality,

                sgsrEnabled = current.sgsrEnabled,
                sgsrMode = current.sgsrMode,
                sgsrSharpness = current.sgsrSharpness,

                reshadeProfile = current.reshadeProfile,
                saturationOverride = current.saturationOverride,
                temperatureOverride = current.temperatureOverride
            )
            overrideRepository.upsert(entity)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deleteOverride() {
        viewModelScope.launch {
            overrideRepository.delete(_uiState.value.packageName)
            _uiState.update { AppOverrideUiState(packageName = it.packageName, appName = it.appName) }
        }
    }
}

package de.langerhans.odintools.appsettings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.data.AppOverrideDao
import de.langerhans.odintools.data.AppOverrideEntity
import de.langerhans.odintools.data.SharedPrefsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppOverrideUiState(
    val packageName: String = "",
    val appName: String = "",

    val tdpProfile: String = "Nenhum",
    val clockProfile: String = "Nenhum",
    val fanProfile: String = "Nenhum",

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
    val availableTdpProfiles: List<String> = emptyList(),
    val availableClockProfiles: List<String> = emptyList(),
    val availableReshadeProfiles: List<String> = listOf("Native", "Vibrant", "Retro", "HDR Boost", "Game Clarity", "Cinematic")
)

@HiltViewModel
class AppOverrideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overrideDao: AppOverrideDao,
    private val sharedPrefsRepo: SharedPrefsRepo,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppOverrideUiState())
    val uiState: StateFlow<AppOverrideUiState> = _uiState.asStateFlow()

    init {
        val packageName = savedStateHandle.get<String>("packageName") ?: ""

        val baseTdp = listOf("Nenhum", "Power Save", "Balanced", "Triple A", "Stock")
        val baseClock = listOf("Nenhum", "Power Save", "Balanced", "Triple A", "Stock")

        _uiState.update { it.copy(
            packageName = packageName,
            availableTdpProfiles = baseTdp,
            availableClockProfiles = baseClock
        ) }

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

    private fun loadOverride(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = overrideDao.getForPackage(packageName)
            if (entity != null) {
                _uiState.update { it.copy(
                    tdpProfile = entity.tdpProfile ?: "Nenhum",
                    clockProfile = entity.clockProfile ?: "Nenhum",
                    fanProfile = entity.fanProfile ?: "Nenhum",

                    lsfgEnabled = entity.lsfgEnabled,
                    lsfgMultiplier = "${entity.lsfgMultiplier}x",
                    lsfgFramePacing = entity.lsfgFramePacing,
                    lsfgPerformanceMode = entity.lsfgPerformanceMode,
                    lsfgQuality = entity.lsfgQuality,

                    sgsrEnabled = entity.sgsrEnabled,
                    sgsrMode = entity.sgsrMode,
                    sgsrSharpness = entity.sgsrSharpness,

                    reshadeProfile = entity.reshadeProfile ?: "Native",
                    saturationOverride = entity.saturationOverride,
                    temperatureOverride = entity.temperatureOverride,

                    isSaved = true
                ) }
            }
        }
    }

    fun updatePerformance(tdp: String, clock: String, fan: String) {
        _uiState.update { it.copy(tdpProfile = tdp, clockProfile = clock, fanProfile = fan) }
    }

    fun updateLsfg(enabled: Boolean, multiplier: String, framePacing: Boolean, performanceMode: Boolean, quality: Float) {
        _uiState.update { it.copy(lsfgEnabled = enabled, lsfgMultiplier = multiplier, lsfgFramePacing = framePacing, lsfgPerformanceMode = performanceMode, lsfgQuality = quality) }
    }

    fun updateSgsr(enabled: Boolean, mode: String, sharpness: Float) {
        _uiState.update { it.copy(sgsrEnabled = enabled, sgsrMode = mode, sgsrSharpness = sharpness) }
    }

    fun updateDisplayColor(reshade: String, saturation: Float, temperature: Float) {
        _uiState.update { it.copy(reshadeProfile = reshade, saturationOverride = saturation, temperatureOverride = temperature) }
    }

    fun saveOverride() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value
            val multInt = current.lsfgMultiplier.replace("x", "").toIntOrNull() ?: 2

            val entity = AppOverrideEntity(
                packageName = current.packageName,
                tdpProfile = if (current.tdpProfile == "Nenhum") null else current.tdpProfile,
                clockProfile = if (current.clockProfile == "Nenhum") null else current.clockProfile,
                fanProfile = if (current.fanProfile == "Nenhum") null else current.fanProfile,

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
            overrideDao.save(entity)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deleteOverride() {
        viewModelScope.launch(Dispatchers.IO) {
            overrideDao.deleteByPackageName(_uiState.value.packageName)
            _uiState.update { AppOverrideUiState(
                packageName = it.packageName,
                appName = it.appName,
                availableTdpProfiles = it.availableTdpProfiles,
                availableClockProfiles = it.availableClockProfiles
            ) }
        }
    }
}

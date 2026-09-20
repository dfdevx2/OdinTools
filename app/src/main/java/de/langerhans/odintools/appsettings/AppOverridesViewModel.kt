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
    val isSaved: Boolean = false,
    val availableTdpProfiles: List<String> = emptyList(),
    val availableClockProfiles: List<String> = emptyList()
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

        // Puxa os perfis padrão + os perfis customizados que você já criou!
        val baseTdp = listOf("Nenhum", "Power Save (5W)", "Balanced (11W)", "Triple A (14.5W)", "Stock (Padrão AYN)")
        val customTdp = sharedPrefsRepo.customTdpProfiles.map { it.name }

        val baseClock = listOf("Nenhum", "Power Save (Underclock Seguro)", "Balanced (Intermediário)", "Triple A (Alto Desempenho)", "Stock (Padrão AYN)")
        val customClock = sharedPrefsRepo.customClockProfiles.map { it.name }

        _uiState.update { it.copy(
            packageName = packageName,
            availableTdpProfiles = baseTdp + customTdp,
            availableClockProfiles = baseClock + customClock
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
                    isSaved = true
                ) }
            }
        }
    }

    fun updateTdpProfile(profile: String) {
        _uiState.update { it.copy(tdpProfile = profile) }
    }

    fun updateClockProfile(profile: String) {
        _uiState.update { it.copy(clockProfile = profile) }
    }

    fun updateFanProfile(profile: String) {
        _uiState.update { it.copy(fanProfile = profile) }
    }

    fun saveOverride() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value
            val entity = AppOverrideEntity(
                packageName = current.packageName,
                tdpProfile = if (current.tdpProfile == "Nenhum") null else current.tdpProfile,
                clockProfile = if (current.clockProfile == "Nenhum") null else current.clockProfile,
                fanProfile = if (current.fanProfile == "Nenhum") null else current.fanProfile
            )
            overrideDao.save(entity)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deleteOverride() {
        viewModelScope.launch(Dispatchers.IO) {
            overrideDao.deleteByPackageName(_uiState.value.packageName)
            _uiState.update { it.copy(
                tdpProfile = "Nenhum",
                clockProfile = "Nenhum",
                fanProfile = "Nenhum",
                isSaved = false
            ) }
        }
    }
}

package com.dfdx047.odinhub.appsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.dfdx047.odinhub.data.AppOverrideDao
import com.dfdx047.odinhub.data.SharedPrefsRepo
import com.dfdx047.odinhub.tools.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppOverrideListViewModel @Inject constructor(
    private val appOverrideDao: AppOverrideDao,
    private val appOverrideMapper: AppOverrideMapper,
    private val deviceUtils: DeviceUtils,
    private val prefs: SharedPrefsRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppOverrideListUiModel())
    val uiState: StateFlow<AppOverrideListUiModel> = _uiState.asStateFlow()

    private var existingOverrides = emptyList<String>()

    init {
        // O tema escolhido pelo utilizador, para este ecrã deixar de ser o único com cores fixas.
        // Lido no arranque do ViewModel (que é recriado a cada navegação para este ecrã), por isso
        // uma mudança de tema feita em Settings já aparece aqui da próxima vez que se entra.
        _uiState.update {
            it.copy(selectedThemeIndex = prefs.selectedThemeIndex, useAmoledBlack = prefs.useAmoledBlack)
        }

        viewModelScope.launch {
            appOverrideDao.getAll()
                .flowOn(Dispatchers.IO)
                .collect { overrides ->
                    existingOverrides = overrides.map { it.packageName }
                    _uiState.update {
                        it.copy(
                            overrideList = appOverrideMapper.mapAppOverrides(overrides),
                            overrideCandidates = appOverrideMapper.mapOverrideCandidates(overrides),
                            deviceVersion = deviceUtils.getDeviceVersion(),
                        )
                    }
                }
        }
    }

    fun addClicked() {
        _uiState.update {
            it.copy(showAppSelectDialog = true)
        }
    }

    fun dismissAppSelectDialog() {
        _uiState.update {
            it.copy(showAppSelectDialog = false)
        }
    }

    /**
     * System settings:
     * performance_mode: standard 0, performance 1, high performance 2
     * fan_mode: disabled 0, quiet 1, (balance 2), (performance 3), smart 4, sport 5, custom 6
     */
}
package de.langerhans.odintools.appsettings

import android.graphics.drawable.Drawable

data class AppOverrideListUiModel(
    val deviceVersion: String = "",
    val showAppSelectDialog: Boolean = false,
    val overrideList: List<AppUiModel> = emptyList(),
    val overrideCandidates: List<AppUiModel> = emptyList(),
)

data class AppOverridesUiModel(
    val deviceVersion: String = "",
    val app: AppUiModel? = null,
    val hasUnsavedChanges: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val navigateBack: Boolean = false,
    val isNewApp: Boolean = false,
    val disabledFanModeKeys: List<String> = emptyList(),
)

data class AppUiModel(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable,
    val subtitle: String? = null,
    val tdpProfile: String? = null,
    val clockProfile: String? = null,
    val fanProfile: String? = null,
)
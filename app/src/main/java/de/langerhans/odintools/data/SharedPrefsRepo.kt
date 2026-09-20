package de.langerhans.odintools.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsRepo @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_FIRST_RUN, value).apply()

    var selectedThemeIndex: Int
        get() = prefs.getInt(KEY_SELECTED_THEME_INDEX, 1)
        set(value) = prefs.edit().putInt(KEY_SELECTED_THEME_INDEX, value).apply()

    var useAmoledBlack: Boolean
        get() = prefs.getBoolean(KEY_USE_AMOLED_BLACK, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_AMOLED_BLACK, value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    // Handle Customization Preferences
    var overlayHandleOpacity: Float
        get() = prefs.getFloat(KEY_OVERLAY_HANDLE_OPACITY, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_OVERLAY_HANDLE_OPACITY, value).apply()

    var overlayHandleWidth: Int
        get() = prefs.getInt(KEY_OVERLAY_HANDLE_WIDTH, 22)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_HANDLE_WIDTH, value).apply()

    var overlayHandlePosY: Int
        get() = prefs.getInt(KEY_OVERLAY_HANDLE_POS_Y, 0)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_HANDLE_POS_Y, value).apply()

    var globalLsfgEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_LSFG_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_LSFG_ENABLED, value).apply()

    var lsfgMultiplier: String
        get() = prefs.getString(KEY_LSFG_MULTIPLIER, "2x") ?: "2x"
        set(value) = prefs.edit().putString(KEY_LSFG_MULTIPLIER, value).apply()

    var lsfgFramePacing: Boolean
        get() = prefs.getBoolean(KEY_LSFG_PACING, true)
        set(value) = prefs.edit().putBoolean(KEY_LSFG_PACING, value).apply()

    var globalSgsrEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_SGSR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_SGSR_ENABLED, value).apply()

    var sgsrMode: String
        get() = prefs.getString(KEY_SGSR_MODE, "Quality") ?: "Quality"
        set(value) = prefs.edit().putString(KEY_SGSR_MODE, value).apply()

    var reshadeProfile: String
        get() = prefs.getString(KEY_RESHADE_PROFILE, "Native") ?: "Native"
        set(value) = prefs.edit().putString(KEY_RESHADE_PROFILE, value).apply()

    var saturationOverride: Float
        get() = prefs.getFloat(KEY_SATURATION_OVERRIDE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SATURATION_OVERRIDE, value).apply()

    var temperatureOverride: Float
        get() = prefs.getFloat(KEY_TEMPERATURE_OVERRIDE, 6500f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE_OVERRIDE, value).apply()

    companion object {
        private const val KEY_IS_FIRST_RUN = "is_first_run"
        private const val KEY_SELECTED_THEME_INDEX = "selected_theme_index"
        private const val KEY_USE_AMOLED_BLACK = "use_amoled_black"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_OVERLAY_HANDLE_OPACITY = "overlay_handle_opacity"
        private const val KEY_OVERLAY_HANDLE_WIDTH = "overlay_handle_width"
        private const val KEY_OVERLAY_HANDLE_POS_Y = "overlay_handle_pos_y"
        private const val KEY_GLOBAL_LSFG_ENABLED = "global_lsfg_enabled"
        private const val KEY_LSFG_MULTIPLIER = "lsfg_multiplier"
        private const val KEY_LSFG_PACING = "lsfg_pacing"
        private const val KEY_GLOBAL_SGSR_ENABLED = "global_sgsr_enabled"
        private const val KEY_SGSR_MODE = "sgsr_mode"
        private const val KEY_RESHADE_PROFILE = "reshade_profile"
        private const val KEY_SATURATION_OVERRIDE = "saturation_override"
        private const val KEY_TEMPERATURE_OVERRIDE = "temperature_override"
    }
}

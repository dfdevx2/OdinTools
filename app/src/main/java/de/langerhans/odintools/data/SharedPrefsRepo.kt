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

    var overlayHandleOpacity: Float
        get() = prefs.getFloat(KEY_OVERLAY_HANDLE_OPACITY, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_OVERLAY_HANDLE_OPACITY, value).apply()

    var overlayHandleWidth: Int
        get() = prefs.getInt(KEY_OVERLAY_HANDLE_WIDTH, 22)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_HANDLE_WIDTH, value).apply()

    var disabledControllerStyle: String?
        get() = prefs.getString(KEY_DISABLED_CONTROLLER_STYLE, null)
        set(value) = prefs.edit().putString(KEY_DISABLED_CONTROLLER_STYLE, value).apply()

    var disabledL2r2Style: String?
        get() = prefs.getString(KEY_DISABLED_L2R2_STYLE, null)
        set(value) = prefs.edit().putString(KEY_DISABLED_L2R2_STYLE, value).apply()

    var saturationOverride: Float
        get() = prefs.getFloat(KEY_SATURATION_OVERRIDE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SATURATION_OVERRIDE, value).apply()

    var temperatureOverride: Float
        get() = prefs.getFloat(KEY_TEMPERATURE_OVERRIDE, 6500f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE_OVERRIDE, value).apply()

    var globalLsfgEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_LSFG_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_LSFG_ENABLED, value).apply()

    var lsfgMultiplier: String
        get() = prefs.getString(KEY_LSFG_MULTIPLIER, "2x") ?: "2x"
        set(value) = prefs.edit().putString(KEY_LSFG_MULTIPLIER, value).apply()

    var lsfgFramePacing: Boolean
        get() = prefs.getBoolean(KEY_LSFG_PACING, true)
        set(value) = prefs.edit().putBoolean(KEY_LSFG_PACING, value).apply()

    var lsfgPerformanceMode: Boolean
        get() = prefs.getBoolean(KEY_LSFG_PERFORMANCE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_LSFG_PERFORMANCE_MODE, value).apply()

    var lsfgGeneratedQuality: Float
        get() = prefs.getFloat(KEY_LSFG_GENERATED_QUALITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_LSFG_GENERATED_QUALITY, value).apply()

    var globalSgsrEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_SGSR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_SGSR_ENABLED, value).apply()

    var sgsrMode: String
        get() = prefs.getString(KEY_SGSR_MODE, "Quality") ?: "Quality"
        set(value) = prefs.edit().putString(KEY_SGSR_MODE, value).apply()

    var sgsrSharpness: Float
        get() = prefs.getFloat(KEY_SGSR_SHARPNESS, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_SGSR_SHARPNESS, value).apply()

    var reshadeProfile: String
        get() = prefs.getString(KEY_RESHADE_PROFILE, "Native") ?: "Native"
        set(value) = prefs.edit().putString(KEY_RESHADE_PROFILE, value).apply()

    var showFpsOverlay: Boolean
        get() = prefs.getBoolean(KEY_FPS_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_FPS_OVERLAY, value).apply()

    var currentForegroundApp: String
        get() = prefs.getString(KEY_CURRENT_FG_APP, "global") ?: "global"
        set(value) = prefs.edit().putString(KEY_CURRENT_FG_APP, value).apply()

    // Unified Per-App Bridge (Shared between App Overrides menu and Overlay)
    fun savePerAppConfig(packageName: String, tdp: Float, perfClock: Float, primeClock: Float, gpuClock: Float, reshade: String, sgsr: Boolean, sgsrMode: String, lsfg: Boolean) {
        prefs.edit()
            .putFloat("override_${packageName}_tdp", tdp)
            .putFloat("override_${packageName}_perf", perfClock)
            .putFloat("override_${packageName}_prime", primeClock)
            .putFloat("override_${packageName}_gpu", gpuClock)
            .putString("override_${packageName}_reshade", reshade)
            .putBoolean("override_${packageName}_sgsr", sgsr)
            .putString("override_${packageName}_sgsrmode", sgsrMode)
            .putBoolean("override_${packageName}_lsfg", lsfg)
            .apply()
    }

    fun getPerAppTdp(packageName: String, default: Float): Float = prefs.getFloat("override_${packageName}_tdp", default)
    fun getPerAppPerfClock(packageName: String, default: Float): Float = prefs.getFloat("override_${packageName}_perf", default)
    fun getPerAppPrimeClock(packageName: String, default: Float): Float = prefs.getFloat("override_${packageName}_prime", default)
    fun getPerAppGpuClock(packageName: String, default: Float): Float = prefs.getFloat("override_${packageName}_gpu", default)
    fun getPerAppReshade(packageName: String, default: String): String = prefs.getString("override_${packageName}_reshade", default) ?: default

    fun saveCustomProfile(name: String, type: String, val1: Float, val2: Float, val3: Float, val4: Float) {
        prefs.edit()
            .putString("custom_profile_${name}_type", type)
            .putFloat("custom_profile_${name}_v1", val1)
            .putFloat("custom_profile_${name}_v2", val2)
            .putFloat("custom_profile_${name}_v3", val3)
            .putFloat("custom_profile_${name}_v4", val4)
            .apply()
    }

    companion object {
        private const val KEY_IS_FIRST_RUN = "is_first_run"
        private const val KEY_SELECTED_THEME_INDEX = "selected_theme_index"
        private const val KEY_USE_AMOLED_BLACK = "use_amoled_black"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_OVERLAY_HANDLE_OPACITY = "overlay_handle_opacity"
        private const val KEY_OVERLAY_HANDLE_WIDTH = "overlay_handle_width"
        private const val KEY_DISABLED_CONTROLLER_STYLE = "disabled_controller_style"
        private const val KEY_DISABLED_L2R2_STYLE = "disabled_l2r2_style"
        private const val KEY_SATURATION_OVERRIDE = "saturation_override"
        private const val KEY_TEMPERATURE_OVERRIDE = "temperature_override"
        private const val KEY_GLOBAL_LSFG_ENABLED = "global_lsfg_enabled"
        private const val KEY_LSFG_MULTIPLIER = "lsfg_multiplier"
        private const val KEY_LSFG_PACING = "lsfg_pacing"
        private const val KEY_LSFG_PERFORMANCE_MODE = "lsfg_performance_mode"
        private const val KEY_LSFG_GENERATED_QUALITY = "lsfg_generated_quality"
        private const val KEY_GLOBAL_SGSR_ENABLED = "global_sgsr_enabled"
        private const val KEY_SGSR_MODE = "sgsr_mode"
        private const val KEY_SGSR_SHARPNESS = "sgsr_sharpness"
        private const val KEY_RESHADE_PROFILE = "reshade_profile"
        private const val KEY_FPS_OVERLAY = "fps_overlay"
        private const val KEY_CURRENT_FG_APP = "current_foreground_app"
    }
}

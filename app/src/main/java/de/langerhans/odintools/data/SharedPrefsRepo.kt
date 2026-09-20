package de.langerhans.odintools.data

import android.content.SharedPreferences
import javax.inject.Inject

class SharedPrefsRepo @Inject constructor(
    private val prefs: SharedPreferences
) {
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

    var vibrationStrength: Int
        get() = prefs.getInt(KEY_VIBRATION_STRENGTH, 50)
        set(value) = prefs.edit().putInt(KEY_VIBRATION_STRENGTH, value).apply()

    var appOverridesEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_OVERRIDES_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_OVERRIDES_ENABLED, value).apply()

    var overrideDelay: Boolean
        get() = prefs.getBoolean(KEY_OVERRIDE_DELAY, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERRIDE_DELAY, value).apply()

    var chargeLimitEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHARGE_LIMIT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CHARGE_LIMIT_ENABLED, value).apply()

    var minBatteryLevel: Int
        get() = prefs.getInt(KEY_MIN_BATTERY_LEVEL, 75)
        set(value) = prefs.edit().putInt(KEY_MIN_BATTERY_LEVEL, value).apply()

    var maxBatteryLevel: Int
        get() = prefs.getInt(KEY_MAX_BATTERY_LEVEL, 85)
        set(value) = prefs.edit().putInt(KEY_MAX_BATTERY_LEVEL, value).apply()

    var videoOutputOverrideEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_OUTPUT_OVERRIDE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_OUTPUT_OVERRIDE_ENABLED, value).apply()

    // CORRIGIDO: Retornou para String? em vez de Int
    var videoOutputControllerStyle: String?
        get() = prefs.getString(KEY_VIDEO_OUTPUT_CONTROLLER_STYLE, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_OUTPUT_CONTROLLER_STYLE, value).apply()

    // CORRIGIDO: Retornou para String? em vez de Int
    var videoOutputL2R2Style: String?
        get() = prefs.getString(KEY_VIDEO_OUTPUT_L2R2_STYLE, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_OUTPUT_L2R2_STYLE, value).apply()

    // Persistência Global para o Lossless Scaling e SGSR na aba Display
    var globalLsfgEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_LSFG_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_LSFG_ENABLED, value).apply()

    var globalSgsrEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_SGSR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_SGSR_ENABLED, value).apply()

    companion object {
        private const val KEY_DISABLED_CONTROLLER_STYLE = "disabled_controller_style"
        private const val KEY_DISABLED_L2R2_STYLE = "disabled_l2r2_style"
        private const val KEY_SATURATION_OVERRIDE = "saturation_override"
        private const val KEY_TEMPERATURE_OVERRIDE = "temperature_override"
        private const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        private const val KEY_APP_OVERRIDES_ENABLED = "app_overrides_enabled"
        private const val KEY_OVERRIDE_DELAY = "override_delay"
        private const val KEY_CHARGE_LIMIT_ENABLED = "charge_limit_enabled"
        private const val KEY_MIN_BATTERY_LEVEL = "min_battery_level"
        private const val KEY_MAX_BATTERY_LEVEL = "max_battery_level"
        private const val KEY_VIDEO_OUTPUT_OVERRIDE_ENABLED = "video_output_override_enabled"
        private const val KEY_VIDEO_OUTPUT_CONTROLLER_STYLE = "video_output_controller_style"
        private const val KEY_VIDEO_OUTPUT_L2R2_STYLE = "video_output_l2r2_style"
        private const val KEY_GLOBAL_LSFG_ENABLED = "global_lsfg_enabled"
        private const val KEY_GLOBAL_SGSR_ENABLED = "global_sgsr_enabled"
    }
}

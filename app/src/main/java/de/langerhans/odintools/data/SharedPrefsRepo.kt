package de.langerhans.odintools.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CustomProfile(val name: String, val type: String, val v1: Float, val v2: Float, val v3: Float, val v4: Float)

@Singleton
class SharedPrefsRepo @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    var isFirstRun: Boolean get() = prefs.getBoolean("is_first_run", true); set(value) = prefs.edit().putBoolean("is_first_run", value).apply()
    var selectedThemeIndex: Int get() = prefs.getInt("selected_theme_index", 1); set(value) = prefs.edit().putInt("selected_theme_index", value).apply()
    var useAmoledBlack: Boolean get() = prefs.getBoolean("use_amoled_black", false); set(value) = prefs.edit().putBoolean("use_amoled_black", value).apply()
    var overlayEnabled: Boolean get() = prefs.getBoolean("overlay_enabled", false); set(value) = prefs.edit().putBoolean("overlay_enabled", value).apply()
    var overlayHandleOpacity: Float get() = prefs.getFloat("overlay_handle_opacity", 0.5f); set(value) = prefs.edit().putFloat("overlay_handle_opacity", value).apply()
    var overlayHandleWidth: Int get() = prefs.getInt("overlay_handle_width", 22); set(value) = prefs.edit().putInt("overlay_handle_width", value).apply()

    var overlayPanelOpacity: Float get() = prefs.getFloat("overlay_panel_opacity", 0.95f); set(value) = prefs.edit().putFloat("overlay_panel_opacity", value).apply()
    var overlayShortcutKeyCode: Int get() = prefs.getInt("overlay_shortcut_keycode", 0); set(value) = prefs.edit().putInt("overlay_shortcut_keycode", value).apply()

    var bgmVolume: Float get() = prefs.getFloat("bgm_volume", 0.5f); set(value) = prefs.edit().putFloat("bgm_volume", value).apply()
    var sfxVolume: Float get() = prefs.getFloat("sfx_volume", 0.8f); set(value) = prefs.edit().putFloat("sfx_volume", value).apply()

    var disabledControllerStyle: String? get() = prefs.getString("disabled_controller_style", null); set(value) = prefs.edit().putString("disabled_controller_style", value).apply()
    var disabledL2r2Style: String? get() = prefs.getString("disabled_l2r2_style", null); set(value) = prefs.edit().putString("disabled_l2r2_style", value).apply()
    var saturationOverride: Float get() = prefs.getFloat("saturation_override", 1.0f); set(value) = prefs.edit().putFloat("saturation_override", value).apply()
    var temperatureOverride: Float get() = prefs.getFloat("temperature_override", 6500f); set(value) = prefs.edit().putFloat("temperature_override", value).apply()
    var appOverridesEnabled: Boolean get() = prefs.getBoolean("app_overrides_enabled", false); set(value) = prefs.edit().putBoolean("app_overrides_enabled", value).apply()
    var useRootTarget: Boolean get() = prefs.getBoolean("use_root_target", false); set(value) = prefs.edit().putBoolean("use_root_target", value).apply()
    var fanMode: Int get() = prefs.getInt("fan_mode", 0); set(value) = prefs.edit().putInt("fan_mode", value).apply()

    var activeLimitMode: String get() = prefs.getString("active_limit_mode", "TDP") ?: "TDP"; set(value) = prefs.edit().putString("active_limit_mode", value).apply()
    var tdpValue: Float get() = prefs.getFloat("tdp_value", 15f); set(value) = prefs.edit().putFloat("tdp_value", value).apply()
    var cpuPerfClock: Float get() = prefs.getFloat("cpu_perf_clock", 3530f); set(value) = prefs.edit().putFloat("cpu_perf_clock", value).apply()
    var cpuPrimeClock: Float get() = prefs.getFloat("cpu_prime_clock", 4320f); set(value) = prefs.edit().putFloat("cpu_prime_clock", value).apply()
    var gpuClock: Float get() = prefs.getFloat("gpu_clock", 1100f); set(value) = prefs.edit().putFloat("gpu_clock", value).apply()

    var globalLsfgEnabled: Boolean get() = prefs.getBoolean("global_lsfg_enabled", false); set(value) = prefs.edit().putBoolean("global_lsfg_enabled", value).apply()
    var lsfgMultiplier: String get() = prefs.getString("lsfg_multiplier", "2x") ?: "2x"; set(value) = prefs.edit().putString("lsfg_multiplier", value).apply()
    var lsfgFramePacing: Boolean get() = prefs.getBoolean("lsfg_pacing", true); set(value) = prefs.edit().putBoolean("lsfg_pacing", value).apply()
    var lsfgPerformanceMode: Boolean get() = prefs.getBoolean("lsfg_performance_mode", false); set(value) = prefs.edit().putBoolean("lsfg_performance_mode", value).apply()

    var globalSgsrEnabled: Boolean get() = prefs.getBoolean("global_sgsr_enabled", false); set(value) = prefs.edit().putBoolean("global_sgsr_enabled", value).apply()
    var sgsrMode: String get() = prefs.getString("sgsr_mode", "Quality") ?: "Quality"; set(value) = prefs.edit().putString("sgsr_mode", value).apply()
    var sgsrSharpness: Float get() = prefs.getFloat("sgsr_sharpness", 0.5f); set(value) = prefs.edit().putFloat("sgsr_sharpness", value).apply()
    var reshadeProfile: String get() = prefs.getString("reshade_profile", "Native") ?: "Native"; set(value) = prefs.edit().putString("reshade_profile", value).apply()
    var showFpsOverlay: Boolean get() = prefs.getBoolean("fps_overlay", false); set(value) = prefs.edit().putBoolean("fps_overlay", value).apply()
    var currentForegroundApp: String get() = prefs.getString("current_foreground_app", "global") ?: "global"; set(value) = prefs.edit().putString("current_foreground_app", value).apply()

    // --- NOVA FUNÇÃO PARA O WATCHER SERVICE FUNCIONAR ---
    fun hasAppOverride(packageName: String): Boolean {
        return prefs.contains("override_${packageName}_tdp")
    }

    fun savePerAppConfig(packageName: String, tdp: Float, perfClock: Float, primeClock: Float, gpuClock: Float, fanMode: Int, reshade: String, sgsr: Boolean, sgsrMode: String, sgsrSharp: Float, lsfg: Boolean, lsfgMult: String, lsfgPacing: Boolean, lsfgPerf: Boolean) {
        prefs.edit().putFloat("override_${packageName}_tdp", tdp).putFloat("override_${packageName}_perf", perfClock).putFloat("override_${packageName}_prime", primeClock).putFloat("override_${packageName}_gpu", gpuClock).putInt("override_${packageName}_fan", fanMode).putString("override_${packageName}_reshade", reshade).putBoolean("override_${packageName}_sgsr", sgsr).putString("override_${packageName}_sgsrmode", sgsrMode).putFloat("override_${packageName}_sgsrsharp", sgsrSharp).putBoolean("override_${packageName}_lsfg", lsfg).putString("override_${packageName}_lsfg_mult", lsfgMult).putBoolean("override_${packageName}_lsfg_pacing", lsfgPacing).putBoolean("override_${packageName}_lsfg_perf", lsfgPerf).apply()
    }
    fun getPerAppTdp(pkg: String, def: Float) = prefs.getFloat("override_${pkg}_tdp", def)
    fun getPerAppPerfClock(pkg: String, def: Float) = prefs.getFloat("override_${pkg}_perf", def)
    fun getPerAppPrimeClock(pkg: String, def: Float) = prefs.getFloat("override_${pkg}_prime", def)
    fun getPerAppGpuClock(pkg: String, def: Float) = prefs.getFloat("override_${pkg}_gpu", def)
    fun getPerAppFanMode(pkg: String, def: Int) = prefs.getInt("override_${pkg}_fan", def)
    fun getPerAppReshade(pkg: String, def: String) = prefs.getString("override_${pkg}_reshade", def) ?: def
    fun getPerAppSgsr(pkg: String, def: Boolean) = prefs.getBoolean("override_${pkg}_sgsr", def)
    fun getPerAppSgsrMode(pkg: String, def: String) = prefs.getString("override_${pkg}_sgsrmode", def) ?: def
    fun getPerAppSgsrSharp(pkg: String, def: Float) = prefs.getFloat("override_${pkg}_sgsrsharp", def)
    fun getPerAppLsfg(pkg: String, def: Boolean) = prefs.getBoolean("override_${pkg}_lsfg", def)
    fun getPerAppLsfgMult(pkg: String, def: String) = prefs.getString("override_${pkg}_lsfg_mult", def) ?: def
    fun getPerAppLsfgPacing(pkg: String, def: Boolean) = prefs.getBoolean("override_${pkg}_lsfg_pacing", def)
    fun getPerAppLsfgPerf(pkg: String, def: Boolean) = prefs.getBoolean("override_${pkg}_lsfg_perf", def)

    fun saveCustomProfile(name: String, type: String, val1: Float, val2: Float, val3: Float, val4: Float) {
        prefs.edit().putString("custom_profile_${name}_type", type).putFloat("custom_profile_${name}_v1", val1).putFloat("custom_profile_${name}_v2", val2).putFloat("custom_profile_${name}_v3", val3).putFloat("custom_profile_${name}_v4", val4).apply()
    }

    fun getAllCustomProfiles(): List<CustomProfile> {
        val profiles = mutableListOf<CustomProfile>()
        prefs.all.keys.filter { it.startsWith("custom_profile_") && it.endsWith("_type") }.forEach { key ->
            val name = key.replace("custom_profile_", "").replace("_type", "")
            profiles.add(CustomProfile(name, prefs.getString(key, "TDP") ?: "TDP", prefs.getFloat("custom_profile_${name}_v1", 0f), prefs.getFloat("custom_profile_${name}_v2", 0f), prefs.getFloat("custom_profile_${name}_v3", 0f), prefs.getFloat("custom_profile_${name}_v4", 0f)))
        }
        return profiles
    }
}

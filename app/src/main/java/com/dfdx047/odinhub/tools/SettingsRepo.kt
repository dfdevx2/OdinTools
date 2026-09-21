package com.dfdx047.odinhub.tools

import android.content.ComponentName
import com.dfdx047.odinhub.BuildConfig
import com.dfdx047.odinhub.service.ForegroundAppWatcherService
import javax.inject.Inject

class SettingsRepo @Inject constructor(
    private val executor: ShellExecutor,
) {

    fun applyRequiredSettings() {
        enableA11yService()
        grantAllAppsPermission()
        // Don't add to whitelist on debug builds, otherwise even Android Studio can't kill the app
        if (!BuildConfig.DEBUG) {
            addOdinHubToWhitelist()
        }
    }

    /**
     * Ativa o serviço de acessibilidade que deteta o jogo em primeiro plano.
     *
     * DOIS BUGS CORRIGIDOS AQUI — juntos, explicam porque as regras por jogo e o puxador do
     * overlay nunca funcionavam de forma fiável:
     *
     * 1. NOME DO COMPONENTE ERRADO. A string era montada como
     *    `$PACKAGE/$PACKAGE.service.ForegroundAppWatcherService`, com `PACKAGE` =
     *    `BuildConfig.APPLICATION_ID`. Só que o código vivia noutro pacote Java (o do projeto
     *    base do qual esta app derivou), diferente do `applicationId` — ou seja, o componente
     *    indicado simplesmente NÃO EXISTIA e o Android descartava a escrita em silêncio. Agora o
     *    nome é obtido da própria classe ([ComponentName]), por isso passa a estar correto por
     *    construção e sobrevive a qualquer renomeação futura.
     *
     * 2. FALTAVA LIGAR O INTERRUPTOR GERAL. Escrever `enabled_accessibility_services` não chega:
     *    o Android só arranca os serviços da lista se `accessibility_enabled` também estiver a 1.
     *    Sem isso, mesmo com o nome certo o serviço ficava listado mas nunca corria.
     */
    private fun enableA11yService() {
        val component = ComponentName(PACKAGE, ForegroundAppWatcherService::class.java.name)
            .flattenToString()

        val currentServices =
            executor.executeAsRoot("settings get secure $KEY_ACCESSIBILITY_SERVICES")
                .map { it ?: "" }
                .getOrDefault("")
                .let { if (it == "null") "" else it }

        if (!currentServices.contains(component)) {
            val updated = listOf(component, currentServices)
                .filter { it.isNotBlank() }
                .joinToString(":")
            executor.executeAsRoot("settings put secure $KEY_ACCESSIBILITY_SERVICES $updated")
        }

        // O interruptor geral. Sem isto, a lista acima é ignorada.
        executor.executeAsRoot("settings put secure $KEY_ACCESSIBILITY_ENABLED 1")
    }

    private fun grantAllAppsPermission() {
        executor.executeAsRoot("pm grant $PACKAGE android.permission.QUERY_ALL_PACKAGES")
    }

    private fun addOdinHubToWhitelist() {
        val currentWhitelist = whitelist
        if (currentWhitelist.contains(PACKAGE)) return
        val newWhitelist = "$PACKAGE,$currentWhitelist".trimEnd(',')
        whitelist = newWhitelist
    }

    fun setSfSaturation(value: Float) {
        executor.executeAsRoot("service call SurfaceFlinger 1022 f ${String.format("%.1f", value)}")
    }

    fun enableChargingSeparation() {
        isChargingSeparation = true
        restrictCurrent = 1000
        restrictCharge = true
    }

    fun disableChargingSeparation() {
        isChargingSeparation = false
        restrictCurrent = 1000000
        restrictCharge = false
    }

    fun chargingSeparationEnabled(): Boolean {
        return isChargingSeparation && restrictCharge && restrictCurrent == 1000
    }

    private var whitelist: String
        get() = executor.getStringSystemSetting(KEY_APP_WHITELIST, "")
        set(value) = executor.setStringSystemSetting(KEY_APP_WHITELIST, value)

    var preventPressHome: Boolean
        get() = executor.getBooleanSystemSetting(KEY_PREVENT_PRESS_HOME, true)
        set(value) = executor.setBooleanSystemSetting(KEY_PREVENT_PRESS_HOME, value)

    var vibrationEnabled: Boolean
        get() = executor.getBooleanSystemSetting(KEY_VIBRATE_ON, false)
        set(value) = executor.setBooleanSystemSetting(KEY_VIBRATE_ON, value)

    var vibrationStrength: Int
        get() = executor.getIntValue(KEY_VIBRATION_STRENGTH, 0)
        set(value) = executor.setIntValue(KEY_VIBRATION_STRENGTH, value)

    var isChargingSeparation: Boolean
        get() = executor.getBooleanSystemSetting(KEY_CHARGING_SEPARATION, false)
        set(value) = executor.setBooleanSystemSetting(KEY_CHARGING_SEPARATION, value)

    var restrictCharge: Boolean
        get() = executor.getBooleanValue(KEY_RESTRICT_CHARGE, false)
        set(value) = executor.setBooleanValue(KEY_RESTRICT_CHARGE, value)

    var restrictCurrent: Int
        get() = executor.getIntValue(KEY_RESTRICT_CURRENT, 0)
        set(value) = executor.setIntValue(KEY_RESTRICT_CURRENT, value)

    var chargingLimit80Enabled: Boolean
        get() = executor.getBooleanSystemSetting(KEY_CHARGING_LIMIT_80, false)
        set(value) = executor.setBooleanSystemSetting(KEY_CHARGING_LIMIT_80, value)

    var chargingLimit10Enabled: Boolean
        get() = executor.getBooleanSystemSetting(KEY_CHARGING_LIMIT_10, false)
        set(value) = executor.setBooleanSystemSetting(KEY_CHARGING_LIMIT_10, value)

    companion object {
        private const val PACKAGE = BuildConfig.APPLICATION_ID
        const val KEY_VENDOR_NAME = "ro.vendor.retro.name"
        const val KEY_BUILD_VERSION = "ro.build.odin2.ota.version"
        const val KEY_SATURATION = "persist.sys.sf.color_saturation"
        const val KEY_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
        const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        const val KEY_APP_WHITELIST = "app_whiteList"
        const val KEY_PREVENT_PRESS_HOME = "prevent_press_home_accidentally"
        const val KEY_VIBRATE_ON = "vibrate_on"
        const val KEY_CUSTOM_M1_VALUE = "remap_custom_to_m1_value"
        const val KEY_CUSTOM_M2_VALUE = "remap_custom_to_m2_value"
        const val KEY_VIBRATION_STRENGTH = "/d/haptics/user_vmax_mv"
        const val KEY_CHARGING_SEPARATION = "is_charging_separation"
        const val KEY_CHARGING_LIMIT_80 = "charging_limit_greater_than_80"
        const val KEY_CHARGING_LIMIT_10 = "charging_limit_less_than_10"
        const val KEY_RESTRICT_CHARGE = "/sys/class/qcom-battery/restrict_chg"
        const val KEY_RESTRICT_CURRENT = "/sys/class/qcom-battery/restrict_cur"
    }
}
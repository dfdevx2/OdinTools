package de.langerhans.odintools.tools

import de.langerhans.odintools.tools.DeviceType.*
import javax.inject.Inject

class DeviceUtils @Inject constructor(
    private val executor: ShellExecutor,
) {

    fun getDeviceVersion() = executor.getStringProperty(SettingsRepo.KEY_BUILD_VERSION, "")

    fun getDeviceCodename() = executor.getStringProperty(SettingsRepo.KEY_VENDOR_NAME, "")

    fun getDeviceType(): DeviceType {
        val codename = getDeviceCodename().uppercase()
        return when {
            codename.contains("Q9") || codename == "ODIN2" -> ODIN2
            codename.contains("MINI") -> ODIN2_MINI
            codename.contains("ODIN3") -> ODIN3
            codename.contains("PORTAL") -> ODIN_PORTAL
            codename.contains("THOR") -> AYN_THOR
            codename.contains("4.0") || codename.contains("RP4") -> RP4
            codename.contains("5.0") || codename.contains("RP5") -> RP5
            codename.contains("6.0") || codename.contains("RP6") -> RP6
            else -> OTHER
        }
    }
}

enum class DeviceType {
    ODIN2, ODIN2_MINI, ODIN3, ODIN_PORTAL, AYN_THOR, RP4, RP5, RP6, OTHER
}
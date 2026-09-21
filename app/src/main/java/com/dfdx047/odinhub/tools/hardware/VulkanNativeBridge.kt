package com.dfdx047.odinhub.tools.hardware

object VulkanNativeBridge {
    // Carrega a biblioteca C++ que acabou de ser compilada pelo CMake
    init {
        System.loadLibrary("OdinVulkanLayer")
    }

    // Declaração das funções externas ligadas ao C++
    external fun updateSgsrSettings(enabled: Boolean, mode: Int)
    external fun updateLsfgSettings(enabled: Boolean, multiplier: Int, framePacing: Boolean)
    external fun updateReshadeSettings(profileId: Int, saturation: Float, temperature: Float)

    // Funções utilitárias para traduzir textos da UI para números do C++
    fun applySgsr(enabled: Boolean, modeStr: String) {
        val modeInt = when (modeStr) {
            "Quality" -> 0
            "Balanced" -> 1
            "Performance" -> 2
            "Ultra" -> 3
            else -> 0
        }
        updateSgsrSettings(enabled, modeInt)
    }

    fun applyLsfg(enabled: Boolean, multiplierStr: String, framePacing: Boolean) {
        val multInt = multiplierStr.replace("x", "").toIntOrNull() ?: 2
        updateLsfgSettings(enabled, multInt, framePacing)
    }

    fun applyReshade(profileStr: String, saturation: Float, temperature: Float) {
        val profileId = when (profileStr) {
            "Native", "Nativo" -> 0
            "Vibrant", "Vibrante" -> 1
            "Cinema" -> 2
            "Retro", "Retrô" -> 3
            "HDR Boost" -> 4
            "Anime Edge" -> 20
            "Game Clarity" -> 10
            else -> 0
        }
        updateReshadeSettings(profileId, saturation, temperature)
    }
}
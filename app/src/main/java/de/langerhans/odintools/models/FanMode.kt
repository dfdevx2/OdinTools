package de.langerhans.odintools.models

import de.langerhans.odintools.tools.ShellExecutor

sealed class FanMode(
    val id: String,
    val settingsValue: Int,
) {
    // Mapeamento dos valores de sistema originais para os nossos novos perfis da UI
    data object Silent : FanMode("Silent (Silencioso)", 1)
    data object Smart : FanMode("Smart (Balanceado)", 4)
    data object Sport : FanMode("Sport (Desempenho Máximo)", 5)
    data object Stock : FanMode("Stock (Padrão)", -1) // -1 indica que devolveremos o controle ao sistema padrão
    data object Unknown : FanMode("unknown", -1)

    fun enable(executor: ShellExecutor) {
        // Envia o valor exato para o sistema (se não for Stock/Unknown)
        if (this != Unknown && this != Stock) {
            executor.setIntSystemSetting(FAN_MODE, settingsValue)
        }
    }

    companion object {
        const val FAN_MODE = "fan_mode"

        // Função para converter o texto clicado na tela de volta para um comando do hardware
        fun fromString(profileName: String): FanMode {
            return when {
                profileName.contains("Silent", ignoreCase = true) -> Silent
                profileName.contains("Smart", ignoreCase = true) -> Smart
                profileName.contains("Sport", ignoreCase = true) -> Sport
                profileName.contains("Stock", ignoreCase = true) -> Stock
                else -> Unknown
            }
        }
    }
}

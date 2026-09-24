package com.dfdx047.odinhub.models

/**
 * Rótulos de exibição dos presets, partilhados pelo overlay em jogo e pelo ecrã
 * "Per-App Overrides".
 *
 * Os valores numéricos (`tdpWatts`, `perfClockKHz`, ...) continuam a ser a única fonte de verdade
 * do que é aplicado ao hardware; isto é só o nome bonito que aparece na lista de jogos
 * (ver AppOverrideMapper) e nos subtítulos dos cartões.
 *
 * Existe porque o bug original da Parte 2 da auditoria era precisamente perfis com o mesmo nome a
 * significarem coisas diferentes em cada ecrã. Com os rótulos calculados aqui, uma regra criada
 * pelo overlay e uma criada pelo ecrã de overrides descrevem os mesmos watts/clocks da mesma
 * maneira -- e uma regra criada em jogo deixa de aparecer sem subtítulo na lista (antes, o overlay
 * gravava `tdpProfile = null` e a lista mostrava um espaço vazio).
 */
object AppOverrideLabels {

    /**
     * Rótulo do limite de TDP guardado numa regra por jogo.
     *
     * Os watts vêm SEMPRE de [TdpProfiles] (a única fonte de verdade dos perfis). Um valor abaixo
     * do mínimo do slider é a sentinela de "Stock (sem limite)" -- ver
     * [TdpProfiles.STOCK_SENTINEL_WATTS] -- e devolve exatamente "Stock", que é o que o
     * `AppOverrideMapper` usa para esconder o campo do subtítulo: "sem limite" e "sem regra"
     * são, para a lista de jogos, a mesma coisa. Qualquer outro valor inclui os watts, para que
     * um perfil e um valor livre com os mesmos watts se descrevam da mesma maneira em todos os
     * ecrãs.
     */
    fun tdpLabel(watts: Float): String {
        if (TdpProfiles.isStockWatts(watts)) return "Stock"
        val named = TdpProfiles.byId(TdpProfiles.idForWatts(watts))
        val formatted = TdpProfiles.formatWatts(watts)
        return if (named != null) "${named.labelEn} ($formatted)" else "Custom ($formatted)"
    }

    /**
     * Clocks manuais: os perfis combinados de clocks deixaram de existir (só há perfis de TDP),
     * por isso o rótulo é sempre os valores numéricos reais. Nunca devolve exatamente "Stock".
     */
    fun clockLabel(perfMHz: Float, primeMHz: Float): String =
        "${perfMHz.toInt()}/${primeMHz.toInt()} MHz"

    fun fanLabel(settingsValue: Int): String = FanMode.fromSettingsValue(settingsValue).shortLabel
}

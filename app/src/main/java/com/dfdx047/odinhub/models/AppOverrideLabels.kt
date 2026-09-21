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

    /** Os MESMOS watts usados pelo overlay, pelo ecrã global de Settings e pelo de overrides. */
    val namedTdpProfiles: List<Pair<String, Float>> = listOf(
        "Power Save" to 5f,
        "Balanced" to 10f,
        "Triple A" to 15f,
        "Stock" to 25f,
    )

    /**
     * O rótulo inclui SEMPRE os watts, e nunca é apenas "Stock".
     *
     * BUG QUE ISTO EVITA: o `AppOverrideMapper` usa a string literal "Stock" como sentinela de
     * "este jogo não tem limite personalizado" e esconde-a do subtítulo da lista. Como o preset de
     * 25 W também se chama "Stock", uma regra real de 25 W ficava indistinguível de "sem regra" e
     * a lista mostrava "Sem limites customizados (Stock)" para um jogo que tinha, de facto, um
     * limite configurado. Com "Stock (25W)" a colisão desaparece.
     */
    fun tdpLabel(watts: Float): String {
        val named = namedTdpProfiles.firstOrNull { it.second == watts }?.first
        return if (named != null) "$named (${watts.toInt()}W)" else "Custom (${watts.toInt()}W)"
    }

    /** Idem: nunca devolve exatamente "Stock", pela mesma razão explicada em [tdpLabel]. */
    fun clockLabel(perfMHz: Float, primeMHz: Float): String {
        val named = CombinedClockProfiles.all
            .firstOrNull { it.perfClockMHz == perfMHz && it.primeClockMHz == primeMHz }
            ?.label
        return named ?: "Custom (${perfMHz.toInt()}/${primeMHz.toInt()} MHz)"
    }

    fun fanLabel(settingsValue: Int): String = FanMode.fromSettingsValue(settingsValue).shortLabel
}

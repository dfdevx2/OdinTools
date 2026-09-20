package de.langerhans.odintools.models

/**
 * Perfis de "ReShade" (pós-processamento) expostos na UI, com o `effectId` REAL que
 * `window_postfx.frag` espera para cada um.
 *
 * O `window_postfx.frag` já tem ~20 efeitos escritos (Vibrance, Filmic, Game Clarity, Retro CRT,
 * Arcade, Color Boost, etc.) -- não faz sentido expor todos de uma vez na UI, então curámos um
 * subconjunto variado e com nomes claros para o utilizador (o resto continua no shader, disponível
 * para adicionar mais tarde só acrescentando uma linha aqui).
 *
 * IMPORTANTE (bug corrigido): o mapeamento anterior em `VulkanNativeBridge.applyReshade` estava
 * errado -- "Vibrant" apontava para o effectId 1 (que no shader é DLS, não Vibrance/5), "Cinema"
 * apontava para 2 (CRT, não Cinematic/11), "Retro" apontava para 3 (HDR, não um perfil retro) e
 * "HDR Boost" apontava para 4 (Natural, não HDR/3). Isto só nunca foi notado porque a camada
 * nunca chegou a correr de verdade -- agora que corre, os nomes têm de bater certo com os
 * `effectId` reais do shader.
 */
data class ReshadeProfile(val label: String, val effectId: Int)

object ReshadeProfiles {
    val all: List<ReshadeProfile> = listOf(
        ReshadeProfile("Nativo", 0),
        ReshadeProfile("Vibrante", 5),
        ReshadeProfile("Cinemático", 11),
        ReshadeProfile("HDR Boost", 3),
        ReshadeProfile("Retro CRT", 17),
        ReshadeProfile("Game Clarity", 10),
        ReshadeProfile("Arcade", 16),
        ReshadeProfile("Anime Edge", 20),
        ReshadeProfile("Competitivo", 13),
        ReshadeProfile("Color Boost", 21),
    )

    val labels: List<String> = all.map { it.label }

    fun effectIdFor(label: String): Int = all.firstOrNull { it.label == label }?.effectId
        // Nomes antigos ainda podem estar gravados no Room de uma versão anterior desta app --
        // mapeamos os que existiam antes para o perfil curado mais próximo, em vez de cair
        // silenciosamente para "Nativo" e o utilizador achar que o perfil que tinha escolhido
        // desapareceu.
        ?: when (label) {
            "Native" -> 0
            "Vibrant" -> 5
            "Cinema", "Cinematic" -> 11
            "Retro", "Retrô" -> 17
            else -> 0
        }
}

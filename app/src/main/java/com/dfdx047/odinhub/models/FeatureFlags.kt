package com.dfdx047.odinhub.models

/**
 * Interruptores de compilação para as partes mais arriscadas da app. Mudar um destes valores e
 * recompilar desliga a funcionalidade inteira sem ter de apagar código.
 *
 * - [GRAPHICS_ENGINE_AVAILABLE]: os controlos de SGSR / ReShade / Frame Generation. Continuam
 *   escondidos: ainda não existe um motor gráfico que atue de facto dentro dos jogos, e mostrar
 *   interruptores que não fazem nada só confundia. Os valores já gravados no Room são mantidos.
 * - [THERMAL_WRITES_ENABLED]: escritas nos trip points / modo das zonas térmicas (via PServerBinder). Se o
 *   aparelho se comportar mal, pôr a `false` torna o cartão "Limite Térmico" só de leitura.
 * - [IGNORE_TRANSIENT_WINDOWS]: o overlay ignora a cortina de notificações, heads-up, teclado e
 *   diálogos do sistema (não os trata como "saiu do jogo"). `false` volta ao comportamento antigo.
 */
object FeatureFlags {
    const val GRAPHICS_ENGINE_AVAILABLE = false
    const val THERMAL_WRITES_ENABLED = true
    const val IGNORE_TRANSIENT_WINDOWS = true
}

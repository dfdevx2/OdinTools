package com.dfdx047.odinhub.models

/**
 * Presets de clock discretos por cluster, ao estilo ClusterTune, em vez de sliders MHz contínuos.
 *
 * PORQUÊ: o Snapdragon 8 Elite (SM8750) não aceita qualquer valor de `scaling_max_freq` -- o
 * governor só reconhece as frequências (OPPs) presentes na tabela do kernel para cada política.
 * Um slider contínuo deixa o utilizador escolher, por exemplo, "3217 MHz", um valor que não existe
 * em nenhuma tabela e que o kernel tipicamente rejeita ou arredonda de forma imprevisível -- o que
 * bate certo com o bug reportado ("os clocks não fazem efeito nenhum, é a mesma coisa que mexer em
 * nada"). Ao restringir a UI a um pequeno conjunto de degraus com valores plausíveis e
 * intencionalmente conservadores (múltiplos redondos de 100 MHz, dentro do intervalo real do
 * dispositivo), aumentamos muito a chance de cada escrita ser aceite -- e, tal como o Pulse/
 * ClusterTune, mantemos [PerformanceManager.applyWriteOp] a confirmar por leitura de volta se um
 * degrau específico não for aceite nalgum firmware, em vez de assumir sucesso às cegas.
 *
 * GPU é agora **sempre** um controlo independente (ver [gpuPresets]): os perfis combinados
 * ([CombinedClockProfile]) deliberadamente não tocam na GPU. Motivo (palavras do utilizador):
 * "a GPU é o menor dos problemas do 8 Elite, o problema maior é o núcleo Prime" -- por isso os
 * perfis Power Save/Balanced/Triple A só reduzem Cluster 0 (Perf) e Cluster 1 (Prime); a GPU fica
 * sempre no máximo por defeito e só desce se o utilizador mexer nela manualmente.
 */
data class ClockPreset(
    val label: String,
    val labelEn: String,
    val clockMHz: Float,
)

object ClusterClockPresets {

    /** Cluster "Perf" (núcleos de performance, não-Prime). Máximo de fábrica: 3530 MHz. */
    val perfPresets: List<ClockPreset> = listOf(
        ClockPreset("Estável (Stock)", "Stock", 3530f),
        ClockPreset("Underclock Pequeno", "Small Underclock", 3200f),
        ClockPreset("Underclock Médio", "Medium Underclock", 2800f),
        ClockPreset("Underclock Grande", "Large Underclock", 2400f),
        ClockPreset("Underclock Extremo", "Extreme Underclock", 1735f),
    )

    /**
     * Cluster "Prime" (núcleos Prime -- segundo o utilizador, "o problema maior" do 8 Elite em
     * termos de consumo). Máximo de fábrica: 4320 MHz.
     */
    val primePresets: List<ClockPreset> = listOf(
        ClockPreset("Estável (Stock)", "Stock", 4320f),
        ClockPreset("Underclock Pequeno", "Small Underclock", 3900f),
        ClockPreset("Underclock Médio", "Medium Underclock", 3400f),
        ClockPreset("Underclock Grande", "Large Underclock", 2900f),
        ClockPreset("Underclock Extremo", "Extreme Underclock", 2246f),
    )

    /** GPU (Adreno), sempre um controlo à parte -- nunca mexido pelos perfis combinados abaixo. */
    val gpuPresets: List<ClockPreset> = listOf(
        ClockPreset("Máximo", "Max", 1100f),
        ClockPreset("Alto", "High", 800f),
        ClockPreset("Médio", "Medium", 500f),
        ClockPreset("Baixo", "Low", 300f),
        ClockPreset("Mínimo", "Min", 160f),
    )
}

/**
 * Perfil combinado (Power Save / Balanced / Triple A / Stock): mexe **só** em Perf + Prime.
 * A GPU não faz parte deste modelo de propósito -- ver o comentário em [ClusterClockPresets].
 */
data class CombinedClockProfile(
    val label: String,
    val labelEn: String,
    val perfClockMHz: Float,
    val primeClockMHz: Float,
)

object CombinedClockProfiles {
    val all: List<CombinedClockProfile> = listOf(
        CombinedClockProfile("Economia", "Power Save", perfClockMHz = 1735f, primeClockMHz = 2246f),
        CombinedClockProfile("Equilibrado", "Balanced", perfClockMHz = 2800f, primeClockMHz = 3400f),
        CombinedClockProfile("Triple A", "Triple A", perfClockMHz = 3200f, primeClockMHz = 3900f),
        CombinedClockProfile("Estável (Stock)", "Stock", perfClockMHz = 3530f, primeClockMHz = 4320f),
    )
}

package com.dfdx047.odinhub.models

import com.dfdx047.odinhub.tools.hardware.ClockTables

/**
 * Tabelas ESTÁTICAS de reserva das frequências por SoC, usadas só quando o `PerformanceManager`
 * não consegue ler a tabela de OPPs real do kernel (`scaling_available_frequencies` /
 * `gpu_available_frequencies`). Nesse caso `ClockTables.source == "fallback"`.
 *
 * PORQUÊ existir uma tabela: o Snapdragon 8 Elite (SM8750) não aceita qualquer valor de
 * `scaling_max_freq` -- o governor só reconhece as frequências (OPPs) presentes na tabela do
 * kernel. Os chips da UI mostram por isso SEMPRE valores numéricos reais ("3532 MHz"), nunca
 * nomes ao estilo ClusterTune ("Underclock Médio"), e por defeito vêm da tabela viva do kernel.
 * Os antigos perfis combinados de clocks (Power Save/Balanced/Triple A) foram removidos de
 * propósito: os únicos perfis que existem são os de TDP (ver [TdpProfiles]); os clocks são um
 * controlo manual para quem quer fixar valores específicos.
 *
 * As tabelas de reserva são as tabelas OPP reais de cada SoC (ver as fontes em cada uma). Se um
 * degrau não existir num firmware específico, o `PerformanceManager` encaixa-o no OPP suportado
 * mais próximo por baixo e confirma por leitura.
 */
object ClusterClockPresets {

    /**
     * Snapdragon 8 Elite / SM8750 (Odin 3). Tabelas OPP reais:
     *  - policy0 (6x Oryon "Phoenix-M", até 3532 MHz) e policy6 (2x "Phoenix-L", até 4320 MHz):
     *    dump de `time_in_state` de um aparelho SM8750 de produção; os máximos batem com o que o
     *    Odin 3 reporta (3532800 / 4320000 kHz).
     *  - GPU Adreno 830: nível de 1.1 GHz de `sun-v2-gpu-pwrlevels.dtsi` (o Odin 3 reporta
     *    1100000000 Hz como máximo).
     * Só são usadas se o kernel não deixar ler as tabelas vivas.
     */
    val SM8750: ClockTables = ClockTables(
        perfMHz = kHz(384000, 556800, 748800, 960000, 1152000, 1363200, 1555200, 1785600, 1996800, 2227200, 2400000, 2745600, 2918400, 3072000, 3321600, 3532800),
        primeMHz = kHz(1017600, 1209600, 1401600, 1689600, 1958400, 2246400, 2438400, 2649600, 2841600, 3072000, 3283200, 3513600, 3801600, 4089600, 4204800, 4320000),
        gpuMHz = listOf(160, 222, 342, 389, 443, 525, 607, 660, 734, 832, 900, 967, 1050, 1100),
        source = ClockTables.SOURCE_FALLBACK,
    )

    /**
     * Snapdragon 8 Gen 2 / SM8550 (Odin 2). Tabelas do cpufreq-hw (policy3 = 2x A715 + 2x A710,
     * policy7 = X3; o cluster de eficiência policy0 não é controlado pela app) e o nível de 680 MHz
     * da Adreno 740 (`kalama-v2-gpu-pwrlevels.dtsi`).
     */
    val SM8550: ClockTables = ClockTables(
        perfMHz = kHz(499200, 614400, 729600, 844800, 940800, 1056000, 1171200, 1286400, 1401600, 1536000, 1651200, 1785600, 1920000, 2054400, 2188800, 2323200, 2457600, 2592000, 2707200, 2803200),
        primeMHz = kHz(595200, 729600, 864000, 998400, 1132800, 1248000, 1363200, 1478400, 1593600, 1708800, 1843200, 1977600, 2092800, 2227200, 2342400, 2476800, 2592000, 2726400, 2841600, 2956800, 3187200),
        gpuMHz = listOf(124, 220, 295, 348, 401, 475, 550, 615, 680),
        source = ClockTables.SOURCE_FALLBACK,
    )

    private fun kHz(vararg values: Long): List<Int> = values.map { (it / 1000L).toInt() }.distinct().sorted()

    /**
     * Escolhe a tabela de reserva pelo SoC (`Build.SOC_MODEL` / `Build.HARDWARE`). Desconhecido
     * -> 8 Elite, que é o aparelho alvo do projeto.
     */
    fun fallbackFor(socModel: String?, hardware: String?): ClockTables {
        val key = listOfNotNull(socModel, hardware).joinToString(" ").uppercase()
        return when {
            key.contains("SM8550") || key.contains("KALAMA") -> SM8550
            key.contains("SM8750") || key.contains("SUN") -> SM8750
            else -> SM8750
        }
    }

    /** `count` valores inteiros igualmente espaçados entre `minMHz` e `maxMHz`, inclusive. */
    fun evenSteps(minMHz: Int, maxMHz: Int, count: Int = 10): List<Int> {
        if (count <= 1 || maxMHz <= minMHz) return listOf(maxMHz)
        return (0 until count).map { i ->
            minMHz + Math.round(i.toDouble() * (maxMHz - minMHz) / (count - 1)).toInt()
        }.distinct().sorted()
    }
}

package de.langerhans.odintools.tools.hardware

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceManager @Inject constructor() {

    // Inicializa o executor com Root por padrão (futuramente injetaremos o PServer aqui como fallback)
    private val executor: SysfsExecutor = RootExecutor()

    // Caminhos Sysfs para o Snapdragon 8 Elite (SM8750)
    private val SYSFS_CPU_PERF_MAX = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq" // 6x Perf Cores
    private val SYSFS_CPU_PRIME_MAX = "/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq" // 2x Prime Cores (No SD8 Gen 3/4 costuma ser policy4 ou 6)
    private val SYSFS_GPU_MAX = "/sys/class/kgsl/kgsl-3d0/max_gpuclk" // Adreno GPU

    // Limites de Frequência do SD8 Elite (Valores em KHz)
    private val PRIME_MAX_KHZ = 4320000 // 4.32 GHz
    private val PRIME_MIN_KHZ = 1000000 // 1.0 GHz

    private val PERF_MAX_KHZ = 3530000 // 3.53 GHz
    private val PERF_MIN_KHZ = 800000  // 800 MHz

    private val GPU_MAX_HZ = 1100000000L // 1.1 GHz
    private val GPU_MIN_HZ = 300000000L  // 300 MHz

    /**
     * Motor Linear de TDP (3W a 25W).
     * Aplica uma limitação estrita de clock baseada na porcentagem de energia liberada.
     */
    fun applyDynamicTdp(watts: Float) {
        val safeWatts = watts.coerceIn(3f, 25f)

        // Calcula o "peso" da performance (0.0 no 3W, 1.0 no 25W)
        val factor = (safeWatts - 3f) / (25f - 3f)

        // Trava matemática de Clocks absolutos baseados no fator de TDP
        val targetPrime = (PRIME_MIN_KHZ + (PRIME_MAX_KHZ - PRIME_MIN_KHZ) * factor).toLong()
        val targetPerf = (PERF_MIN_KHZ + (PERF_MAX_KHZ - PERF_MIN_KHZ) * factor).toLong()
        val targetGpu = (GPU_MIN_HZ + (GPU_MAX_HZ - GPU_MIN_HZ) * factor).toLong()

        applyAbsoluteClocks(perfClockKHz = targetPerf, primeClockKHz = targetPrime, gpuClockHz = targetGpu)
    }

    /**
     * Aplicação de Perfis Globais Otimizados
     */
    fun applyProfile(profile: String) {
        when (profile) {
            "Power Save" -> applyAbsoluteClocks(PERF_MIN_KHZ.toLong(), PRIME_MIN_KHZ.toLong(), GPU_MIN_HZ) // Equivalente a ~3W
            "Balanced" -> applyDynamicTdp(10f) // Trava em 10W - Foco em bateria e média performance
            "Smart" -> {
                // Aqui depois engataremos o algoritmo do Pulse que flutua o governor
                applyDynamicTdp(15f)
            }
            "Triple A" -> applyDynamicTdp(20f) // Modo pesado
            "Full" -> applyDynamicTdp(25f) // Desbloqueia tudo, ignorando termostato
        }
    }

    /**
     * Trava manual dura. Escreve diretamente no Sysfs.
     */
    fun applyAbsoluteClocks(perfClockKHz: Long, primeClockKHz: Long, gpuClockHz: Long) {
        if (!executor.isAvailable()) return

        // Trava Cluster 0 (Performance)
        executor.write(SYSFS_CPU_PERF_MAX, perfClockKHz.toString())

        // Trava Cluster 1 (Prime)
        executor.write(SYSFS_CPU_PRIME_MAX, primeClockKHz.toString())

        // Trava GPU
        executor.write(SYSFS_GPU_MAX, gpuClockHz.toString())
    }
}

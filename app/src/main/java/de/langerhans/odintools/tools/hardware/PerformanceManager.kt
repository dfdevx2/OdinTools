package de.langerhans.odintools.tools.hardware

import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PerformanceManager @Inject constructor() {

    private val executor: SysfsExecutor = RootExecutor()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoTdpJob: Job? = null

    // Caminhos Sysfs (Snapdragon 8 Elite SM8750)
    private val SYSFS_CPU_PERF_MAX = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private val SYSFS_CPU_PRIME_MAX = "/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq"
    private val SYSFS_GPU_MAX = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"

    // Limites de Frequência (KHz) Absolutos do Chip
    private val PRIME_MAX_KHZ = 4320000L
    private val PRIME_MIN_KHZ = 1000000L
    private val PERF_MAX_KHZ = 3530000L
    private val PERF_MIN_KHZ = 800000L
    private val GPU_MAX_HZ = 1100000000L
    private val GPU_MIN_HZ = 300000000L

    // Rastreio atual dos clocks do loop
    private var currentPerfClock = PERF_MAX_KHZ
    private var currentPrimeClock = PRIME_MAX_KHZ
    private var currentGpuClock = GPU_MAX_HZ

    /**
     * O Verdadeiro Daemon de AutoTDP.
     * Fica em loop infinito lendo a bateria e subindo/descendo os clocks.
     */
    fun applyDynamicTdp(targetWatts: Float) {
        autoTdpJob?.cancel() // Mata o loop anterior

        // Se pedir o máximo, libera geral as comportas
        if (targetWatts >= 25f) {
            applyAbsoluteClocks(PERF_MAX_KHZ, PRIME_MAX_KHZ, GPU_MAX_HZ)
            return
        }

        // Começa com metade do clock para o ajuste ser suave
        currentPerfClock = PERF_MAX_KHZ / 2
        currentPrimeClock = PRIME_MAX_KHZ / 2
        currentGpuClock = GPU_MAX_HZ / 2

        autoTdpJob = scope.launch {
            while (isActive) {
                val currentPowerDraw = getRealTimePowerDrawWatts()

                // Margem de tolerância (0.5W) para o emulador não ficar com engasgos (stuttering)
                if (currentPowerDraw > targetWatts + 0.5f) {
                    // CORTA A ENERGIA (Desce os clocks em 5%)
                    currentPerfClock = (currentPerfClock * 0.95).toLong().coerceAtLeast(PERF_MIN_KHZ)
                    currentPrimeClock = (currentPrimeClock * 0.95).toLong().coerceAtLeast(PRIME_MIN_KHZ)
                    currentGpuClock = (currentGpuClock * 0.95).toLong().coerceAtLeast(GPU_MIN_HZ)

                    applyAbsoluteClocks(currentPerfClock, currentPrimeClock, currentGpuClock, fromLoop = true)

                } else if (currentPowerDraw < targetWatts - 0.5f) {
                    // FOLGA DE ENERGIA (Sobe os clocks em 5% pra garantir FPS)
                    currentPerfClock = (currentPerfClock * 1.05).toLong().coerceAtMost(PERF_MAX_KHZ)
                    currentPrimeClock = (currentPrimeClock * 1.05).toLong().coerceAtMost(PRIME_MAX_KHZ)
                    currentGpuClock = (currentGpuClock * 1.05).toLong().coerceAtMost(GPU_MAX_HZ)

                    applyAbsoluteClocks(currentPerfClock, currentPrimeClock, currentGpuClock, fromLoop = true)
                }

                // Velocidade de reação do TDP: Lê a bateria a cada 1 segundo
                delay(1000)
            }
        }
    }

    fun applyProfile(profile: String) {
        when (profile) {
            "Power Save" -> applyDynamicTdp(5f)
            "Balanced" -> applyDynamicTdp(10f)
            "Smart" -> applyDynamicTdp(15f)
            "Triple A" -> applyDynamicTdp(20f)
            "Full" -> applyDynamicTdp(25f)
        }
    }

    /**
     * Trava dura de clocks manuais
     */
    fun applyAbsoluteClocks(perfClockKHz: Long, primeClockKHz: Long, gpuClockHz: Long, fromLoop: Boolean = false) {
        // Se a chamada veio da interface (slider manual), o usuário quer matar o AutoTDP
        if (!fromLoop) autoTdpJob?.cancel()

        if (!executor.isAvailable()) return

        executor.write(SYSFS_CPU_PERF_MAX, perfClockKHz.toString())
        executor.write(SYSFS_CPU_PRIME_MAX, primeClockKHz.toString())
        executor.write(SYSFS_GPU_MAX, gpuClockHz.toString())
    }

    /**
     * Lê a API padrão de bateria do Kernel Linux (V x A = W)
     */
    private fun getRealTimePowerDrawWatts(): Float {
        return try {
            val currentUaStr = File("/sys/class/power_supply/battery/current_now").readText().trim()
            val voltageUvStr = File("/sys/class/power_supply/battery/voltage_now").readText().trim()

            // Converte microAmperes e microVolts para Amperes e Volts reais
            val amps = abs(currentUaStr.toFloat() / 1_000_000f)
            val volts = voltageUvStr.toFloat() / 1_000_000f

            amps * volts
        } catch (e: Exception) {
            Log.e("OdinHub_AutoTDP", "Falha ao ler bateria, retornando 0", e)
            0f
        }
    }
}

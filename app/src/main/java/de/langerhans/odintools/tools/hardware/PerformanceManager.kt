package de.langerhans.odintools.tools.hardware

import android.util.Log
import de.langerhans.odintools.tools.ShellExecutor
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PerformanceManager @Inject constructor(
    private val executor: ShellExecutor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var hardwareJob: Job? = null

    // Caminhos Sysfs (Snapdragon 8 Elite SM8750)
    private val SYSFS_CPU_PERF_MAX = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private val SYSFS_CPU_PRIME_MAX = "/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq"
    private val SYSFS_GPU_MAX = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"

    // Limites de Frequência Absolutos Reais do Chip
    private val PRIME_MAX_KHZ = 4320000L
    private val PRIME_MIN_KHZ = 2246000L
    private val PERF_MAX_KHZ = 3530000L
    private val PERF_MIN_KHZ = 1735000L
    private val GPU_MAX_HZ = 1100000000L
    private val GPU_MIN_HZ = 160000000L

    // Rastreio dos Clocks e Estados
    private var targetPerf = PERF_MAX_KHZ
    private var targetPrime = PRIME_MAX_KHZ
    private var targetGpu = GPU_MAX_HZ

    private var isAutoTdp = false
    private var targetWatts = 15f

    // Controlado pela Interface (Se ativado, desliga o overhead de loop para clocks manuais)
    var isKsuModuleActive = false

    init {
        startHardwareDaemon()
    }

    private fun startHardwareDaemon() {
        hardwareJob?.cancel()
        hardwareJob = scope.launch {
            while (isActive) {
                if (isAutoTdp) {
                    // AutoTDP SEMPRE precisa rodar o loop para ler a bateria e ajustar o clock dinamicamente
                    calculateAutoTdpStep()
                    writeLimitsToSysfs()
                } else if (!isKsuModuleActive) {
                    // MODO PULSE (SEM ROOT): Clock fixo, mas precisa reescrever a cada 1s pra vencer o daemon da AYN
                    writeLimitsToSysfs()
                }
                // Se isAutoTdp for falso E isKsuModuleActive for verdadeiro, o loop descansa e não gasta CPU!

                delay(1000)
            }
        }
    }

    private fun calculateAutoTdpStep() {
        val currentPowerDraw = getRealTimePowerDrawWatts()
        if (currentPowerDraw <= 0f) return

        if (currentPowerDraw > targetWatts + 0.5f) {
            targetPerf = (targetPerf * 0.95).toLong().coerceAtLeast(PERF_MIN_KHZ)
            targetPrime = (targetPrime * 0.95).toLong().coerceAtLeast(PRIME_MIN_KHZ)
            targetGpu = (targetGpu * 0.95).toLong().coerceAtLeast(GPU_MIN_HZ)
        } else if (currentPowerDraw < targetWatts - 0.5f) {
            targetPerf = (targetPerf * 1.05).toLong().coerceAtMost(PERF_MAX_KHZ)
            targetPrime = (targetPrime * 1.05).toLong().coerceAtMost(PRIME_MAX_KHZ)
            targetGpu = (targetGpu * 1.05).toLong().coerceAtMost(GPU_MAX_HZ)
        }
    }

    private fun writeLimitsToSysfs() {
        if (!executor.pServerAvailable) return

        executor.executeAsRoot("echo $targetPerf > $SYSFS_CPU_PERF_MAX")
        executor.executeAsRoot("echo $targetPrime > $SYSFS_CPU_PRIME_MAX")
        executor.executeAsRoot("echo $targetGpu > $SYSFS_GPU_MAX")
    }

    fun applyDynamicTdp(watts: Float) {
        isAutoTdp = true
        targetWatts = watts.coerceIn(3f, 25f)
        targetPerf = PERF_MAX_KHZ / 2
        targetPrime = PRIME_MAX_KHZ / 2
        targetGpu = GPU_MAX_HZ / 2
    }

    fun applyAbsoluteClocks(perfClockKHz: Long, primeClockKHz: Long, gpuClockHz: Long) {
        isAutoTdp = false
        targetPerf = perfClockKHz
        targetPrime = primeClockKHz
        targetGpu = gpuClockHz
        // Dispara uma vez na hora pra aplicar
        writeLimitsToSysfs()
    }

    private fun getRealTimePowerDrawWatts(): Float {
        return try {
            val currentUaStr = File("/sys/class/power_supply/battery/current_now").readText().trim()
            val voltageUvStr = File("/sys/class/power_supply/battery/voltage_now").readText().trim()
            val amps = abs(currentUaStr.toFloat() / 1_000_000f)
            val volts = voltageUvStr.toFloat() / 1_000_000f
            amps * volts
        } catch (e: Exception) {
            0f
        }
    }
}

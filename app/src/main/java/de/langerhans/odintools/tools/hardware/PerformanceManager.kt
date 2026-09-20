package de.langerhans.odintools.tools.hardware

import de.langerhans.odintools.models.FanMode
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

    // Caminhos Sysfs (Snapdragon / Odin)
    private val SYSFS_CPU_PERF_MAX = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private val SYSFS_CPU_PRIME_MAX = "/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq"
    private val SYSFS_GPU_MAX = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"

    private val PRIME_MAX_KHZ = 4320000L
    private val PRIME_MIN_KHZ = 2246000L
    private val PERF_MAX_KHZ = 3530000L
    private val PERF_MIN_KHZ = 1735000L
    private val GPU_MAX_HZ = 1100000000L
    private val GPU_MIN_HZ = 160000000L

    private var targetPerf = PERF_MAX_KHZ
    private var targetPrime = PRIME_MAX_KHZ
    private var targetGpu = GPU_MAX_HZ

    private var isAutoTdp = false
    private var targetWatts = 15f

    init {
        startHardwareDaemon()
    }

    private fun startHardwareDaemon() {
        hardwareJob?.cancel()
        hardwareJob = scope.launch {
            while (isActive) {
                if (isAutoTdp) {
                    calculateAutoTdpStep()
                    writeLimitsToSysfsAtomic()
                } else {
                    writeLimitsToSysfsAtomic()
                }
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

    /**
     * ESTRATÉGIA DO PULSE: Escreve todos os comandos em formato de script shell unificado.
     * Isto garante que o desbloqueio (666), escrita (echo) e bloqueio de segurança (444)
     * ocorram na mesma sessão atômica do KernelSU / pservbinder, evitando rejeição do kernel.
     */
    private fun writeLimitsToSysfsAtomic() {
        val script = buildString {
            appendLine("#!/system/bin/sh")
            // Perf Cores (Policy 0)
            appendLine("chmod 666 $SYSFS_CPU_PERF_MAX")
            appendLine("echo $targetPerf > $SYSFS_CPU_PERF_MAX")
            appendLine("chmod 444 $SYSFS_CPU_PERF_MAX")

            // Prime Cores (Policy 6)
            appendLine("chmod 666 $SYSFS_CPU_PRIME_MAX")
            appendLine("echo $targetPrime > $SYSFS_CPU_PRIME_MAX")
            appendLine("chmod 444 $SYSFS_CPU_PRIME_MAX")

            // Adreno GPU
            appendLine("chmod 666 $SYSFS_GPU_MAX")
            appendLine("echo $targetGpu > $SYSFS_GPU_MAX")
            appendLine("chmod 444 $SYSFS_GPU_MAX")
        }

        // Executa o script atômico através do executor root / pserverbinder existente no app
        executor.executeAsRoot(script)
    }

    fun applyDynamicTdp(watts: Float) {
        isAutoTdp = true
        targetWatts = watts.coerceIn(3f, 25f)
        targetPerf = PERF_MAX_KHZ / 2
        targetPrime = PRIME_MAX_KHZ / 2
        targetGpu = GPU_MAX_HZ / 2
        writeLimitsToSysfsAtomic()
    }

    fun applyAbsoluteClocks(perfClockKHz: Long, primeClockKHz: Long, gpuClockHz: Long) {
        isAutoTdp = false
        targetPerf = perfClockKHz.coerceIn(PERF_MIN_KHZ, PERF_MAX_KHZ)
        targetPrime = primeClockKHz.coerceIn(PRIME_MIN_KHZ, PRIME_MAX_KHZ)
        targetGpu = gpuClockHz.coerceIn(GPU_MIN_HZ, GPU_MAX_HZ)
        writeLimitsToSysfsAtomic()
    }

    fun applyFanMode(fanMode: FanMode) {
        fanMode.enable(executor)
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

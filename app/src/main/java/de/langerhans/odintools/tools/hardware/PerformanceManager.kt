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

    private val commandBuilder = PerformanceCommandBuilder()
    private val SYSFS_GPU_MAX = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"

    private val GPU_MAX_HZ = 1100000000L
    private val GPU_MIN_HZ = 160000000L

    private var targetPerfRatio = 1.0f
    private var targetPrimeRatio = 1.0f
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
                }
                writeLimitsToSysfs()
                delay(1000)
            }
        }
    }

    private fun calculateAutoTdpStep() {
        val currentPowerDraw = getRealTimePowerDrawWatts()
        if (currentPowerDraw <= 0f) return

        if (currentPowerDraw > targetWatts + 0.5f) {
            targetPerfRatio = (targetPerfRatio * 0.95f).coerceAtLeast(0.4f)
            targetPrimeRatio = (targetPrimeRatio * 0.95f).coerceAtLeast(0.4f)
            targetGpu = (targetGpu * 0.95).toLong().coerceAtLeast(GPU_MIN_HZ)
        } else if (currentPowerDraw < targetWatts - 0.5f) {
            targetPerfRatio = (targetPerfRatio * 1.05f).coerceAtMost(1.0f)
            targetPrimeRatio = (targetPrimeRatio * 1.05f).coerceAtMost(1.0f)
            targetGpu = (targetGpu * 1.05).toLong().coerceAtMost(GPU_MAX_HZ)
        }
    }

    /**
     * Varredura dinâmica de políticas idêntica à do Pulse para suportar qualquer variante de SoC do Odin.
     */
    private fun getCpuPolicies(): List<Pair<String, Long>> {
        val policies = mutableListOf<Pair<String, Long>>()
        try {
            val dir = File("/sys/devices/system/cpu/cpufreq")
            dir.listFiles()?.filter { it.isDirectory && it.name.startsWith("policy") }?.forEach { policyDir ->
                val maxFreqFile = File(policyDir, "cpuinfo_max_freq")
                val scalingMaxFile = File(policyDir, "scaling_max_freq")
                val targetFile = if (maxFreqFile.exists()) maxFreqFile else scalingMaxFile
                if (targetFile.exists()) {
                    val maxFreq = targetFile.readText().trim().toLongOrNull() ?: 3000000L
                    policies.add(policyDir.absolutePath to maxFreq)
                }
            }
        } catch (e: Exception) {
            // Ignora falhas de I/O
        }
        return policies.sortedBy { it.second } // Ordena do menor para o maior (Perf -> Prime)
    }

    private fun writeLimitsToSysfs() {
        val policies = getCpuPolicies()
        if (policies.isEmpty()) return

        val selectedValues = mutableMapOf<Int, Long>()
        policies.forEachIndexed { index, (_, maxFreq) ->
            val isPrime = index == policies.lastIndex
            val ratio = if (isPrime) targetPrimeRatio else targetPerfRatio
            selectedValues[index] = (maxFreq * ratio).toLong().coerceAtMost(maxFreq)
        }

        val gpuPath = if (File(SYSFS_GPU_MAX).exists()) SYSFS_GPU_MAX else null
        val script = commandBuilder.buildApplyScript(
            cpuPolicies = policies,
            selectedValues = selectedValues,
            isReset = false,
            gpuPath = gpuPath,
            gpuValue = targetGpu,
        )

        executor.executeAsRoot(script)
    }

    fun applyDynamicTdp(watts: Float) {
        isAutoTdp = true
        targetWatts = watts.coerceIn(3f, 25f)
        val ratio = (watts / 25f).coerceIn(0.3f, 1.0f)
        targetPerfRatio = ratio
        targetPrimeRatio = ratio
        targetGpu = (GPU_MAX_HZ * ratio).toLong().coerceAtLeast(GPU_MIN_HZ)
        writeLimitsToSysfs()
    }

    fun applyAbsoluteClocks(perfClockKHz: Long, primeClockKHz: Long, gpuClockHz: Long) {
        isAutoTdp = false
        val policies = getCpuPolicies()
        if (policies.isNotEmpty()) {
            val maxPerf = policies.getOrNull(policies.lastIndex - 1)?.second ?: 3530000L
            val maxPrime = policies.lastOrNull()?.second ?: 4320000L

            targetPerfRatio = (perfClockKHz.toFloat() / maxPerf.toFloat()).coerceIn(0.3f, 1.0f)
            targetPrimeRatio = (primeClockKHz.toFloat() / maxPrime.toFloat()).coerceIn(0.3f, 1.0f)
        }
        targetGpu = gpuClockHz.coerceIn(GPU_MIN_HZ, GPU_MAX_HZ)
        writeLimitsToSysfs()
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

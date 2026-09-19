package de.langerhans.odintools.tools.hardware

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.tools.ShellExecutor
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PerformanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShellExecutor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var hardwareJob: Job? = null

    // Caminhos Sysfs (Snapdragon 8 Elite)
    private val SYSFS_CPU_PERF_MAX = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private val SYSFS_CPU_PRIME_MAX = "/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq"
    private val SYSFS_GPU_MAX = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"

    // Limites de Frequência Absolutos Reais
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
    var isKsuModuleActive = false

    init {
        startHardwareDaemon()
    }

    private fun startHardwareDaemon() {
        hardwareJob?.cancel()
        hardwareJob = scope.launch {
            while (isActive) {
                if (isAutoTdp) {
                    calculateAutoTdpStep()
                    writeLimitsToSysfs()
                } else if (!isKsuModuleActive) {
                    writeLimitsToSysfs()
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

    private fun writeLimitsToSysfs() {
        if (!executor.pServerAvailable) return

        // O Segredo do Pulse: Script com trava 444
        val scriptContent = """
            #!/system/bin/sh
            chmod 666 $SYSFS_CPU_PERF_MAX
            echo $targetPerf > $SYSFS_CPU_PERF_MAX
            chmod 444 $SYSFS_CPU_PERF_MAX

            chmod 666 $SYSFS_CPU_PRIME_MAX
            echo $targetPrime > $SYSFS_CPU_PRIME_MAX
            chmod 444 $SYSFS_CPU_PRIME_MAX

            chmod 666 $SYSFS_GPU_MAX
            echo $targetGpu > $SYSFS_GPU_MAX
            chmod 444 $SYSFS_GPU_MAX
        """.trimIndent()

        runGeneratedScript("apply_clocks.sh", scriptContent)
    }

    private fun runGeneratedScript(scriptName: String, scriptContents: String) {
        try {
            val scriptDir = File(context.filesDir, "root-scripts")
            if (!scriptDir.exists()) scriptDir.mkdirs()

            val scriptFile = File(scriptDir, scriptName)
            scriptFile.writeText(scriptContents)
            scriptFile.setReadable(true, false)
            scriptFile.setExecutable(true, false)

            executor.executeAsRoot("sh ${scriptFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("OdinHub_AutoTDP", "Falha ao gerar/rodar script", e)
        }
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
        writeLimitsToSysfs()
    }

    private fun getRealTimePowerDrawWatts(): Float {
        return try {
            val currentUaStr = File("/sys/class/power_supply/battery/current_now").readText().trim()
            val voltageUvStr = File("/sys/class/power_supply/battery/voltage_now").readText().trim()
            val amps = abs(currentUaStr.toFloat() / 1_000_000f)
            val volts = voltageUvStr.toFloat() / 1_000_000f
            amps * volts
        } catch (e: Exception) { 0f }
    }
}

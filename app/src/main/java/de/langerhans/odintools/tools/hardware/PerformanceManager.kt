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
    // Aqui injetamos o motor nativo do OdinTools.
    // Ele usa o PServer da AYN por padrão (e cai pro Root se o PServer falhar).
    private val executor: ShellExecutor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var hardwareJob: Job? = null

    // Caminhos Sysfs (Snapdragon 8 Elite SM8750)
    private val SYSFS_CPU_PERF_MAX = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private val SYSFS_CPU_PRIME_MAX = "/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq"
    private val SYSFS_GPU_MAX = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"

    // Limites de Frequência Absolutos do Chip
    private val PRIME_MAX_KHZ = 4320000L
    private val PRIME_MIN_KHZ = 1000000L
    private val PERF_MAX_KHZ = 3530000L
    private val PERF_MIN_KHZ = 800000L
    private val GPU_MAX_HZ = 1100000000L
    private val GPU_MIN_HZ = 300000000L

    // Rastreio dos Clocks do Loop
    private var targetPerf = PERF_MAX_KHZ
    private var targetPrime = PRIME_MAX_KHZ
    private var targetGpu = GPU_MAX_HZ

    private var isAutoTdp = false
    private var targetWatts = 15f

    init {
        startHardwareDaemon()
    }

    /**
     * O SEGREDO REVELADO: O "Daemon de Batalha".
     * Fica em loop infinito para vencer o daemon nativo (game-boost) da AYN.
     */
    private fun startHardwareDaemon() {
        hardwareJob?.cancel()
        hardwareJob = scope.launch {
            while (isActive) {
                if (isAutoTdp) {
                    calculateAutoTdpStep()
                }

                // Re-assert: Reescreve os limites no PServer a cada 1 segundo
                writeLimitsToSysfs()

                delay(1000) // Poll Tick (Cadência de repetição)
            }
        }
    }

    /**
     * Lógica de AutoTDP que lê a bateria física e castiga os clocks dinamicamente
     */
    private fun calculateAutoTdpStep() {
        val currentPowerDraw = getRealTimePowerDrawWatts()
        if (currentPowerDraw <= 0f) return // Pula se o sensor falhar

        if (currentPowerDraw > targetWatts + 0.5f) {
            // Corta a energia descendo os clocks em 5%
            targetPerf = (targetPerf * 0.95).toLong().coerceAtLeast(PERF_MIN_KHZ)
            targetPrime = (targetPrime * 0.95).toLong().coerceAtLeast(PRIME_MIN_KHZ)
            targetGpu = (targetGpu * 0.95).toLong().coerceAtLeast(GPU_MIN_HZ)
        } else if (currentPowerDraw < targetWatts - 0.5f) {
            // Folga de energia, sobe os clocks em 5% pra ganhar FPS
            targetPerf = (targetPerf * 1.05).toLong().coerceAtMost(PERF_MAX_KHZ)
            targetPrime = (targetPrime * 1.05).toLong().coerceAtMost(PRIME_MAX_KHZ)
            targetGpu = (targetGpu * 1.05).toLong().coerceAtMost(GPU_MAX_HZ)
        }
    }

    private fun writeLimitsToSysfs() {
        if (!executor.pServerAvailable) {
            Log.e("OdinHub_Hardware", "PServerBinder Indisponível!")
            return
        }

        // Execução pelo PServer/ShellExecutor
        executor.executeAsRoot("chmod 644 $SYSFS_CPU_PERF_MAX && echo $targetPerf > $SYSFS_CPU_PERF_MAX")
        executor.executeAsRoot("chmod 644 $SYSFS_CPU_PRIME_MAX && echo $targetPrime > $SYSFS_CPU_PRIME_MAX")
        executor.executeAsRoot("chmod 644 $SYSFS_GPU_MAX && echo $targetGpu > $SYSFS_GPU_MAX")
    }

    fun applyDynamicTdp(watts: Float) {
        isAutoTdp = true
        targetWatts = watts.coerceIn(3f, 25f)
        // Começa com clocks moderados para o loop estabilizar a corrente
        targetPerf = PERF_MAX_KHZ / 2
        targetPrime = PRIME_MAX_KHZ / 2
        targetGpu = GPU_MAX_HZ / 2
    }

    fun applyAbsoluteClocks(perfClockKHz: Long, primeClockKHz: Long, gpuClockHz: Long) {
        isAutoTdp = false
        targetPerf = perfClockKHz
        targetPrime = primeClockKHz
        targetGpu = gpuClockHz
        writeLimitsToSysfs() // Aplica a trava dura imediatamente
    }

    fun applyProfile(profile: String) {
        when (profile) {
            "Power Save" -> applyDynamicTdp(5f)
            "Balanced" -> applyDynamicTdp(10f)
            "Smart" -> applyDynamicTdp(15f)
            "Triple A" -> applyDynamicTdp(20f)
            "Full" -> {
                isAutoTdp = false
                targetPerf = PERF_MAX_KHZ
                targetPrime = PRIME_MAX_KHZ
                targetGpu = GPU_MAX_HZ
                writeLimitsToSysfs()
            }
        }
    }

    /**
     * Leitura física do sensor de bateria (V x A = W)
     */
    private fun getRealTimePowerDrawWatts(): Float {
        return try {
            val currentUaStr = File("/sys/class/power_supply/battery/current_now").readText().trim()
            val voltageUvStr = File("/sys/class/power_supply/battery/voltage_now").readText().trim()

            val amps = abs(currentUaStr.toFloat() / 1_000_000f)
            val volts = voltageUvStr.toFloat() / 1_000_000f

            amps * volts
        } catch (e: Exception) {
            Log.e("OdinHub_AutoTDP", "Erro no sensor da bateria", e)
            0f
        }
    }
}

package com.dfdx047.odinhub.tools.hardware

import android.util.Log
import com.dfdx047.odinhub.models.FanMode
import com.dfdx047.odinhub.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Resultado da última tentativa de aplicar um [HardwareWriteOp] individual, para diagnóstico na UI. */
data class HardwareNodeStatus(
    val label: String,
    val path: String,
    val requestedValue: String,
    val appliedValue: String?,
    val ok: Boolean,
)

@Singleton
class PerformanceManager @Inject constructor(
    private val executor: ShellExecutor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var hardwareJob: Job? = null

    private val sysfsGpuMaxPath = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
    private val gpuMaxHz = 1_100_000_000L
    private val gpuMinHz = 160_000_000L

    private var targetPerfPercent = 1.0f
    private var targetPrimePercent = 1.0f
    private var targetGpu = gpuMaxHz

    private var isAutoTdp = false
    private var targetWatts = 15f

    /**
     * Estado da última passada de aplicação de limites, um item por nó de sysfs tocado.
     * Antes desta auditoria não existia nenhum sinal de erro entre [PerformanceManager] e a UI:
     * uma escrita podia falhar (kernel a rejeitar, permissão negada, PServer indisponível) e o
     * slider continuava a mostrar o valor pedido como se tivesse "colado". Ecrãs/ViewModels podem
     * agora observar este StateFlow para mostrar um aviso real ao utilizador.
     */
    private val _lastApplyStatus = MutableStateFlow<List<HardwareNodeStatus>>(emptyList())
    val lastApplyStatus: StateFlow<List<HardwareNodeStatus>> = _lastApplyStatus.asStateFlow()

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

        // Piso do TDP dinâmico: ver TDP_MIN_RATIO em PerformanceCommandBuilder para o porquê de
        // não usar CPU_MIN_RATIO aqui -- alvos baixos (ex: 5W) precisam de conseguir descer bem
        // abaixo dos 30% antigos para teres alguma hipótese de convergir de verdade.
        val minRatio = PerformanceCommandBuilder.TDP_MIN_RATIO

        // Passo proporcional ao erro (em vez de ±5% fixo): quanto mais longe do alvo, maior o
        // salto por tick, para convergir em segundos em vez de dezenas de ticks -- e para
        // conseguir mesmo sair da zona alta (100%) e chegar perto de pisos baixos como 12%.
        val errorRatio = ((currentPowerDraw - targetWatts) / targetWatts).coerceIn(-1f, 1f)
        val step = 1f + (-errorRatio * 0.12f).coerceIn(-0.20f, 0.20f)

        val outsideDeadband = kotlin.math.abs(currentPowerDraw - targetWatts) > 0.3f
        if (outsideDeadband) {
            targetPerfPercent = (targetPerfPercent * step).coerceIn(minRatio, PerformanceCommandBuilder.CPU_MAX_RATIO)
            targetPrimePercent = (targetPrimePercent * step).coerceIn(minRatio, PerformanceCommandBuilder.CPU_MAX_RATIO)
            targetGpu = (targetGpu * step).toLong().coerceIn(gpuMinHz, gpuMaxHz)
        }
    }

    /**
     * Varredura dinâmica de políticas (inspirada na arquitetura SysfsFileSystem do Pulse):
     * lê em tempo de execução todas as políticas de cpufreq disponíveis no kernel do dispositivo,
     * em vez de assumir caminhos fixos como `policy0`/`policy6` (que variam por SoC/firmware).
     */
    private fun getCpuPolicies(): List<CpuPolicyNode> {
        val policies = mutableListOf<CpuPolicyNode>()
        try {
            val dir = File("/sys/devices/system/cpu/cpufreq")
            dir.listFiles()?.filter { it.isDirectory && it.name.startsWith("policy") }?.forEach { policyDir ->
                val maxFreqFile = File(policyDir, "cpuinfo_max_freq")
                val scalingMaxFile = File(policyDir, "scaling_max_freq")
                val targetFile = if (maxFreqFile.exists()) maxFreqFile else scalingMaxFile
                if (targetFile.exists()) {
                    val maxFreq = targetFile.readText().trim().toLongOrNull() ?: 3_000_000L
                    policies.add(CpuPolicyNode(policyDir.absolutePath, maxFreq))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCpuPolicies: falha ao varrer cpufreq", e)
        }
        return policies
    }

    private fun writeLimitsToSysfs() {
        val policies = getCpuPolicies()
        if (policies.isEmpty()) {
            Log.w(TAG, "writeLimitsToSysfs: nenhuma política de cpufreq encontrada, nada a aplicar")
            return
        }

        val ops = PerformanceCommandBuilder.buildCpuFrequencyOps(
            policies = policies,
            perfRatio = targetPerfPercent,
            primeRatio = targetPrimePercent,
            // No modo TDP dinâmico o piso tem de ser o do AutoTDP (12%), senão alvos baixos como
            // 5W ficam presos nos 30% dos clocks manuais e o limite não se sente -- ver
            // PerformanceCommandBuilder.buildCpuFrequencyOps/TDP_MIN_RATIO.
            minRatio = if (isAutoTdp) {
                PerformanceCommandBuilder.TDP_MIN_RATIO
            } else {
                PerformanceCommandBuilder.CPU_MIN_RATIO
            },
        ) + if (File(sysfsGpuMaxPath).exists()) {
            listOf(PerformanceCommandBuilder.buildGpuFrequencyOp(sysfsGpuMaxPath, targetGpu))
        } else {
            emptyList()
        }

        // CAUSA RAIZ (auditoria): a versão anterior concatenava TODOS os nós (CPU perf, CPU prime,
        // GPU) numa única string com `&&`. Se qualquer nó no meio fosse rejeitado pelo kernel, o
        // `&&` abortava a cadeia e TODOS os nós seguintes ficavam sem ser escritos — mesmo sem
        // nenhuma relação com a falha. Aplicamos agora cada nó de forma independente, com
        // verificação e recuo, para que uma falha isolada (ex: GPU bloqueada pelo bootloader) não
        // impeça os clocks de CPU de serem aplicados, e vice-versa.
        val results = ops.map { op -> applyWriteOp(op) }
        _lastApplyStatus.update { results }

        results.filterNot { it.ok }.forEach {
            Log.w(TAG, "writeLimitsToSysfs: falha ao aplicar ${it.label} (${it.path}): pedido=${it.requestedValue} lido=${it.appliedValue}")
        }
    }

    /**
     * Aplica uma escrita atómica com resiliência de duas camadas:
     *  1. Tenta a cadeia completa `chmod 666 && echo && chmod 444` numa única transação binder
     *     (mais rápido, funciona quando o backend interpreta o comando com um shell real).
     *  2. Se a leitura de verificação mostrar que o valor não colou, recua para os três passos
     *     como transações binder separadas — cobre o caso de backends que não suportam operadores
     *     de shell (`&&`/`>`) num único argumento de comando.
     * Em ambos os casos, o valor é sempre lido de volta do sysfs para confirmar que realmente foi
     * aplicado, em vez de confiar cegamente no `Result.success` do executor.
     */
    private fun applyWriteOp(op: HardwareWriteOp, retries: Int = 1): HardwareNodeStatus {
        executor.executeAsRoot(op.asChainedCommand())
        var readBack = readSysfsValue(op.targetPath)
        if (readBack == op.value) {
            return HardwareNodeStatus(op.label, op.targetPath, op.value, readBack, ok = true)
        }

        repeat(retries + 1) {
            op.asSequentialCommands().forEach { executor.executeAsRoot(it) }
            readBack = readSysfsValue(op.targetPath)
            if (readBack == op.value) {
                return HardwareNodeStatus(op.label, op.targetPath, op.value, readBack, ok = true)
            }
        }
        return HardwareNodeStatus(op.label, op.targetPath, op.value, readBack, ok = false)
    }

    private fun readSysfsValue(path: String): String? {
        // Lê sempre via root (o nó pode estar de volta a 444/root-only depois do "lock").
        return executor.executeAsRoot("cat $path").getOrNull()?.trim()
    }

    fun applyDynamicTdp(watts: Float) {
        isAutoTdp = true
        targetWatts = watts.coerceIn(3f, 25f)
        val ratio = PerformanceCommandBuilder.wattsToRatio(targetWatts)
        targetPerfPercent = ratio
        targetPrimePercent = ratio
        targetGpu = (gpuMaxHz * ratio).toLong().coerceAtLeast(gpuMinHz)
        writeLimitsToSysfs()
    }

    fun applyAbsoluteClocks(perfClockKHz: Long, primeClockKHz: Long, gpuClockHz: Long) {
        isAutoTdp = false
        val policies = getCpuPolicies()
        val (perfRatio, primeRatio) = PerformanceCommandBuilder.absoluteClocksToRatios(
            perfClockKHz = perfClockKHz,
            primeClockKHz = primeClockKHz,
            policies = policies,
        )
        targetPerfPercent = perfRatio
        targetPrimePercent = primeRatio
        targetGpu = gpuClockHz.coerceIn(gpuMinHz, gpuMaxHz)
        writeLimitsToSysfs()
    }

    fun applyFanMode(fanMode: FanMode) {
        fanMode.enable(executor)
    }

    private fun readTextFile(path: String): String? {
        return try {
            File(path).readText().trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun getRealTimePowerDrawWatts(): Float {
        return try {
            val currentUaStr = readTextFile("/sys/class/power_supply/battery/current_now") ?: return 0f
            val voltageUvStr = readTextFile("/sys/class/power_supply/battery/voltage_now") ?: return 0f
            val amps = abs(currentUaStr.toFloat() / 1_000_000f)
            val volts = voltageUvStr.toFloat() / 1_000_000f
            amps * volts
        } catch (e: Exception) {
            0f
        }
    }

    private companion object {
        const val TAG = "PerformanceManager"
    }
}

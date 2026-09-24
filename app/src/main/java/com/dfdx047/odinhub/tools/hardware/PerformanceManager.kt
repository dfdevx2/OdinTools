package com.dfdx047.odinhub.tools.hardware

import android.util.Log
import com.dfdx047.odinhub.models.ClusterClockPresets
import com.dfdx047.odinhub.models.FanMode
import com.dfdx047.odinhub.models.TdpProfiles
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

    /**
     * Máximo/mínimo da GPU derivados da tabela REAL de frequências do kgsl quando ela é legível
     * (antes eram 1.1 GHz / 160 MHz fixos, valores do 8 Elite que estão errados no Odin 2). Se
     * nada for legível, cai na tabela estática por SoC ([clockTables] com source "fallback") e,
     * em último caso, nos valores do 8 Elite.
     */
    private val gpuMaxHz: Long
        get() = gpuAvailableFreqsHz.maxOrNull()
            ?: _clockTables.value.gpuMHz.maxOrNull()?.let { it * 1_000_000L }
            ?: DEFAULT_GPU_MAX_HZ
    private val gpuMinHz: Long
        get() = gpuAvailableFreqsHz.minOrNull()
            ?: _clockTables.value.gpuMHz.minOrNull()?.let { it * 1_000_000L }
            ?: DEFAULT_GPU_MIN_HZ

    private var targetPerfPercent = 1.0f
    private var targetPrimePercent = 1.0f
    // Arranca no máximo do 8 Elite; a escrita encaixa sempre no OPP suportado mais alto por
    // baixo (snapToSupported), por isso noutro SoC isto significa simplesmente "máximo".
    private var targetGpu = DEFAULT_GPU_MAX_HZ

    private var isAutoTdp = false
    private var targetWatts = 15f

    /**
     * Tabelas de frequências reais do SoC (OPPs do kernel), em MHz, para a UI desenhar chips com
     * valores que o hardware suporta mesmo. Preenchidas uma vez, em Dispatchers.IO, no arranque
     * (ver [loadClockTables]); até lá é [ClockTables.EMPTY].
     */
    private val _clockTables = MutableStateFlow(ClockTables.EMPTY)
    val clockTables: StateFlow<ClockTables> = _clockTables.asStateFlow()

    /**
     * Estado da última passada de aplicação de limites, um item por nó de sysfs tocado.
     * Antes desta auditoria não existia nenhum sinal de erro entre [PerformanceManager] e a UI:
     * uma escrita podia falhar (kernel a rejeitar, permissão negada, PServer indisponível) e o
     * slider continuava a mostrar o valor pedido como se tivesse "colado". Ecrãs/ViewModels podem
     * agora observar este StateFlow para mostrar um aviso real ao utilizador.
     */
    private val _lastApplyStatus = MutableStateFlow<List<HardwareNodeStatus>>(emptyList())
    val lastApplyStatus: StateFlow<List<HardwareNodeStatus>> = _lastApplyStatus.asStateFlow()

    /**
     * Frequências que a GPU Adreno aceita. Mesmo problema dos clusters de CPU: escrever um valor
     * arbitrário em `max_gpuclk` faz o driver assentar no OPP mais próximo, e a verificação por
     * leitura reportava isso como falha ("Não foi possível aplicar: gpu-max-clock").
     *
     * Declarado ANTES do `init` de propósito: o daemon e o carregamento das tabelas arrancam em
     * Dispatchers.IO a partir do `init`, e um delegate `lazy` declarado depois do `init` ainda
     * não estaria atribuído se a coroutine corresse antes de o construtor terminar.
     */
    private val gpuAvailableFreqsHz: List<Long> by lazy {
        listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies",
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
        ).asSequence()
            .map { readAvailableFreqs(File(it)) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    init {
        scope.launch { loadClockTables() }
        startHardwareDaemon()
    }

    /**
     * Lê as tabelas de OPPs: `scaling_available_frequencies` de cada política de cpufreq (a
     * política com maior `cpuinfo_max_freq` é o Prime; a seguinte é o Perf) e `gpu_available_frequencies` do kgsl. Estes nós são normalmente legíveis por
     * qualquer utilizador, sem root. Se nada for legível, usa a tabela estática por SoC de
     * `ClusterClockPresets` e marca `source = "fallback"`. Nunca lança: uma falha aqui só
     * significa chips de reserva na UI.
     */
    private fun loadClockTables() {
        val fallback = ClusterClockPresets.fallbackFor(
            socModel = runCatching { android.os.Build.SOC_MODEL }.getOrNull(),
            hardware = runCatching { android.os.Build.HARDWARE }.getOrNull(),
        )
        val loaded = runCatching {
            val sorted = getCpuPolicies().sortedBy { it.cpuinfoMaxFreqKHz }
            val prime = sorted.lastOrNull()?.availableFreqsKHz.orEmpty()
            // Perf = a política logo abaixo do Prime (a mesma que absoluteClocksToRatios usa como
            // 100%). Juntar TODAS as outras misturava, no 8 Gen 2, o cluster de eficiência
            // (até 2016 MHz) com o de performance (até 2803 MHz) numa só fila de chips.
            val perf = sorted.getOrNull(sorted.lastIndex - 1)?.availableFreqsKHz.orEmpty()
            ClockTables(
                perfMHz = ClockTableCuration.kHzToMHz(perf),
                primeMHz = ClockTableCuration.kHzToMHz(prime),
                gpuMHz = ClockTableCuration.hzToMHz(gpuAvailableFreqsHz),
                source = ClockTables.SOURCE_KERNEL,
            )
        }.getOrElse {
            Log.w(TAG, "loadClockTables: falha a ler as tabelas do kernel", it)
            ClockTables.EMPTY
        }

        // Cada tabela cai individualmente na reserva: um kernel pode expor as do CPU e não a da
        // GPU (ou vice-versa). `source` só fica "kernel" quando as três vieram do kernel.
        val perf = loaded.perfMHz.ifEmpty { fallback.perfMHz }
        val prime = loaded.primeMHz.ifEmpty { fallback.primeMHz }
        val gpu = loaded.gpuMHz.ifEmpty { fallback.gpuMHz }
        val allFromKernel = loaded.perfMHz.isNotEmpty() && loaded.primeMHz.isNotEmpty() && loaded.gpuMHz.isNotEmpty()
        _clockTables.value = ClockTables(
            perfMHz = perf,
            primeMHz = prime,
            gpuMHz = gpu,
            source = if (allFromKernel) ClockTables.SOURCE_KERNEL else ClockTables.SOURCE_FALLBACK,
        )
        Log.i(TAG, "loadClockTables: perf=${perf.size} prime=${prime.size} gpu=${gpu.size} source=${_clockTables.value.source}")
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
                    policies.add(
                        CpuPolicyNode(
                            policyPath = policyDir.absolutePath,
                            cpuinfoMaxFreqKHz = maxFreq,
                            availableFreqsKHz = readAvailableFreqs(File(policyDir, "scaling_available_frequencies")),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCpuPolicies: falha ao varrer cpufreq", e)
        }
        return policies
    }

    /**
     * Lê uma lista de frequências suportadas de um nó de sysfs (valores separados por espaços).
     * Devolve lista vazia se o nó não existir ou não for legível -- nesse caso não encaixamos nada
     * e mantemos o comportamento antigo.
     */
    private fun readAvailableFreqs(file: File): List<Long> = runCatching {
        if (!file.exists()) return emptyList()
        file.readText()
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { it.toLongOrNull() }
            .sorted()
    }.getOrElse {
        Log.w(TAG, "readAvailableFreqs: falha a ler ${file.path}", it)
        emptyList()
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
                // Clocks manuais / Stock: cada chip é um OPP real, por isso o piso é mínimo.
                PerformanceCommandBuilder.MANUAL_MIN_RATIO
            },
        ) + if (File(sysfsGpuMaxPath).exists()) {
            // Idem CPU: encaixa no OPP suportado mais próximo por baixo, para a verificação por
            // leitura deixar de acusar falha num limite que de facto foi aplicado.
            val snappedGpuHz = snapToSupported(targetGpu, gpuAvailableFreqsHz, toleranceAbove = 999_999L)
            listOf(PerformanceCommandBuilder.buildGpuFrequencyOp(sysfsGpuMaxPath, snappedGpuHz))
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
        if (isApplied(op.value, readBack)) {
            return HardwareNodeStatus(op.label, op.targetPath, op.value, readBack, ok = true)
        }

        repeat(retries + 1) {
            op.asSequentialCommands().forEach { executor.executeAsRoot(it) }
            readBack = readSysfsValue(op.targetPath)
            if (isApplied(op.value, readBack)) {
                return HardwareNodeStatus(op.label, op.targetPath, op.value, readBack, ok = true)
            }
        }
        return HardwareNodeStatus(op.label, op.targetPath, op.value, readBack, ok = false)
    }

    /**
     * Decide se uma escrita "pegou", comparando o pedido com o que o nó devolve.
     *
     * Antes era uma igualdade exata de strings, e isso gerava FALSOS NEGATIVOS constantes: estes
     * nós são LIMITES MÁXIMOS cujos valores têm de existir na tabela de OPPs do kernel. Ao pedir
     * um valor intermédio, o driver assenta na frequência suportada mais próxima e a leitura
     * devolve um número diferente do pedido -- ainda que o limite esteja perfeitamente aplicado.
     * Era esta a origem dos avisos "Não foi possível aplicar: cpu-perf-policy-0 / gpu-max-clock"
     * a repetirem-se em ciclo enquanto o aparelho estava, de facto, limitado.
     *
     * Critério correto para um teto: o valor efetivo não pode ficar ACIMA do pedido. Assentar
     * abaixo é sucesso. Toleramos 2% acima para o caso de o driver arredondar para o OPP
     * imediatamente superior. Se o valor lido continuar bem acima, a escrita falhou mesmo (nó
     * bloqueado, ou outro daemon a repor o valor de fábrica) e aí sim reportamos.
     */
    private fun isApplied(requested: String, readBack: String?): Boolean {
        if (readBack == null) return false
        if (readBack == requested) return true

        val requestedValue = requested.toLongOrNull() ?: return false
        val actualValue = readBack.toLongOrNull() ?: return false
        return actualValue <= (requestedValue * 102) / 100
    }

    private fun readSysfsValue(path: String): String? {
        // Lê sempre via root (o nó pode estar de volta a 444/root-only depois do "lock").
        return executor.executeAsRoot("cat $path").getOrNull()?.trim()
    }

    fun applyDynamicTdp(watts: Float) {
        isAutoTdp = true
        // Intervalo do slider em todos os ecrãs: 1..25 W (ver TdpProfiles). O piso de clocks
        // continua a ser TDP_MIN_RATIO -- 1 W é um alvo que o loop tenta perseguir, não uma
        // promessa de que a CPU desce a 4%.
        targetWatts = watts.coerceIn(TdpProfiles.TDP_MIN_WATTS, TdpProfiles.TDP_MAX_WATTS)
        val ratio = PerformanceCommandBuilder.wattsToRatio(targetWatts)
        targetPerfPercent = ratio
        targetPrimePercent = ratio
        targetGpu = (gpuMaxHz * ratio).toLong().coerceAtLeast(gpuMinHz)
        writeLimitsToSysfs()
    }

    /**
     * Perfil "Stock" de TDP: sem limite nenhum. Clocks de CPU e GPU a 100% e o loop de AutoTDP
     * DESLIGADO -- é o que distingue isto de "25 W": com 25 W o loop continua a correr e pode
     * ainda travar os clocks se o consumo medido passar do alvo.
     */
    fun applyStockPerformance() {
        isAutoTdp = false
        targetPerfPercent = PerformanceCommandBuilder.CPU_MAX_RATIO
        targetPrimePercent = PerformanceCommandBuilder.CPU_MAX_RATIO
        targetGpu = gpuMaxHz
        writeLimitsToSysfs()
    }

    /**
     * Aplica uma seleção de TDP já resolvida (ver `TdpProfiles.resolveWatts`): `null` = Stock,
     * qualquer outro valor = TDP dinâmico nesses watts. Ponto único de entrada para o Settings,
     * o overlay e o ForegroundAppWatcherService, para os três se comportarem da mesma maneira.
     */
    fun applyTdpSelection(watts: Float?) {
        if (watts == null || TdpProfiles.isStockWatts(watts)) applyStockPerformance() else applyDynamicTdp(watts)
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

        /** Valores do 8 Elite, usados só quando nem o kernel nem a tabela por SoC respondem. */
        const val DEFAULT_GPU_MAX_HZ = 1_100_000_000L
        const val DEFAULT_GPU_MIN_HZ = 160_000_000L
    }
}

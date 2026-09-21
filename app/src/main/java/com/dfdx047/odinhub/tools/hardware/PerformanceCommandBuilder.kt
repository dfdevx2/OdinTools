package com.dfdx047.odinhub.tools.hardware

/**
 * Representa uma política de cpufreq descoberta dinamicamente no kernel (ex: um cluster
 * "policyN" em /sys/devices/system/cpu/cpufreq). Não depende de [java.io.File] nem de qualquer
 * classe do Android, para que possa ser criada livremente em testes JVM puros.
 */
data class CpuPolicyNode(
    val policyPath: String,
    val cpuinfoMaxFreqKHz: Long,
    /**
     * Frequências que esta política REALMENTE aceita (a tabela de OPPs do kernel, lida de
     * `scaling_available_frequencies`). Vazia quando o nó não pôde ser lido.
     *
     * Sem isto escrevíamos valores arbitrários (`max * ratio`, ex: 1 071 000 kHz) que não existem
     * na tabela; o kernel aceitava a escrita mas assentava na frequência suportada mais próxima,
     * a leitura de verificação não batia certo com o pedido, e o resultado era reportado como
     * FALHA -- daí os avisos "Não foi possível aplicar: cpu-perf-policy-0" a aparecerem sem parar
     * enquanto, na prática, o limite estava a ser aplicado.
     */
    val availableFreqsKHz: List<Long> = emptyList(),
)

/**
 * Encaixa um alvo na lista de valores realmente suportados, escolhendo o maior valor que não
 * ultrapassa o alvo (é um LIMITE máximo: arredondar para cima furaria o limite pedido). Se o alvo
 * ficar abaixo de tudo, usa o menor suportado. Sem lista, devolve o alvo inalterado.
 */
fun snapToSupported(target: Long, supported: List<Long>): Long {
    if (supported.isEmpty()) return target
    return supported.filter { it <= target }.maxOrNull() ?: supported.min()
}

/**
 * Uma escrita atómica num nó de sysfs, desenhada à volta do padrão "desbloquear (666) -> escrever
 * -> bloquear (444)" usado pelo Pulse/ClusterTune para conseguir escrever em nós protegidos pelo
 * kernel sem deixá-los permanentemente abertos a outros processos.
 *
 * Expomos os três passos separadamente (em vez de só a string encadeada) porque descobrimos que
 * encadear tudo com `&&` numa única transação binder tem duas falhas graves:
 *  1. Se qualquer nó no meio da cadeia for rejeitado pelo kernel (comum em GPUs/clusters
 *     bloqueados por bootloader), o `&&` aborta TODOS os passos seguintes — incluindo nós que
 *     não têm nada a ver com a falha. Isto por si só explica o comportamento "instável": qual
 *     nó falha primeiro depende da ordem de iteração do diretório, por isso o resultado parece
 *     aleatório de boot para boot.
 *  2. Alguns backends de shell privilegiado (dependendo de como o PServer invoca o processo)
 *     podem não suportar operadores de shell (`&&`, `>`) quando o comando é entregue como um
 *     único argumento — funcionam bem para comandos simples (`getprop`, `settings put`) mas
 *     falham silenciosamente em cadeias compostas.
 *
 * [PerformanceManager] tenta primeiro a cadeia completa (mais rápida, 1 transação) e, se a
 * verificação de leitura falhar, recua para os três passos como transações binder separadas.
 */
data class HardwareWriteOp(
    val label: String,
    val targetPath: String,
    val value: String,
) {
    val unlockCommand: String get() = "chmod 666 $targetPath"
    val writeCommand: String get() = "echo $value > $targetPath"
    val lockCommand: String get() = "chmod 444 $targetPath"

    /** Comando único e linear, usado como tentativa rápida quando o backend suporta `sh -c`. */
    fun asChainedCommand(): String = "$unlockCommand && $writeCommand && $lockCommand"

    /** Os três passos como comandos independentes, para execução sequencial e resiliente. */
    fun asSequentialCommands(): List<String> = listOf(unlockCommand, writeCommand, lockCommand)
}

/**
 * Constrói os comandos de hardware (CPU/GPU) a partir de dados já lidos do kernel, sem tocar em
 * [java.io.File] ou em qualquer API do Android — toda a matemática de clocks/TDP e a formatação
 * dos comandos vive aqui, isolada e testável com JUnit puro.
 */
object PerformanceCommandBuilder {

    const val CPU_MIN_RATIO = 0.30f
    const val CPU_MAX_RATIO = 1.0f

    /**
     * Piso mínimo usado especificamente pelo TDP dinâmico (AutoTDP), separado de [CPU_MIN_RATIO].
     *
     * CAUSA RAIZ do bug "5W não limita, 10W limita": [wattsToRatio] e o passo de convergência em
     * `PerformanceManager.calculateAutoTdpStep` usavam o mesmo piso de 30% que os clocks manuais.
     * Um alvo de 5W (5/25 = 20%) era imediatamente sujeito a `coerceIn(0.30f, 1.0f)` e ficava preso
     * em 30% -- o que ainda deixa a CPU a desenhar bem mais do que 5W reais, por isso o utilizador
     * via "nenhum limite". Um alvo de 10W (10/25 = 40%) já ficava acima do piso e conseguia
     * convergir normalmente, por isso "funcionava". Um piso mais baixo dá ao loop fechado
     * (estilo Pulse, ver `calculateAutoTdpStep`) espaço real para descer até alvos baixos.
     */
    const val TDP_MIN_RATIO = 0.12f

    /**
     * Gera uma escrita de `scaling_max_freq` por cada política de CPU descoberta em tempo de
     * execução (varredura dinâmica, ao estilo Pulse: nunca assume `policy0`/`policy6` fixos,
     * porque o mapeamento de clusters varia por SoC e por firmware).
     *
     * A política com a maior frequência máxima é sempre tratada como o cluster "Prime"; todas as
     * outras recebem o ratio "Perf". Isto reflete topologias 1+3+4, 1+5+2, etc. sem hardcoding.
     */
    fun buildCpuFrequencyOps(
        policies: List<CpuPolicyNode>,
        perfRatio: Float,
        primeRatio: Float,
        // BUG CRÍTICO QUE ISTO CORRIGE: este piso estava fixo em [CPU_MIN_RATIO] (30%) aqui
        // dentro. Ou seja, todo o trabalho do [TDP_MIN_RATIO] (12%) -- o `wattsToRatio` e o passo
        // de convergência do AutoTDP -- era desfeito na última linha antes de escrever em sysfs:
        // um alvo de 5W calculava 12%, chegava aqui e era imediatamente puxado de volta para 30%.
        // A correção documentada do "5W não limita, 10W limita" nunca chegou a ter efeito real no
        // aparelho. Agora quem chama diz qual é o piso apropriado ao modo em uso.
        minRatio: Float = CPU_MIN_RATIO,
    ): List<HardwareWriteOp> {
        if (policies.isEmpty()) return emptyList()
        val sorted = policies.sortedBy { it.cpuinfoMaxFreqKHz }
        return sorted.mapIndexed { index, node ->
            val isPrime = index == sorted.lastIndex
            val ratio = (if (isPrime) primeRatio else perfRatio).coerceIn(minRatio, CPU_MAX_RATIO)
            val rawTargetKHz = (node.cpuinfoMaxFreqKHz * ratio)
                .toLong()
                .coerceIn(1L, node.cpuinfoMaxFreqKHz)
            // Encaixa na tabela de OPPs do kernel: escrevemos um valor que existe mesmo, por isso
            // a leitura de verificação passa a bater certo em vez de reportar uma falsa falha.
            val targetFreqKHz = snapToSupported(rawTargetKHz, node.availableFreqsKHz)
            buildWriteOp(
                label = if (isPrime) "cpu-prime-policy" else "cpu-perf-policy-$index",
                path = "${node.policyPath}/scaling_max_freq",
                value = targetFreqKHz.toString(),
            )
        }
    }

    fun buildGpuFrequencyOp(gpuMaxPath: String, targetHz: Long): HardwareWriteOp =
        buildWriteOp(label = "gpu-max-clock", path = gpuMaxPath, value = targetHz.toString())

    fun buildWriteOp(label: String, path: String, value: String): HardwareWriteOp =
        HardwareWriteOp(label = label, targetPath = path, value = value)

    /** Converte um alvo de TDP em watts para o ratio de clock normalizado usado pelo AutoTDP. */
    fun wattsToRatio(watts: Float, maxWatts: Float = 25f, minRatio: Float = TDP_MIN_RATIO): Float =
        (watts / maxWatts).coerceIn(minRatio, CPU_MAX_RATIO)

    /**
     * Deriva os ratios perf/prime a partir de clocks absolutos (KHz) pedidos pelo utilizador,
     * usando os máximos de cada política já descoberta como referência de 100%.
     */
    fun absoluteClocksToRatios(
        perfClockKHz: Long,
        primeClockKHz: Long,
        policies: List<CpuPolicyNode>,
        fallbackPerfMaxKHz: Long = 3_530_000L,
        fallbackPrimeMaxKHz: Long = 4_320_000L,
    ): Pair<Float, Float> {
        val sorted = policies.sortedBy { it.cpuinfoMaxFreqKHz }
        val maxPerf = sorted.getOrNull(sorted.lastIndex - 1)?.cpuinfoMaxFreqKHz ?: fallbackPerfMaxKHz
        val maxPrime = sorted.lastOrNull()?.cpuinfoMaxFreqKHz ?: fallbackPrimeMaxKHz

        val perfRatio = (perfClockKHz.toFloat() / maxPerf.toFloat()).coerceIn(CPU_MIN_RATIO, CPU_MAX_RATIO)
        val primeRatio = (primeClockKHz.toFloat() / maxPrime.toFloat()).coerceIn(CPU_MIN_RATIO, CPU_MAX_RATIO)
        return perfRatio to primeRatio
    }
}

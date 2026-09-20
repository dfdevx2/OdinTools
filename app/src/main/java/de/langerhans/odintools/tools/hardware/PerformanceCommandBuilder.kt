package de.langerhans.odintools.tools.hardware

class PerformanceCommandBuilder {

    fun buildApplyScript(
        cpuPolicies: List<Pair<String, Long>>, // Lista de caminhos de policy e frequências máximas
        selectedValues: Map<Int, Long>,
        isReset: Boolean,
        gpuPath: String?,
        gpuValue: Long?,
    ): String {
        val lines = mutableListOf<String>()
        val targetMode = if (isReset) "644" else "444"

        // 1. Aplica nos clusters de CPU dinâmicos
        cpuPolicies.forEachIndexed { index, (policyPath, _) ->
            val value = selectedValues[index] ?: return@forEachIndexed
            if (value > 0) {
                val maxPath = "$policyPath/scaling_max_freq"
                write(lines, maxPath, value, targetMode)
            }
        }

        // 2. Aplica na GPU Adreno se disponível
        if (!gpuPath.isNullOrBlank() && gpuValue != null && gpuValue > 0) {
            write(lines, gpuPath, gpuValue, "444")
        }

        return buildString {
            appendLine("#!/system/bin/sh")
            lines.forEach(::appendLine)
        }
    }

    private fun write(lines: MutableList<String>, path: String, value: Long, mode: String) {
        lines += "chmod 666 $path"
        lines += "echo $value > $path"
        lines += "chmod $mode $path"
    }
}

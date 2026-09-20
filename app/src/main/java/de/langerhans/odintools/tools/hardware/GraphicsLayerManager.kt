package de.langerhans.odintools.tools.hardware

import de.langerhans.odintools.models.ReshadeProfiles
import de.langerhans.odintools.tools.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Substitui o antigo `VulkanNativeBridge`. Aquele ficheiro chamava funções JNI que escreviam
 * variáveis globais C++ dentro do `.so` carregado pelo NOSSO processo (a app Odin Hub) -- mas a
 * camada Vulkan que precisa de facto desta configuração corre dentro do processo do JOGO,
 * carregada pelo loader Vulkan do sistema (`debug.vulkan.layer.dir`), nunca pelo nosso próprio
 * `System.loadLibrary`. Processos diferentes = memória diferente: um JNI setter no nosso processo
 * nunca influenciava a camada a correr no jogo. Era código morto disfarçado de "ponte".
 *
 * Corrigido: a configuração passa a viajar entre processos via **propriedades de sistema**
 * (`debug.odinhub.*`, ver `OdinLayerConfig.h` do lado nativo) -- o resto desta app já usa `setprop`
 * extensivamente via root (`ShellExecutor`), por isso é o canal mais simples e já disponível.
 * `debug.*` é o único prefixo de propriedades legível por qualquer processo sem restrições em
 * todas as versões do Android usadas aqui, mesmo vindo de um UID diferente do nosso -- exactamente
 * o que precisamos para que o processo do jogo consiga ler o que a nossa app (root) escreveu.
 *
 * Mantém os MESMOS nomes de métodos que o `VulkanNativeBridge` antigo (`applySgsr`/`applyLsfg`/
 * `applyReshade`) para minimizar alterações nos ecrãs/serviços que já os chamavam.
 */
@Singleton
class GraphicsLayerManager @Inject constructor(
    private val executor: ShellExecutor,
) {
    fun applySgsr(enabled: Boolean, modeStr: String) {
        val modeInt = when (modeStr) {
            "Quality" -> 0
            "Balanced" -> 1
            "Performance" -> 2
            "Ultra" -> 3
            else -> 0
        }
        setprop(PROP_SGSR_ENABLED, if (enabled) "1" else "0")
        setprop(PROP_SGSR_MODE, modeInt.toString())
    }

    fun applySgsrSharpness(sharpness: Float) {
        setprop(PROP_SGSR_SHARP, sharpness.toString())
    }

    fun applyLsfg(enabled: Boolean, multiplierStr: String, framePacing: Boolean) {
        val multInt = multiplierStr.replace("x", "").toIntOrNull() ?: 2
        setprop(PROP_LSFG_ENABLED, if (enabled) "1" else "0")
        setprop(PROP_LSFG_MULT, multInt.toString())
        // Frame pacing ainda não é lido pela camada nativa (o motor de geração de frames em si
        // ainda não está implementado -- ver AUDIT_PARTE5.md) mas já persistimos a intenção do
        // utilizador para quando essa parte existir; nada a fazer aqui por agora.
    }

    fun applyReshade(profileLabel: String, saturation: Float, temperature: Float) {
        val effectId = ReshadeProfiles.effectIdFor(profileLabel)
        setprop(PROP_RESHADE_EFFECT, effectId.toString())
        setprop(PROP_RESHADE_SAT, saturation.toString())
        setprop(PROP_RESHADE_TEMP, temperature.toString())
    }

    /**
     * Ativa a camada Vulkan real (`libVkLayer_OdinHub.so`) para UM jogo específico, via as
     * propriedades root `debug.vulkan.layers`/`debug.vulkan.layer.dir` -- o mecanismo que o
     * loader Vulkan on-device do Android lê ao criar uma VkInstance em qualquer processo.
     * Funciona com root (que esta app já assume em todo o lado) sem exigir que o jogo seja
     * "debuggable" nem depender do interruptor de developer options "GPU debug layers".
     *
     * `nativeLibraryDir` é o `applicationInfo.nativeLibraryDir` da NOSSA app (onde o
     * `libVkLayer_OdinHub.so` compilado por `app/src/main/cpp` fica instalado) -- passado de fora
     * em vez de resolvido aqui para não precisar de um Context nesta classe.
     *
     * Ver a Parte 5 do relatório: isto substitui `DisplayManager.applyGpuLayers`, que apontava
     * para nomes de camada fictícios e nunca era chamado por ninguém.
     */
    fun enableLayerForGame(nativeLibraryDir: String) {
        executor.executeAsRoot("setprop debug.vulkan.layers VkLayer_OdinHub")
        executor.executeAsRoot("setprop debug.vulkan.layer.dir $nativeLibraryDir")
    }

    /** Chamado quando se sai do jogo -- ver bug reportado sobre o overlay/perfis afetarem o sistema inteiro. */
    fun disableLayer() {
        executor.executeAsRoot("setprop debug.vulkan.layers \"\"")
        executor.executeAsRoot("setprop debug.vulkan.layer.dir \"\"")
    }

    private fun setprop(key: String, value: String) {
        executor.executeAsRoot("setprop $key $value")
    }

    private companion object {
        // Têm de bater certo, byte a byte, com as constantes em OdinLayerConfig.h.
        const val PROP_SGSR_ENABLED = "debug.odinhub.sgsr.on"
        const val PROP_SGSR_MODE = "debug.odinhub.sgsr.mode"
        const val PROP_SGSR_SHARP = "debug.odinhub.sgsr.sharp"
        const val PROP_RESHADE_EFFECT = "debug.odinhub.fx.effect"
        const val PROP_RESHADE_SAT = "debug.odinhub.fx.sat"
        const val PROP_RESHADE_TEMP = "debug.odinhub.fx.temp"
        const val PROP_LSFG_ENABLED = "debug.odinhub.lsfg.on"
        const val PROP_LSFG_MULT = "debug.odinhub.lsfg.mult"
    }
}

package com.dfdx047.odinhub.tools.hardware

import com.dfdx047.odinhub.data.SharedPrefsRepo
import com.dfdx047.odinhub.tools.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayManager @Inject constructor(
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
) {
    // ==========================================
    // INJEÇÃO DE VULKAN LAYERS (FRAME GEN E SGSR)
    // ==========================================

    /**
     * Aplica as bibliotecas dinâmicas (.so) no jogo que está abrindo.
     * Funciona sem root se o Odin Hub tiver permissão ADB/Shizuku, ou com Root nativo.
     */
    fun applyGpuLayers(packageName: String, lsfgEnabled: Boolean, sgsrEnabled: Boolean) {
        if (!lsfgEnabled && !sgsrEnabled) {
            clearGpuLayers()
            return
        }

        val layers = mutableListOf<String>()
        if (lsfgEnabled) layers.add("VK_LAYER_LSFG_frame_generation") // Nome fictício da layer do Lossless Scaling
        if (sgsrEnabled) layers.add("VK_LAYER_QCOM_sgsr") // Nome padrão da layer do Snapdragon Super Res

        val layersString = layers.joinToString(":")

        // Ativa a infraestrutura de depuração gráfica no Android
        executor.executeAsRoot("settings put global enable_gpu_debug_layers 1")
        // Diz ao Android em qual jogo injetar
        executor.executeAsRoot("settings put global gpu_debug_app $packageName")
        // Injeta as camadas selecionadas
        executor.executeAsRoot("settings put global gpu_debug_layers $layersString")
        // Aponta para a pasta do nosso app onde os arquivos .so estarão guardados
        executor.executeAsRoot("settings put global gpu_debug_layer_app com.dfdx047.odinhub")
    }

    /**
     * Limpa a injeção quando o jogo é fechado ou o recurso desativado.
     */
    fun clearGpuLayers() {
        executor.executeAsRoot("settings delete global enable_gpu_debug_layers")
        executor.executeAsRoot("settings delete global gpu_debug_app")
        executor.executeAsRoot("settings delete global gpu_debug_layers")
        executor.executeAsRoot("settings delete global gpu_debug_layer_app")
    }

    // ==========================================
    // CALIBRAÇÃO DE TELA (SATURAÇÃO E TEMPERATURA)
    // ==========================================
    //
    // PORQUE NÃO FUNCIONAVA ANTES:
    //  - Saturação: `setprop persist.sys.sf.color_saturation` só é lido pelo SurfaceFlinger no
    //    arranque, por isso mexer no slider não mudava nada até ao próximo reboot. E o
    //    "refresh" `service call SurfaceFlinger 1008 i32 1` NÃO é um refresh: a transação 1008
    //    é "desativar o Hardware Composer" -- forçava composição por GPU (custo de desempenho e
    //    bateria nos jogos) e ficava assim até reiniciar.
    //  - Temperatura: usava o Night Display, que só AQUECE o ecrã e ainda limita o valor ao
    //    intervalo do aparelho (~2600-4100 K). Tudo acima disso -- incluindo os 6500 K
    //    "neutros" -- era cortado para o máximo quente, e "mais frio" era impossível.
    //
    // AGORA: as duas transações de depuração do próprio SurfaceFlinger, que aplicam na hora e
    // funcionam nos dois sentidos (via PServerBinder, que corre como root):
    //  - 1022 f <x>          -> fator global de saturação (0.0-2.0, 1.0 = original)
    //  - 1015 i32 1 f*16     -> matriz de cor 4x4 (column-major); aqui só a diagonal RGB,
    //                           com os ganhos do balanço de branco calculados de [ColorMath].
    //  - 1015 i32 0          -> repõe a matriz identidade.
    // Os valores perdem-se num reboot/reinício do SurfaceFlinger -- o BootReceiver e o
    // ForegroundAppWatcherService reaplicam.

    /** Aplica saturação e temperatura de uma vez (é o que os ecrãs e o watcher chamam). */
    fun applyColor(saturation: Float, kelvin: Float) {
        cleanupLegacyStateOnce()
        applySaturation(saturation)
        applyTemperature(kelvin)
    }

    // Valores neutros só são escritos se NÓS tivermos alterado antes (flags em prefs, porque o
    // estado do SurfaceFlinger sobrevive à morte do processo da app). Assim, com o slider no
    // neutro, a app nunca pisa a matriz / saturação que o próprio sistema usa (Night Light,
    // correção de cor de acessibilidade, modo de cor "vívido").

    fun applySaturation(saturation: Float) {
        val value = saturation.coerceIn(ColorMath.SATURATION_MIN, ColorMath.SATURATION_MAX)
        val neutral = ColorMath.isNeutralSaturation(value)
        if (neutral && !prefs.displaySaturationActive) return
        if (executor.executeAsRoot("service call SurfaceFlinger 1022 f ${ColorMath.fmt(value)}").isSuccess) {
            prefs.displaySaturationActive = !neutral
        }
    }

    fun applyTemperature(kelvin: Float) {
        val neutral = ColorMath.isNeutral(kelvin)
        if (neutral && !prefs.displayMatrixActive) return
        val result = if (neutral) {
            executor.executeAsRoot("service call SurfaceFlinger 1015 i32 0")
        } else {
            executor.executeAsRoot(ColorMath.colorMatrixCommand(ColorMath.whiteBalanceGains(kelvin)))
        }
        if (result.isSuccess) prefs.displayMatrixActive = !neutral
    }

    /** No arranque do aparelho o SurfaceFlinger começa limpo -- esquecer o que tínhamos escrito. */
    fun onDeviceBoot() {
        prefs.displaySaturationActive = false
        prefs.displayMatrixActive = false
    }

    fun resetDisplayColor() {
        executor.executeAsRoot("service call SurfaceFlinger 1022 f 1.0")
        executor.executeAsRoot("service call SurfaceFlinger 1015 i32 0")
        // Desfaz o estado que as versões antigas deixavam para trás.
        executor.executeAsRoot("service call SurfaceFlinger 1008 i32 0")
        executor.executeAsRoot("settings put secure night_display_activated 0")
        prefs.displaySaturationActive = false
        prefs.displayMatrixActive = false
    }

    /**
     * Versões anteriores ligavam o Night Display e desativavam o HWC (1008 i32 1). Na primeira
     * aplicação depois de atualizar, desfaz isso uma vez -- sem isto o ecrã ficava com a
     * dominante quente antiga por cima da nova matriz.
     */
    private fun cleanupLegacyStateOnce() {
        if (prefs.displayColorMigrated) return
        executor.executeAsRoot("service call SurfaceFlinger 1008 i32 0")
        executor.executeAsRoot("settings put secure night_display_activated 0")
        prefs.displayColorMigrated = true
    }
}

/**
 * Matemática pura (testável em JVM) da calibração de cor.
 */
object ColorMath {
    const val SATURATION_MIN = 0f
    const val SATURATION_MAX = 2f
    const val NEUTRAL_KELVIN = 6500f
    const val KELVIN_MIN = 4000f
    const val KELVIN_MAX = 9000f

    fun isNeutral(kelvin: Float): Boolean = kotlin.math.abs(kelvin - NEUTRAL_KELVIN) < 50f
    fun isNeutralSaturation(saturation: Float): Boolean = kotlin.math.abs(saturation - 1f) < 0.01f

    /** Cor do corpo negro a [kelvin] (aproximação de Tanner Helland), canais 0..255. */
    fun kelvinToRgb(kelvin: Float): FloatArray {
        val t = kelvin.coerceIn(1000f, 40000f) / 100.0
        val r = if (t <= 66) 255.0 else 329.698727446 * Math.pow(t - 60, -0.1332047592)
        val g = if (t <= 66) 99.4708025861 * Math.log(t) - 161.1195681661 else 288.1221695283 * Math.pow(t - 60, -0.0755148492)
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> 138.5177312231 * Math.log(t - 10) - 305.0447927307
        }
        return floatArrayOf(r.coerceIn(0.0, 255.0).toFloat(), g.coerceIn(0.0, 255.0).toFloat(), b.coerceIn(0.0, 255.0).toFloat())
    }

    /**
     * Ganhos R/G/B para o branco do ecrã passar de 6500 K para [kelvin]. Normalizados para o
     * maior ser 1.0 -- só se atenua canais, nunca se satura (clipping) nenhum.
     */
    fun whiteBalanceGains(kelvin: Float): FloatArray {
        val target = kelvinToRgb(kelvin.coerceIn(KELVIN_MIN, KELVIN_MAX))
        val ref = kelvinToRgb(NEUTRAL_KELVIN)
        val raw = FloatArray(3) { target[it] / ref[it] }
        val max = raw.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return FloatArray(3) { (raw[it] / max).coerceIn(0f, 1f) }
    }

    /** `service call SurfaceFlinger 1015 i32 1 f ...` com uma matriz diagonal (column-major). */
    fun colorMatrixCommand(gains: FloatArray): String {
        val m = FloatArray(16)
        m[0] = gains[0]; m[5] = gains[1]; m[10] = gains[2]; m[15] = 1f
        return "service call SurfaceFlinger 1015 i32 1 " + m.joinToString(" ") { "f ${fmt(it)}" }
    }

    /** Sempre com ponto decimal (Locale.US) -- "1,5" com locale PT partia o comando. */
    fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.4f", value)
}

package de.langerhans.odintools.tools.hardware

import de.langerhans.odintools.tools.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayManager @Inject constructor(
    private val executor: ShellExecutor
) {
    // ==========================================
    // INJEÇÃO DE VULKAN LAYERS (FRAME GEN E SGSR)
    // ==========================================

    /**
     * Aplica as bibliotecas dinâmicas (.so) no jogo que está abrindo.
     * Funciona sem root se o OdinTools tiver permissão ADB/Shizuku, ou com Root nativo.
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
        executor.executeAsRoot("settings put global gpu_debug_layer_app de.langerhans.odintools")
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

    /**
     * Usa o binário nativo do SurfaceFlinger (Android 11+) para alterar a matriz de cores.
     * Saturação padrão = 1.0f. Valores maiores = cores mais vivas.
     */
    fun applySaturation(saturation: Float) {
        // Comando direto para o SurfaceFlinger (suporta root).
        // Em muitos firmwares da AYN, a saturação é controlada via 'persist.sys.sf.color_saturation'
        executor.executeAsRoot("setprop persist.sys.sf.color_saturation $saturation")

        // Força a atualização da tela
        executor.executeAsRoot("service call SurfaceFlinger 1008 i32 1")
    }

    /**
     * Altera o balanço de branco da tela (Night Display / Color Transform).
     * 6500K é o padrão (Branco puro). Valores menores ficam amarelados, maiores ficam azulados.
     */
    fun applyTemperature(kelvin: Float) {
        // Usamos o sistema nativo do Android Night Display
        executor.executeAsRoot("settings put secure night_display_activated 1")
        // O Android só aceita valores de temperatura como inteiros se o modo noturno for forçado
        executor.executeAsRoot("settings put secure night_display_color_temperature ${kelvin.toInt()}")
    }

    fun resetDisplayColor() {
        executor.executeAsRoot("setprop persist.sys.sf.color_saturation 1.0")
        executor.executeAsRoot("settings put secure night_display_activated 0")
        executor.executeAsRoot("service call SurfaceFlinger 1008 i32 1")
    }
}
package de.langerhans.odintools.tools.hardware

import de.langerhans.odintools.tools.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayManager @Inject constructor(
    private val executor: ShellExecutor
) {
    // A injeção de camadas Vulkan (SGSR/ReShade/LSFG) mudou-se para GraphicsLayerManager
    // (enableLayerForGame/disableLayer), usando `debug.vulkan.layers`/`debug.vulkan.layer.dir`
    // via root em vez das `settings global gpu_debug_*` daqui -- essas dependem de developer
    // options ("Enable GPU debug layers") e de o jogo alvo ser "debuggable", o que não se aplica
    // à generalidade dos jogos reais; o mecanismo root já usado pelo resto desta app funciona
    // sempre. Esta função também apontava para nomes de camada fictícios
    // (`VK_LAYER_LSFG_frame_generation`/`VK_LAYER_QCOM_sgsr`) e nunca chegou a ser chamada por
    // ninguém -- ver AUDIT_PARTE5.md.

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
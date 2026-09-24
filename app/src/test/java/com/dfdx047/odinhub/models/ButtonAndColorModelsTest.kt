package com.dfdx047.odinhub.models

import com.dfdx047.odinhub.tools.hardware.ColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes JVM puros: macros M1/M2, gestos do Home e matemática da calibração de cor. */
class ButtonAndColorModelsTest {

    // ---------------------------------------------------------------- ButtonMacros

    @Test
    fun `encode e decode sao inversos`() {
        val steps = listOf(MacroStep(listOf(96, 97), 50), MacroStep(listOf(99), 0))
        val raw = ButtonMacros.encode(steps)
        assertEquals("96+97@50;99@0", raw)
        assertEquals(steps, ButtonMacros.decode(raw))
    }

    @Test
    fun `decode ignora lixo sem lancar`() {
        assertEquals(emptyList<MacroStep>(), ButtonMacros.decode(null))
        assertEquals(emptyList<MacroStep>(), ButtonMacros.decode(""))
        assertEquals(listOf(MacroStep(listOf(96), 0)), ButtonMacros.decode("abc;96@x;@5"))
    }

    @Test
    fun `comando usa keycombination para combos e sleep entre passos`() {
        val cmd = ButtonMacros.toShellCommand(listOf(MacroStep(listOf(96, 97), 250), MacroStep(listOf(99), 500)))
        assertEquals("input gamepad keycombination 96 97 ; sleep 0.25 ; input gamepad keyevent 99", cmd)
    }

    @Test
    fun `macro vazia nao gera comando`() {
        assertNull(ButtonMacros.toShellCommand(emptyList()))
    }

    @Test
    fun `sleep de segundos inteiros e fracoes`() {
        val cmd = ButtonMacros.toShellCommand(listOf(MacroStep(listOf(96), 1000), MacroStep(listOf(97), 50), MacroStep(listOf(98), 0)))
        assertEquals("input gamepad keyevent 96 ; sleep 1 ; input gamepad keyevent 97 ; sleep 0.05 ; input gamepad keyevent 98", cmd)
    }

    @Test
    fun `modo da macro por jogo`() {
        assertEquals(MacroMode.GLOBAL, MacroMode.of(null))
        assertEquals(MacroMode.OFF, MacroMode.of(""))
        assertEquals(MacroMode.CUSTOM, MacroMode.of("96@0"))
        assertNull(MacroMode.encode(MacroMode.GLOBAL, listOf(MacroStep(listOf(96)))))
        assertEquals("", MacroMode.encode(MacroMode.OFF, listOf(MacroStep(listOf(96)))))
        assertEquals("96@0", MacroMode.encode(MacroMode.CUSTOM, listOf(MacroStep(listOf(96)))))
    }

    // ---------------------------------------------------------------- HomeGestureConfig

    @Test
    fun `maxTaps depende das acoes configuradas`() {
        assertEquals(3, HomeGestureConfig().maxTaps)
        assertEquals(2, HomeGestureConfig(tripleTap = ButtonAction.NONE).maxTaps)
        assertEquals(1, HomeGestureConfig(doubleTap = ButtonAction.NONE, tripleTap = ButtonAction.NONE).maxTaps)
    }

    @Test
    fun `acao por numero de toques`() {
        val cfg = HomeGestureConfig()
        assertEquals(ButtonAction.HOME, cfg.actionForTaps(1))
        assertEquals(ButtonAction.TOGGLE_OVERLAY, cfg.actionForTaps(2))
        assertEquals(ButtonAction.SCREEN_RECORD, cfg.actionForTaps(3))
        assertEquals(ButtonAction.SCREEN_RECORD, cfg.actionForTaps(5))
        assertEquals(ButtonAction.NONE, cfg.actionForTaps(0))
    }

    @Test
    fun `ids desconhecidos viram NONE`() {
        assertEquals(ButtonAction.NONE, ButtonAction.fromId("nao_existe"))
        assertEquals(ButtonAction.SCREENSHOT, ButtonAction.fromId("screenshot"))
    }

    // ---------------------------------------------------------------- ColorMath

    @Test
    fun `6500K neutro da ganhos unitarios`() {
        val g = ColorMath.whiteBalanceGains(6500f)
        g.forEach { assertEquals(1f, it, 0.001f) }
    }

    @Test
    fun `mais quente corta o azul, mais frio corta o vermelho`() {
        val warm = ColorMath.whiteBalanceGains(4000f)
        assertEquals(1f, warm[0], 0.001f)
        assertTrue(warm[2] < 0.8f)
        val cool = ColorMath.whiteBalanceGains(9000f)
        assertEquals(1f, cool[2], 0.001f)
        assertTrue(cool[0] < 0.95f)
    }

    @Test
    fun `comando da matriz tem 16 floats com ponto decimal`() {
        val cmd = ColorMath.colorMatrixCommand(floatArrayOf(1f, 0.9f, 0.5f))
        assertTrue(cmd.startsWith("service call SurfaceFlinger 1015 i32 1 "))
        assertEquals(16, Regex(" f ").findAll(cmd).count())
        assertTrue(!cmd.contains(","))
        assertTrue(cmd.contains("f 0.5000"))
    }
}

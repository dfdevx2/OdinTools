package com.dfdx047.odinhub.models

import android.view.KeyEvent

/**
 * Um passo de uma macro dos botões traseiros: as [keys] são premidas EM SIMULTÂNEO (combo) e,
 * depois, espera-se [delayMs] antes do passo seguinte.
 */
data class MacroStep(val keys: List<Int>, val delayMs: Int = 0)

/**
 * Macros / combos dos botões M1 e M2.
 *
 * COMO FUNCIONA: com a macro ligada, o remapeamento NATIVO do botão (setting da AYN
 * `remap_custom_to_mX_value`) passa a apontar para uma tecla-gatilho que nenhum jogo usa
 * ([TRIGGER_M1] / [TRIGGER_M2]). O serviço de acessibilidade do Odin Hub consome essa tecla e
 * reproduz os passos com o comando `input` (via PServerBinder, que corre como root -- sem root
 * a Android não deixa uma app injetar teclas noutra). Ao desligar a macro, o mapeamento nativo
 * que lá estava é reposto.
 *
 * LIMITAÇÕES (honestas): cada passo arranca o comando `input`, que demora ~100-300 ms, por isso
 * serve para combos e sequências, não para inputs de frame perfeito; e as teclas saem de um
 * dispositivo virtual com origem "gamepad" -- a maioria dos emuladores e jogos aceita, mas um
 * jogo que só leia o comando físico pode ignorá-las.
 */
object ButtonMacros {
    /** KEYCODE_BUTTON_15 / KEYCODE_BUTTON_16: botões genéricos de gamepad que nenhum jogo usa. */
    const val TRIGGER_M1 = KeyEvent.KEYCODE_BUTTON_15
    const val TRIGGER_M2 = KeyEvent.KEYCODE_BUTTON_16

    const val MAX_STEPS = 16
    val DELAY_OPTIONS_MS = listOf(0, 50, 100, 250, 500)

    /** Teclas que o editor oferece, com o nome curto mostrado nos chips. */
    val GAMEPAD_KEYS: List<Pair<Int, String>> = listOf(
        KeyEvent.KEYCODE_BUTTON_A to "A",
        KeyEvent.KEYCODE_BUTTON_B to "B",
        KeyEvent.KEYCODE_BUTTON_X to "X",
        KeyEvent.KEYCODE_BUTTON_Y to "Y",
        KeyEvent.KEYCODE_BUTTON_L1 to "L1",
        KeyEvent.KEYCODE_BUTTON_R1 to "R1",
        KeyEvent.KEYCODE_BUTTON_L2 to "L2",
        KeyEvent.KEYCODE_BUTTON_R2 to "R2",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "L3",
        KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",
        KeyEvent.KEYCODE_BUTTON_START to "Start",
        KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        KeyEvent.KEYCODE_DPAD_UP to "↑",
        KeyEvent.KEYCODE_DPAD_DOWN to "↓",
        KeyEvent.KEYCODE_DPAD_LEFT to "←",
        KeyEvent.KEYCODE_DPAD_RIGHT to "→",
    )

    fun keyLabel(keyCode: Int): String =
        GAMEPAD_KEYS.firstOrNull { it.first == keyCode }?.second ?: keyCode.toString()

    fun describe(step: MacroStep): String =
        step.keys.joinToString(" + ") { keyLabel(it) } + if (step.delayMs > 0) "  ·  ${step.delayMs} ms" else ""

    /**
     * Formato gravado em SharedPrefs: passos separados por `;`, cada um `k1+k2@delay`.
     * Ex.: "96+97@50;99@0" = (A+B), espera 50 ms, depois X.
     */
    fun encode(steps: List<MacroStep>): String =
        steps.filter { it.keys.isNotEmpty() }
            .joinToString(";") { step -> step.keys.joinToString("+") + "@" + step.delayMs.coerceAtLeast(0) }

    /** Inverso de [encode]. Tolerante: passos malformados são ignorados, nunca lança. */
    fun decode(raw: String?): List<MacroStep> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { part ->
            val keysPart = part.substringBefore('@')
            val delay = part.substringAfter('@', "0").trim().toIntOrNull()?.coerceIn(0, 10_000) ?: 0
            val keys = keysPart.split('+').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
            if (keys.isEmpty()) null else MacroStep(keys.distinct(), delay)
        }.take(MAX_STEPS)
    }

    /**
     * Um único comando de shell para a macro inteira (uma só transação no PServer). Só inteiros
     * entram no comando, por isso não há nada para "escapar".
     */
    fun toShellCommand(steps: List<MacroStep>): String? {
        val parts = mutableListOf<String>()
        steps.filter { it.keys.isNotEmpty() }.take(MAX_STEPS).forEach { step ->
            parts += if (step.keys.size == 1) {
                "input gamepad keyevent ${step.keys[0]}"
            } else {
                "input gamepad keycombination ${step.keys.joinToString(" ")}"
            }
            if (step.delayMs > 0) parts += "sleep ${formatSeconds(step.delayMs)}"
        }
        // Um "sleep" no fim não serve para nada.
        while (parts.lastOrNull()?.startsWith("sleep") == true) parts.removeAt(parts.lastIndex)
        return if (parts.isEmpty()) null else parts.joinToString(" ; ")
    }

    private fun formatSeconds(ms: Int): String {
        val whole = ms / 1000
        val frac = ms % 1000
        return if (frac == 0) "$whole" else "$whole." + frac.toString().padStart(3, '0').trimEnd('0')
    }
}

/**
 * Macro de um botão numa regra por jogo (AppOverrideEntity.m1Macro / m2Macro):
 *  - `null`  -> [GLOBAL]: segue o que está na aba Controls;
 *  - `""`    -> [OFF]: mapeamento nativo neste jogo, mesmo com macro global ligada;
 *  - passos  -> [CUSTOM]: a macro própria deste jogo (formato de ButtonMacros.encode).
 */
enum class MacroMode(val labelEn: String, val labelPt: String) {
    GLOBAL("Global", "Global"),
    OFF("Off (native)", "Desligada"),
    CUSTOM("Custom", "Própria");

    fun label(isEn: Boolean): String = if (isEn) labelEn else labelPt

    companion object {
        fun of(stored: String?): MacroMode = when {
            stored == null -> GLOBAL
            stored.isBlank() -> OFF
            else -> CUSTOM
        }

        /** Valor a gravar no Room para este modo e estes passos. */
        fun encode(mode: MacroMode, steps: List<MacroStep>): String? = when (mode) {
            GLOBAL -> null
            OFF -> ""
            // Macro própria sem passos equivale a "desligada" (nunca gravar CUSTOM vazio como GLOBAL).
            CUSTOM -> ButtonMacros.encode(steps)
        }
    }
}

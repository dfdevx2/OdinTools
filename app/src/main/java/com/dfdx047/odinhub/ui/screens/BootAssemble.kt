package com.dfdx047.odinhub.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dfdx047.odinhub.ui.theme.ConsoleTheme

/*
 * Animação "a interface a reconstruir-se" que corre logo a seguir ao vídeo de arranque:
 * um feixe de varrimento desce pelo ecrã sobre uma grelha, e atrás dele o título, a barra de
 * menus e o painel entram em cascata.
 *
 * O progresso chega como lambda e só é lido dentro de `graphicsLayer`/`Canvas` (fase de desenho):
 * a animação não provoca recomposições do ecrã inteiro a cada fotograma, só redesenho.
 * Com progresso = 1 tudo fica exatamente como sem animação (identidade), por isso é inócuo fora
 * do arranque.
 */

/** Faz um elemento "montar-se" entre [start] e [end] do progresso global (0..1). */
fun Modifier.assembleStage(
    progress: () -> Float,
    start: Float,
    end: Float,
    fromX: Dp = 0.dp,
    fromY: Dp = 0.dp,
): Modifier = this.graphicsLayer {
    val t = ((progress() - start) / (end - start)).coerceIn(0f, 1f)
    val e = FastOutSlowInEasing.transform(t)
    alpha = e
    translationX = (1f - e) * fromX.toPx()
    translationY = (1f - e) * fromY.toPx()
    val s = 0.96f + 0.04f * e
    scaleX = s
    scaleY = s
}

/** Feixe de varrimento + grelha + véu sobre a parte ainda "por construir". Não interceta toques. */
@Composable
fun AssembleScanOverlay(progress: () -> Float, theme: ConsoleTheme, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val p = progress()
        if (p >= 1f) return@Canvas

        val scan = FastOutSlowInEasing.transform((p / SCAN_END).coerceIn(0f, 1f))
        val y = size.height * scan
        val fade = (1f - (p - SCAN_END) / (1f - SCAN_END)).coerceIn(0f, 1f)

        // Véu por baixo do feixe: o que ainda não foi "reconstruído".
        if (y < size.height) {
            drawRect(color = theme.background.copy(alpha = 0.94f * fade), topLeft = Offset(0f, y), size = Size(size.width, size.height - y))
        }

        // Grelha técnica na zona por construir.
        val step = 44.dp.toPx()
        val gridColor = theme.primary.copy(alpha = 0.14f * fade)
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, y), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var gy = size.height
        while (gy >= y) {
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            gy -= step
        }

        // Rasto luminoso acima do feixe e o próprio feixe.
        val glow = 110.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, theme.primary.copy(alpha = 0.30f * fade)),
                startY = y - glow,
                endY = y,
            ),
            topLeft = Offset(0f, y - glow),
            size = Size(size.width, glow),
        )
        drawLine(theme.primary.copy(alpha = fade), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.75f * fade), Offset(size.width * 0.08f, y), Offset(size.width * 0.92f, y), strokeWidth = 1.dp.toPx())

        // Cantoneiras de "HUD" que se afastam para as bordas.
        val inset = (1f - scan) * size.minDimension * 0.18f + 16.dp.toPx()
        val arm = 28.dp.toPx()
        val c = theme.primary.copy(alpha = 0.8f * fade)
        val w = 2.dp.toPx()
        drawLine(c, Offset(inset, inset), Offset(inset + arm, inset), w)
        drawLine(c, Offset(inset, inset), Offset(inset, inset + arm), w)
        drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset - arm, inset), w)
        drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset, inset + arm), w)
        drawLine(c, Offset(inset, size.height - inset), Offset(inset + arm, size.height - inset), w)
        drawLine(c, Offset(inset, size.height - inset), Offset(inset, size.height - inset - arm), w)
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - arm, size.height - inset), w)
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - arm), w)
    }
}

/** Duração total da animação de montagem. */
const val ASSEMBLE_DURATION_MS = 1800

/** Fração do progresso em que o feixe chega ao fundo; depois disso os efeitos desvanecem. */
private const val SCAN_END = 0.7f

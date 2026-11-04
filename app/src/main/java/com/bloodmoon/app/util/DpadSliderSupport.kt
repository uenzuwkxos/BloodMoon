package com.bloodmoon.app.util

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * O Slider padrão do Compose/Material3 não tem nenhum comportamento definido para as setas
 * esquerda/direita de um D-pad de TV: sem foco explícito nem tratamento de tecla, apertar essas
 * setas com o slider selecionado simplesmente não faz nada (é isso que foi reportado como "as
 * sliders não são compatíveis com controle de TV"). Este Modifier cobre exatamente esse caso:
 * enquanto o componente estiver com foco, DPAD_LEFT diminui e DPAD_RIGHT aumenta [value] em
 * [step], soltando a tecla para. Segurar a tecla repete o ajuste automaticamente (delay inicial
 * de 400ms, depois repetição a cada 80ms) - o mesmo tipo de comportamento "segurar para
 * acelerar" que outros apps de TV (ajuste de volume, brilho) já usam.
 *
 * [onChange] é chamado a cada passo (equivalente a onValueChangeFinished do Slider original) e
 * já entrega o valor coagido a [valueRange] - o chamador não precisa repetir esse coerceIn.
 *
 * Aplicado ao Slider inteiro (não só à thumb), pois é isso que garante que o item receba foco de
 * D-pad ao navegar - o próprio Slider do Material3 já é focável, mas sem este Modifier o foco
 * chegava até ele sem que as setas fizessem nada além de tentar mudar o foco para o próximo
 * item (comportamento herdado do FocusManager, não do Slider).
 */
fun Modifier.dpadAdjustable(
    value: Int,
    valueRange: IntRange,
    step: Int = 1,
    enabled: Boolean = true,
    onChange: (Int) -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    // Sem "by": dentro de um Modifier.composed genérico, o delegado de propriedade
    // (getValue/setValue de MutableState) falha ao ser inferido pelo compilador Kotlin
    // ("Type 'TypeVariable(T)' has no method 'getValue'...") - erro real de build confirmado.
    // Acessar .value diretamente evita depender dessa inferência, sem nenhuma mudança de
    // comportamento.
    val repeatJobState = remember { mutableStateOf<Job?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    fun stopRepeating() {
        repeatJobState.value?.cancel()
        repeatJobState.value = null
    }

    fun startRepeating(direction: Int) {
        stopRepeating()
        // Aplica o primeiro passo imediatamente (sem esperar o delay inicial), senão um toque
        // rápido no D-pad pareceria não ter feito nada.
        val first = (value + direction * step).coerceIn(valueRange.first, valueRange.last)
        onChange(first)
        repeatJobState.value = scope.launch {
            delay(400)
            var current = first
            while (isActive) {
                current = (current + direction * step).coerceIn(valueRange.first, valueRange.last)
                onChange(current)
                if (current == valueRange.first || current == valueRange.last) break
                delay(80)
            }
        }
    }

    this
        .focusable(enabled = true, interactionSource = interactionSource)
        .onPreviewKeyEvent { event ->
            when {
                event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown -> {
                    startRepeating(-1)
                    true
                }
                event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown -> {
                    startRepeating(1)
                    true
                }
                (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) &&
                    event.type == KeyEventType.KeyUp -> {
                    stopRepeating()
                    true
                }
                else -> false
            }
        }
}

package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.sqrt

internal class LyricsPressIndication private constructor(
    private val rightAligned: Boolean
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        LyricsPressNode(interactionSource, rightAligned)

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = rightAligned.hashCode()

    companion object {
        val Start = LyricsPressIndication(false)
        val End = LyricsPressIndication(true)
    }
}

private val pressSpring = spring<Float>(
    stiffness = 322f,
    dampingRatio = 24f / (2f * sqrt(322f)),
    visibilityThreshold = 0.0001f
)

private val releaseSpring = spring<Float>(
    stiffness = 150f,
    dampingRatio = 50f / (2f * sqrt(600f)),
    visibilityThreshold = 0.0001f
)

private class LyricsPressNode(
    private val interactionSource: InteractionSource,
    private val rightAligned: Boolean
) : Modifier.Node(), DrawModifierNode {
    private var animatedScale = Animatable(1f, visibilityThreshold = 0.0001f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions
                .filter { it is PressInteraction.Press || it is PressInteraction.Release || it is PressInteraction.Cancel }
                .collectLatest { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> animatedScale.animateTo(0.95f, pressSpring)
                        is PressInteraction.Release -> animatedScale.animateTo(1f, releaseSpring)
                        is PressInteraction.Cancel -> animatedScale.snapTo(1f)
                    }
                }
        }
    }

    override fun onDetach() {
        animatedScale = Animatable(1f, visibilityThreshold = 0.0001f)
    }

    override fun ContentDrawScope.draw() {
        val currentScale = animatedScale.value
        if (currentScale == 1f) {
            drawContent()
        } else {
            scale(currentScale, Offset(if (rightAligned) size.width else 0f, size.height)) {
                this@draw.drawContent()
            }
        }
    }
}

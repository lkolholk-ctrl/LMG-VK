package com.lmg.vk.ui.effects

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

internal class DustParticleMesh(width: Int, height: Int, density: Float, maxParticles: Int = 6000) {
    init {
        require(width > 0 && height > 0 && density > 0f && maxParticles > 0)
    }

    private val cell = max(2f * density, sqrt(width.toFloat() * height / maxParticles))
    private val columns = ceil(width / cell).toInt().coerceIn(1, maxParticles)
    private val rows = ceil(height / cell).toInt().coerceIn(1, maxParticles / columns)
    val count = columns * rows
    val vertices = FloatArray(count * 12)
    val textureCoordinates = FloatArray(count * 12)
    val colors = IntArray(count * 6)
    private val delays = FloatArray(count)
    private val velocityX = FloatArray(count)
    private val velocityY = FloatArray(count)
    private val wind = FloatArray(count)
    private val drift = 46f * density

    init {
        val random = Random(width * 31 + height)
        repeat(count) { index ->
            val x = index % columns
            val y = index / columns
            val left = x * width.toFloat() / columns
            val right = (x + 1) * width.toFloat() / columns
            val top = y * height.toFloat() / rows
            val bottom = (y + 1) * height.toFloat() / rows
            val i = index * 12
            floatArrayOf(left, top, right, top, left, bottom, right, top, right, bottom, left, bottom)
                .copyInto(textureCoordinates, i)
            delays[index] = left / width * .26f + random.nextFloat() * .07f
            velocityX[index] = (16f + random.nextFloat() * 40f) * density
            velocityY[index] = -(10f + random.nextFloat() * 54f) * density
            wind[index] = (random.nextFloat() - .5f) * 36f * density
        }
        update(0f)
    }

    fun update(progress: Float) {
        repeat(count) { index ->
            val t = ((progress - delays[index]) / .67f).coerceIn(0f, 1f)
            val shrink = 1f - .88f * t
            val i = index * 12
            val cx = (textureCoordinates[i] + textureCoordinates[i + 2]) * .5f
            val cy = (textureCoordinates[i + 1] + textureCoordinates[i + 5]) * .5f
            val dx = velocityX[index] * t + drift * t * t
            val dy = velocityY[index] * t + wind[index] * t * t
            val alpha = ((1f - t) * (1f - t) * 255f).toInt().coerceIn(0, 255)
            val color = (alpha shl 24) or 0x00FFFFFF
            repeat(6) { vertex ->
                val v = i + vertex * 2
                vertices[v] = cx + (textureCoordinates[v] - cx) * shrink + dx
                vertices[v + 1] = cy + (textureCoordinates[v + 1] - cy) * shrink + dy
                colors[index * 6 + vertex] = color
            }
        }
    }
}

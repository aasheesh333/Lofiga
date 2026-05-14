package com.dhanuk.lofiga.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var barCount: Int = 32
    var barColor: Int = 0xFF993DF5.toInt()
    var isActive: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var amplitudes = FloatArray(32) { 0f }
    private var idlePhase = 0f

    fun setAmplitudeData(data: List<Float>) {
        amplitudes = if (data.size >= barCount) {
            data.take(barCount).toFloatArray()
        } else {
            data.toFloatArray() + FloatArray(barCount - data.size)
        }
        isActive = true
        invalidate()
    }

    fun setIdle() {
        isActive = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val centerY = h / 2f
        val barW = w / barCount
        val gap = barW * 0.15f
        val rectW = barW - gap
        val maxH = h * 0.9f

        if (!isActive) {
            idlePhase = (idlePhase + 0.05f) % (2f * kotlin.math.PI.toFloat())
        }

        for (i in 0 until barCount) {
            val x = i * barW + gap / 2f

            val amp = if (isActive) {
                amplitudes.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            } else {
                val t = i.toFloat() / barCount * 2f * kotlin.math.PI.toFloat()
                (0.15f + 0.05f * kotlin.math.sin((3f * t + idlePhase * 0.5f).toDouble())).toFloat()
                    .coerceIn(0.05f, 0.5f)
            }

            val barH = amp * maxH
            paint.color = barColor
            paint.alpha = (76 + (179 * amp).toInt()).coerceIn(0, 255)

            canvas.drawRoundRect(
                x, centerY - barH / 2f,
                x + rectW, centerY + barH / 2f,
                2f, 2f, paint
            )
        }

        if (!isActive) {
            postOnAnimation { invalidate() }
        }
    }
}

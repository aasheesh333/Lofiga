package com.dhanuk.lofiga.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.pow
import kotlin.math.sqrt

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var barCount: Int = 32
    var mirrorMode: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var amplitudes = FloatArray(barCount) { 0f }
    private var idlePhase = 0f
    private var isActive = false

    private var gradientColors = intArrayOf(
        Color.rgb(0, 220, 0),
        Color.rgb(220, 220, 0),
        Color.rgb(220, 60, 60)
    )

    private val smoothed = FloatArray(barCount) { 0f }
    private val peaks = FloatArray(barCount) { 0f }
    private val peakHolds = IntArray(barCount) { 0 }
    private val fallSpeeds = FloatArray(barCount) { 0f }

    private var barGradient: LinearGradient? = null
    private var lastHeight = 0f

    fun setAmplitudeData(data: List<Float>) {
        val raw = FloatArray(barCount) { i ->
            data.getOrElse(i) { 0f }.coerceIn(0f, 1f)
        }
        val hasActualData = raw.any { it > 0.01f }

        if (!hasActualData) {
            isActive = false
            invalidate()
            return
        }

        isActive = true
        val smoothing = 0.35f
        for (i in 0 until barCount) {
            smoothed[i] += (raw[i] - smoothed[i]) * smoothing

            if (smoothed[i] >= peaks[i]) {
                peaks[i] = smoothed[i]
                peakHolds[i] = 10
                fallSpeeds[i] = 0f
            } else if (peakHolds[i] > 0) {
                peakHolds[i]--
            } else {
                fallSpeeds[i] += 0.002f
                peaks[i] = (peaks[i] - fallSpeeds[i] * peaks[i]).coerceAtLeast(0f)
            }
        }
        amplitudes = smoothed.copyOf()
        invalidate()
    }

    fun setIdle() {
        isActive = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            val hf = h.toFloat()
            barGradient = LinearGradient(
                0f, 0f, 0f, hf,
                gradientColors,
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            lastHeight = hf
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        if (!isActive) {
            idlePhase = (idlePhase + 0.05f) % (2f * kotlin.math.PI.toFloat())
        }

        val centerX = w / 2f
        val barW = centerX / barCount
        val gap = barW * 0.2f
        val rectW = barW - gap
        val maxH = h * 0.85f

        if (barGradient == null || lastHeight != h) {
            barGradient = LinearGradient(
                0f, 0f, 0f, h,
                gradientColors,
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            lastHeight = h
        }

        paint.shader = barGradient

        for (i in 0 until barCount) {
            val amp = if (isActive) {
                amplitudes.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            } else {
                val t = i.toFloat() / barCount * 2f * kotlin.math.PI.toFloat()
                (0.12f + 0.04f * kotlin.math.sin((3f * t + idlePhase * 0.5f).toDouble())).toFloat()
                    .coerceIn(0.05f, 0.4f)
            }

            val barH = amp * maxH

            if (mirrorMode) {
                val leftX = centerX - (i + 1) * barW + gap / 2f
                val rightX = centerX + i * barW + gap / 2f
                drawBar(canvas, leftX, h, rectW, barH)
                drawBar(canvas, rightX, h, rectW, barH)
            } else {
                val x = i * barW + gap / 2f
                drawBar(canvas, x, h, rectW, barH)
            }

            if (isActive) {
                val peakH = peaks.getOrElse(i) { 0f } * maxH
                if (peakH > 2f) {
                    paint.shader = null
                    paint.color = Color.argb(180, 255, 255, 255)
                    val cx = if (mirrorMode) {
                        centerX - (i + 1) * barW + gap / 2f + rectW / 2f
                    } else {
                        i * barW + gap / 2f + rectW / 2f
                    }
                    canvas.drawCircle(cx, h - peakH, 3f, paint)
                    if (mirrorMode) {
                        canvas.drawCircle(centerX + i * barW + gap / 2f + rectW / 2f, h - peakH, 3f, paint)
                    }
                    paint.shader = barGradient
                }
            }
        }

        paint.shader = null
        paint.alpha = 255

        if (isActive) {
            val hasActivePeaks = peaks.any { it > 0.01f }
            if (hasActivePeaks) {
                postOnAnimation { invalidate() }
            }
        } else {
            postOnAnimation { invalidate() }
        }
    }

    private fun drawBar(canvas: Canvas, x: Float, h: Float, rectW: Float, barH: Float) {
        if (barH < 1f) return
        val top = h - barH
        canvas.drawRoundRect(x, top, x + rectW, h, 2f, 2f, paint)
    }
}

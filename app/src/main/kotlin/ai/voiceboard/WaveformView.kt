package ai.voiceboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max

/**
 * Thin, long, scrolling amplitude waveform — a live visual pulse of the mic input.
 *
 * Bars are mirrored around a horizontal center line with rounded caps and a soft
 * left-to-right gradient. New samples enter on the right and scroll left, like a
 * live audio monitor. Values are normalized 0f..1f (see [WavRecorder]).
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val maxBars = 56
    private val samples = ArrayDeque<Float>().apply { repeat(maxBars) { addLast(0.02f) } }

    // Smoothly animates each new sample in instead of popping to full height.
    private var displayLevels = FloatArray(maxBars) { 0.02f }
    private var targetLevels = FloatArray(maxBars) { 0.02f }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            var changed = false
            for (i in displayLevels.indices) {
                val d = targetLevels[i] - displayLevels[i]
                if (kotlin.math.abs(d) > 0.001f) {
                    displayLevels[i] += d * 0.35f
                    changed = true
                }
            }
            if (changed) invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        barPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(
                Color.parseColor("#4A6B3A"),
                Color.parseColor("#9FD97A"),
                Color.parseColor("#4A6B3A")
            ),
            null, Shader.TileMode.CLAMP
        )
    }

    /** Push a new normalized amplitude (0f..1f). Call from the main thread. */
    fun pushAmplitude(level: Float) {
        samples.removeFirst()
        samples.addLast(level.coerceIn(0f, 1f))
        samples.forEachIndexed { i, v -> targetLevels[i] = max(v, 0.02f) }
    }

    /** Resets to a flat idle line (e.g. when recording starts/stops). */
    fun reset() {
        for (i in 0 until maxBars) {
            samples[i] = 0.02f
            targetLevels[i] = 0.02f
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f
        val gap = w / maxBars
        val barWidth = gap * 0.55f

        for (i in 0 until maxBars) {
            val level = displayLevels[i]
            val barHeight = max(h * 0.06f, h * 0.92f * level)
            val cx = gap * i + gap / 2f
            canvas.drawRoundRect(
                cx - barWidth / 2f, midY - barHeight / 2f,
                cx + barWidth / 2f, midY + barHeight / 2f,
                barWidth / 2f, barWidth / 2f,
                barPaint
            )
        }
    }
}

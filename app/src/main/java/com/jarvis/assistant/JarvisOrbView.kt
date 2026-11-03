package com.jarvis.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated holographic Jarvis indicator.
 * Cyan = idle, orange = listening, violet = processing.
 */
class JarvisOrbView(context: Context) : View(context) {
    enum class Mode { IDLE, LISTENING, THINKING }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotation = 0f
    private var pulse = 0f
    private var mode = Mode.IDLE
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotation = it.animatedFraction * 360f
            pulse = (sin(it.animatedFraction * Math.PI * 2.0).toFloat() + 1f) / 2f
            invalidate()
        }
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        animator.start()
        contentDescription = "Джарвис"
    }

    fun setMode(next: Mode) {
        if (mode != next) {
            mode = next
            invalidate()
        }
    }

    private fun color(): Int = when (mode) {
        Mode.IDLE -> Color.rgb(0, 190, 255)
        Mode.LISTENING -> Color.rgb(255, 145, 20)
        Mode.THINKING -> Color.rgb(160, 75, 255)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = width.coerceAtMost(height) * 0.39f
        val c = color()
        val glow = 7f + pulse * 5f

        // Soft holographic core.
        paint.style = Paint.Style.FILL
        paint.color = c
        paint.alpha = 28
        paint.setShadowLayer(glow * density, 0f, 0f, c)
        canvas.drawCircle(cx, cy, radius * (0.88f + pulse * 0.035f), paint)
        paint.clearShadowLayer()
        paint.alpha = 255

        // Main luminous rings.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * density
        paint.color = c
        paint.alpha = 210
        paint.setShadowLayer(8f * density, 0f, 0f, c)
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, rotation, 235f, false, paint)
        paint.strokeWidth = 1f * density
        paint.alpha = 100
        canvas.drawArc(oval, rotation + 180f, 75f, false, paint)
        paint.clearShadowLayer()

        // Segmented outer orbit.
        val outer = radius * 1.18f
        paint.strokeWidth = 2f * density
        paint.alpha = 125
        val outerRect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
        for (i in 0 until 12) {
            val start = rotation * 0.65f + i * 30f
            canvas.drawArc(outerRect, start, if (i % 3 == 0) 13f else 5f, false, paint)
        }

        // Small moving signal points.
        paint.style = Paint.Style.FILL
        paint.alpha = 230
        for (i in 0 until 3) {
            val angle = Math.toRadians((rotation + i * 120f).toDouble())
            val orbit = radius * 1.18f
            canvas.drawCircle(
                cx + cos(angle).toFloat() * orbit,
                cy + sin(angle).toFloat() * orbit,
                (2.2f + pulse) * density,
                paint
            )
        }

        // Central J.
        paint.color = Color.WHITE
        paint.alpha = 245
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        paint.textSize = radius * 0.88f
        paint.style = Paint.Style.FILL
        paint.setShadowLayer(9f * density, 0f, 0f, c)
        canvas.drawText("J", cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
        paint.clearShadowLayer()

        // Listening waveform around the center.
        if (mode == Mode.LISTENING) {
            paint.color = c
            paint.alpha = 220
            paint.strokeWidth = 2f * density
            paint.style = Paint.Style.STROKE
            for (i in -4..4) {
                val x = cx + i * 7f * density
                val h = (7f + ((i * i + (rotation / 12f).toInt()) % 5) * 3f) * density
                canvas.drawLine(x, cy - h, x, cy + h, paint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

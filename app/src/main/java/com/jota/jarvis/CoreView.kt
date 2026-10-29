package com.jota.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

enum class CoreState { IDLE, LISTENING, PROCESSING, DONE }

class CoreView(context: Context) : View(context) {

    var state: CoreState = CoreState.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private var phase = 0f
    private val rect = RectF()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#48DFF0")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
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

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) / 2f
        if (base <= 0f) return

        val speed = when (state) {
            CoreState.IDLE -> 1
            CoreState.LISTENING -> 2
            CoreState.PROCESSING -> 4
            CoreState.DONE -> 1
        }
        val amp = when (state) {
            CoreState.IDLE -> 0.03f
            CoreState.LISTENING -> 0.08f
            CoreState.PROCESSING -> 0.04f
            CoreState.DONE -> 0.02f
        }

        val t = phase * 2f * PI.toFloat() * speed
        val r = base * (0.30f + amp * sin(t))

        // brilho externo
        glowPaint.shader = RadialGradient(
            cx, cy, r * 2.4f,
            intArrayOf(Color.argb(150, 72, 223, 240), Color.argb(0, 72, 223, 240)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 2.4f, glowPaint)

        // núcleo
        corePaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.parseColor("#48DFF0"), Color.parseColor("#3678FF")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, corePaint)

        // anéis
        ringPaint.strokeWidth = base * 0.012f
        val radii = floatArrayOf(1.35f, 1.7f, 2.05f)
        for (i in radii.indices) {
            val rr = r * radii[i]
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            ringPaint.alpha = 220 - i * 55
            if (state == CoreState.DONE) {
                canvas.drawCircle(cx, cy, rr, ringPaint)
            } else {
                val dir = if (i % 2 == 0) 1f else -1f
                val start = dir * phase * 360f * speed + i * 70f
                canvas.drawArc(rect, start, 110f, false, ringPaint)
                canvas.drawArc(rect, start + 180f, 70f, false, ringPaint)
            }
        }
    }
}

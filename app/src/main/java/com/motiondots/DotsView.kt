package com.motiondots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the dots and moves them with a spring driven by the phone's acceleration.
 * Used both as the full-screen overlay and as the preview inside the settings screen
 * (with [demo] = true it animates itself with fake motion).
 */
class DotsView(context: Context, private val demo: Boolean = false) : View(context) {

    private val settings = DotSettings(context)
    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var dots = FloatArray(0) // x0,y0,x1,y1,...

    // Cached settings
    private var radiusPx = 0f
    private var scalePx = 0f       // px of travel per m/s^2
    private var maxTravelPx = 0f
    private var stiffness = 100f
    private var invertSign = 1f
    private var baseAlpha = 255
    private var outline = true
    private var fadeStill = false
    private var color = Color.WHITE

    // Input (screen space: right / up, m/s^2)
    @Volatile private var accRight = 0f
    @Volatile private var accUp = 0f

    // Physics state
    private var posX = 0f
    private var posY = 0f
    private var velX = 0f
    private var velY = 0f
    private var alphaMul = 1f
    private var lastMotion = SystemClock.uptimeMillis()
    private var lastFrame = 0L

    init {
        reload()
    }

    /** Re-read settings (call after any change). */
    fun reload() {
        val s = settings
        radiusPx = s.sizeDp * density / 2f
        scalePx = 10f * density * s.sensitivity / 100f
        maxTravelPx = 70f * density * max(1f, s.sensitivity / 100f)
        stiffness = 40f + s.responsiveness * 4f
        invertSign = if (s.invert) -1f else 1f
        baseAlpha = (s.opacity.coerceIn(5, 100) * 255 / 100)
        outline = s.outline
        fadeStill = s.fadeWhenStill
        color = s.color

        fill.color = color
        stroke.strokeWidth = max(1f, density)
        val lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        stroke.color = if (lum > 0.5) Color.BLACK else Color.WHITE

        buildDots()
        postInvalidateOnAnimation()
    }

    /** Feed the latest acceleration in screen space (right-positive, up-positive, m/s^2). */
    fun setAcceleration(right: Float, up: Float) {
        accRight = right
        accUp = up
        if (abs(right) > 0.35f || abs(up) > 0.35f) lastMotion = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildDots()
    }

    private fun buildDots() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val m = settings.marginDp * density + radiusPx
        val n = settings.count.coerceAtLeast(2)
        val out = ArrayList<Float>()
        fun add(x: Float, y: Float) { out.add(x); out.add(y) }
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        when (settings.pattern) {
            DotSettings.PATTERN_EDGES -> for (i in 0 until n) {
                val y = lerp(m, h - m, i / (n - 1f))
                add(m, y); add(w - m, y)
            }
            DotSettings.PATTERN_FRAME -> {
                val nh = max(2, (n * w / h).roundToInt() + 1)
                for (i in 0 until nh) {
                    val x = lerp(m, w - m, i / (nh - 1f))
                    add(x, m); add(x, h - m)
                }
                for (i in 1 until n - 1) {
                    val y = lerp(m, h - m, i / (n - 1f))
                    add(m, y); add(w - m, y)
                }
            }
            DotSettings.PATTERN_GRID -> {
                val cols = max(2, (n * w / h).roundToInt())
                for (r in 0 until n) for (c in 0 until cols) {
                    add(lerp(m, w - m, c / (cols - 1f)), lerp(m, h - m, r / (n - 1f)))
                }
            }
            else -> { // ring
                val total = n * 2
                for (i in 0 until total) {
                    val a = 2.0 * PI * i / total
                    add(w / 2f + (w / 2f - m) * cos(a).toFloat(), h / 2f + (h / 2f - m) * sin(a).toFloat())
                }
            }
        }
        dots = out.toFloatArray()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0.001f, 0.05f)
        lastFrame = now

        var aR = accRight
        var aU = accUp
        if (demo) {
            val t = now / 1000.0
            aR = (3.5 * sin(t * 1.3)).toFloat()
            aU = (2.5 * sin(t * 0.8 + 1.0)).toFloat()
            lastMotion = now
        }
        // Dead zone so sensor noise doesn't make the dots jitter.
        if (abs(aR) < 0.12f) aR = 0f
        if (abs(aU) < 0.12f) aU = 0f

        // Dots drift opposite to acceleration (like the world sliding past); up-accel pushes dots down.
        val targetX = (-aR * scalePx * invertSign).coerceIn(-maxTravelPx, maxTravelPx)
        val targetY = (aU * scalePx * invertSign).coerceIn(-maxTravelPx, maxTravelPx)

        // Damped spring, sub-stepped for stability.
        val damping = 2f * sqrt(stiffness) * 0.75f
        val steps = 3
        val h = dt / steps
        repeat(steps) {
            velX += (stiffness * (targetX - posX) - damping * velX) * h
            velY += (stiffness * (targetY - posY) - damping * velY) * h
            posX += velX * h
            posY += velY * h
        }

        val wantMul = if (fadeStill && now - lastMotion > 3000) 0.25f else 1f
        alphaMul += (wantMul - alphaMul) * min(1f, dt * 2.5f)

        val a = (baseAlpha * alphaMul).roundToInt().coerceIn(0, 255)
        fill.alpha = a
        stroke.alpha = a * 6 / 10
        var i = 0
        while (i < dots.size) {
            val x = dots[i] + posX
            val y = dots[i + 1] + posY
            canvas.drawCircle(x, y, radiusPx, fill)
            if (outline) canvas.drawCircle(x, y, radiusPx, stroke)
            i += 2
        }

        val moving = abs(velX) > 0.5f || abs(velY) > 0.5f ||
            abs(targetX - posX) > 0.2f || abs(targetY - posY) > 0.2f ||
            abs(wantMul - alphaMul) > 0.01f || (fadeStill && now - lastMotion <= 3000)
        if (demo || moving) postInvalidateOnAnimation() else lastFrame = 0L
    }
}

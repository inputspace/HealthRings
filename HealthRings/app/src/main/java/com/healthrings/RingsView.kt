package com.healthrings

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class RingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var stepsPct: Float = 0f
        set(value) { field = value; invalidate() }
    var calPct: Float = 0f
        set(value) { field = value; invalidate() }
    var movePct: Float = 0f
        set(value) { field = value; invalidate() }

    private val STROKE = 28f
    private val GAP = 20f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        val r1 = minOf(cx, cy) - STROKE / 2f - 4f
        val r2 = r1 - STROKE - GAP
        val r3 = r2 - STROKE - GAP

        // Steps ring (outer) — green
        drawRing(canvas, cx, cy, r1, 0x221D9E75.toInt(), 0xFF1D9E75.toInt(), stepsPct)
        // Calories ring (middle) — orange
        drawRing(canvas, cx, cy, r2, 0x22D85A30.toInt(), 0xFFD85A30.toInt(), calPct)
        // Move ring (inner) — blue
        drawRing(canvas, cx, cy, r3, 0x22378ADD.toInt(), 0xFF378ADD.toInt(), movePct)
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, r: Float, trackColor: Int, fillColor: Int, pct: Float) {
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        trackPaint.color = trackColor
        trackPaint.strokeWidth = STROKE
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)
        if (pct > 0.01f) {
            fillPaint.color = fillColor
            fillPaint.strokeWidth = STROKE
            canvas.drawArc(oval, -90f, (360f * pct.coerceIn(0f, 1f)), false, fillPaint)
        }
    }
}

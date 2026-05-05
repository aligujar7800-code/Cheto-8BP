package com.cheto.eightball

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.sqrt

class GuidelineView(context: Context) : View(context) {

    var showBankShots = true
    var showCueReflection = true
    var lineAlpha: Int = 200

    // Aim data from MemoryManager
    var aimResult: MemoryManager.AimResult? = null

    // Debug info
    var debugStatus: String = "INITIALIZING..."
    var frameCount: Int = 0
    var showDebug: Boolean = true 

    // ---- Paints ----
    private val aimLinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val targetLinePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val bankBouncePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val debugPaint = Paint().apply {
        color = Color.CYAN
        textSize = 24f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameCount++

        val aim = aimResult
        if (aim != null) {
            drawFromMemory(canvas, aim)
        }

        if (showDebug) {
            canvas.drawText("CHETO ACTIVE | FPS: $frameCount", 50f, 80f, debugPaint)
            canvas.drawText("STATUS: $debugStatus", 50f, 110f, debugPaint)
        }
    }

    private fun drawFromMemory(canvas: Canvas, aim: MemoryManager.AimResult) {
        // Here we map the memory coordinates (usually normalized 0-1 or game units)
        // to the actual screen pixels.
        // For 8BP, we usually need a specific mapping constant.
        
        val balls = aim.balls
        if (balls.isEmpty()) return

        // Cue ball is usually the first one in the list or has a specific type
        val cueBall = balls.find { it.type == 0 } ?: balls[0]
        
        val cx = mapX(cueBall.x)
        val cy = mapY(cueBall.y)
        val angle = aim.cueAngle

        // Draw basic direction line for now
        val endX = cx + Math.cos(angle.toDouble()).toFloat() * 1000
        val endY = cy + Math.sin(angle.toDouble()).toFloat() * 1000
        
        canvas.drawLine(cx, cy, endX, endY, aimLinePaint)
        
        // Draw all other balls as small circles for verification
        for (ball in balls) {
            canvas.drawCircle(mapX(ball.x), mapY(ball.y), 15f, targetLinePaint)
        }
    }

    private fun mapX(x: Float): Float = x * width
    private fun mapY(y: Float): Float = y * height
}

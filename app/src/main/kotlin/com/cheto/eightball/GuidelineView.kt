package com.cheto.eightball

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class GuidelineView(context: Context) : View(context) {

    var showHorizontal = true
    var showVertical = true
    var showCircle = true
    var showCrosshair = true
    var showPowerGuide = true
    var showFullScan = true
    var showBankShots = true
    var showSpinCurve = true
    var autoAimEnabled = false
    var breakChance = 45

    private val ballPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val bankPaint = Paint().apply {
        color = Color.MAGENTA
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }

    private val curvePaint = Paint().apply {
        color = Color.rgb(255, 165, 0) // Orange
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val targetPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val centerPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 40f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Center point
        val centerX = width / 2
        val centerY = height / 2

        // Draw crosshair at center
        if (showCrosshair) {
            canvas.drawLine(centerX - 50, centerY, centerX + 50, centerY, centerPaint)
            canvas.drawLine(centerX, centerY - 50, centerX, centerY + 50, centerPaint)
        }

        // Draw basic guide lines
        if (showHorizontal) {
            canvas.drawLine(0f, centerY, width, centerY, paint)
        }
        if (showVertical) {
            canvas.drawLine(centerX, 0f, centerX, height, paint)
        }
        
        // Circular guide
        if (showCircle) {
            canvas.drawCircle(centerX, centerY, 100f, paint)
        }

        // Power Guide
        if (showPowerGuide) {
            val startY = centerY - 200
            val endY = centerY + 200
            canvas.drawLine(width - 100, startY, width - 100, endY, paint)
            for (i in 0..10) {
                val y = startY + (i * 40)
                canvas.drawLine(width - 110, y, width - 90, y, paint)
            }
            canvas.drawText("POWER", width - 100, startY - 20, textPaint)
        }

        // Break Chance Text
        canvas.drawText("BREAK CHANCE: $breakChance%", centerX, 100f, textPaint)

        // Full Table Scan / Multi-Ball Prediction
        if (showFullScan) {
            drawMultiBallPrediction(canvas, width, height)
        }
    }

    private fun drawMultiBallPrediction(canvas: Canvas, width: Float, height: Float) {
        val centerX = width / 2
        val centerY = height / 2

        // Simulate Cue Ball Trajectory
        // In a real tool, these points would come from Image Recognition
        val cueX = centerX
        val cueY = centerY + 300
        
        // Line from Cue Ball to Target
        canvas.drawLine(cueX, cueY, centerX, centerY, ballPaint)
        canvas.drawCircle(centerX, centerY, 30f, ballPaint) // Prediction of where cue ball hits

        // Reflected line (where white ball goes)
        canvas.drawLine(centerX, centerY, centerX - 200, centerY - 200, ballPaint)
        
        // Spin Curve (English) Prediction
        if (showSpinCurve) {
            val path = android.graphics.Path()
            path.moveTo(centerX - 200, centerY - 200)
            path.quadTo(centerX - 300, centerY - 150, centerX - 400, centerY - 50)
            canvas.drawPath(path, curvePaint)
            canvas.drawText("SPIN CURVE", centerX - 400, centerY - 30, textPaint)
        } else {
            canvas.drawText("CUE STOP", centerX - 200, centerY - 220, textPaint)
        }

        // Target Ball trajectory to Pocket
        canvas.drawLine(centerX, centerY, centerX + 400, centerY - 400, targetPaint)
        
        // Bank Shot Prediction (Bouncing off walls)
        if (showBankShots) {
            // Simulate bounce off top rail
            canvas.drawLine(centerX + 400, centerY - 400, centerX + 600, centerY - 200, bankPaint)
            canvas.drawCircle(centerX + 600, centerY - 200, 30f, bankPaint)
            canvas.drawText("BANK POCKET", centerX + 600, centerY - 220, textPaint)
        } else {
            canvas.drawCircle(centerX + 400, centerY - 400, 30f, targetPaint)
            canvas.drawText("POCKET", centerX + 400, centerY - 420, textPaint)
        }

        // Other balls prediction (Simulated)
        for (i in 1..3) {
            val bx = centerX + (i * 150) - 300
            val by = centerY - (i * 100)
            canvas.drawCircle(bx, by, 25f, targetPaint)
            canvas.drawLine(bx, by, bx + 100, by - 200, targetPaint)
            
            if (showBankShots) {
                 canvas.drawLine(bx + 100, by - 200, bx + 50, by - 300, bankPaint)
            }
        }
        
        if (autoAimEnabled) {
             canvas.drawText("MAGNETIC AIM: ON", centerX, centerY + 200, textPaint)
             canvas.drawCircle(centerX, centerY, 50f, centerPaint)
        }
    }
}

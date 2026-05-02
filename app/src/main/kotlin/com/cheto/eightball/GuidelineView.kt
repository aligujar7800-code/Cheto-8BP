package com.cheto.eightball

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.sqrt

class GuidelineView(context: Context) : View(context) {

    var showHorizontal = false
    var showVertical = false
    var showCircle = false
    var showCrosshair = false
    var showPowerGuide = false
    var showFullScan = true
    var showBankShots = true
    var showSpinCurve = false
    var autoAimEnabled = false
    var breakChance = 45
    
    // User can adjust transparency (0-255)
    var lineAlpha: Int = 200

    // Paints
    private val mainPathPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val targetPathPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val cueReflectPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val ghostBallPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val detectionPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    var detections: List<YoloDetector.Detection> = emptyList()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detections.isEmpty()) return

        // Update alphas
        mainPathPaint.alpha = lineAlpha
        targetPathPaint.alpha = lineAlpha
        cueReflectPaint.alpha = lineAlpha
        ghostBallPaint.alpha = lineAlpha

        val scaleX = width / 640f
        val scaleY = height / 640f

        var cueBall: YoloDetector.Detection? = null
        var guideline: YoloDetector.Detection? = null
        var playArea: YoloDetector.Detection? = null
        val targetBalls = mutableListOf<YoloDetector.Detection>()
        val holes = mutableListOf<YoloDetector.Detection>()

        // 0: Ball, 1: Force, 2: Guideline, 3: Hole, 4: Play_Area, 5: Spin, 6: White
        for (det in detections) {
            when (det.label) {
                6 -> cueBall = det
                2 -> guideline = det
                4 -> playArea = det
                0 -> targetBalls.add(det)
                3 -> holes.add(det)
            }
        }

        // We need at least the cue ball to do physics
        if (cueBall != null) {
            val cx = cueBall.x * scaleX
            val cy = cueBall.y * scaleY
            val radius = (cueBall.w * scaleX) / 2f

            // Calculate Aim Angle
            var dirX = 0f
            var dirY = 0f
            
            if (guideline != null) {
                val gx = guideline.x * scaleX
                val gy = guideline.y * scaleY
                val dx = gx - cx
                val dy = gy - cy
                val len = sqrt(dx * dx + dy * dy)
                if (len > 0) {
                    dirX = dx / len
                    dirY = dy / len
                }
            } else {
                // No guideline detected, cannot predict aim
                return
            }

            // Table bounds
            var boundsLeft = 0f
            var boundsTop = 0f
            var boundsRight = width.toFloat()
            var boundsBottom = height.toFloat()

            if (playArea != null) {
                val px = playArea.x * scaleX
                val py = playArea.y * scaleY
                val pw = playArea.w * scaleX
                val ph = playArea.h * scaleY
                boundsLeft = px - pw / 2
                boundsTop = py - ph / 2
                boundsRight = px + pw / 2
                boundsBottom = py + ph / 2
            }

            // Raycast for Collision
            var closestHitT = Float.MAX_VALUE
            var hitBall: YoloDetector.Detection? = null

            for (ball in targetBalls) {
                val bx = ball.x * scaleX
                val by = ball.y * scaleY
                
                val lx = bx - cx
                val ly = by - cy
                val tca = lx * dirX + ly * dirY
                
                if (tca < 0) continue // Ball is behind
                
                val d2 = (lx * lx + ly * ly) - (tca * tca)
                val hitRadius = radius * 2f // Two ball radii for center-to-center impact
                val radius2 = hitRadius * hitRadius
                
                if (d2 > radius2) continue // Ray misses ball
                
                val thc = sqrt(radius2 - d2)
                val t0 = tca - thc
                
                if (t0 < closestHitT && t0 > 0) {
                    closestHitT = t0
                    hitBall = ball
                }
            }

            // Draw Aim Line
            if (hitBall != null) {
                // Hit a ball
                val impactX = cx + dirX * closestHitT
                val impactY = cy + dirY * closestHitT
                
                // Draw line from cue ball to impact point
                canvas.drawLine(cx, cy, impactX, impactY, mainPathPaint)
                // Draw Ghost Ball (where cue ball will be at impact)
                canvas.drawCircle(impactX, impactY, radius, ghostBallPaint)
                
                // Calculate Target Ball Trajectory
                val bx = hitBall.x * scaleX
                val by = hitBall.y * scaleY
                
                val nx = bx - impactX
                val ny = by - impactY
                val nLen = sqrt(nx * nx + ny * ny)
                val targetDirX = nx / nLen
                val targetDirY = ny / nLen
                
                // Extend target ball line to walls (Bank calculation)
                drawRay(canvas, bx, by, targetDirX, targetDirY, boundsLeft, boundsTop, boundsRight, boundsBottom, radius, targetPathPaint, showBankShots)
                
                // Cue ball reflection (90 degrees tangent)
                val tangentX = -targetDirY
                val tangentY = targetDirX
                
                // Determine which direction the cue ball bounces
                val dot = dirX * tangentX + dirY * tangentY
                val refDirX = if (dot > 0) tangentX else -tangentX
                val refDirY = if (dot > 0) tangentY else -tangentY
                
                drawRay(canvas, impactX, impactY, refDirX, refDirY, boundsLeft, boundsTop, boundsRight, boundsBottom, radius, cueReflectPaint, false)
                
            } else {
                // Hit a wall directly
                drawRay(canvas, cx, cy, dirX, dirY, boundsLeft, boundsTop, boundsRight, boundsBottom, radius, mainPathPaint, showBankShots)
            }
        }
    }

    private fun drawRay(canvas: Canvas, startX: Float, startY: Float, dirX: Float, dirY: Float, left: Float, top: Float, right: Float, bottom: Float, radius: Float, paint: Paint, doBounces: Boolean) {
        var cx = startX
        var cy = startY
        var dx = dirX
        var dy = dirY
        
        var bounces = if (doBounces) 3 else 0
        
        for (i in 0..bounces) {
            var tX = Float.MAX_VALUE
            var tY = Float.MAX_VALUE
            var wallX = 0f
            var wallY = 0f
            var hitVertical = false
            
            if (dx > 0) {
                tX = (right - radius - cx) / dx
            } else if (dx < 0) {
                tX = (left + radius - cx) / dx
            }
            
            if (dy > 0) {
                tY = (bottom - radius - cy) / dy
            } else if (dy < 0) {
                tY = (top + radius - cy) / dy
            }
            
            val t = if (tX < tY) tX else tY
            hitVertical = tX < tY
            
            val endX = cx + dx * t
            val endY = cy + dy * t
            
            canvas.drawLine(cx, cy, endX, endY, paint)
            
            if (i < bounces) {
                // Reflect
                if (hitVertical) {
                    dx = -dx
                } else {
                    dy = -dy
                }
                cx = endX
                cy = endY
            }
        }
    }
}

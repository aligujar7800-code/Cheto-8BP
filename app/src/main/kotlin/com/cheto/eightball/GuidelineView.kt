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

    // Aim data from ScreenAnalyzer (pixel-based)
    var aimResult: ScreenAnalyzer.AimResult? = null

    // Aim data from YOLO (fallback)
    var detections: List<YoloDetector.Detection> = emptyList()

    // Scale factors for mapping capture coords -> screen coords
    var captureScaleX: Float = 1f
    var captureScaleY: Float = 1f

    // ---- Paints ----

    private val aimLinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val targetLinePaint = Paint().apply {
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

    private val bankBouncePaint = Paint().apply {
        color = Color.MAGENTA
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val ghostBallPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val pocketPaint = Paint().apply {
        color = Color.argb(180, 0, 255, 0)
        strokeWidth = 3f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Apply transparency
        aimLinePaint.alpha = lineAlpha
        targetLinePaint.alpha = lineAlpha
        cueReflectPaint.alpha = (lineAlpha * 0.7f).toInt()
        bankBouncePaint.alpha = (lineAlpha * 0.6f).toInt()
        ghostBallPaint.alpha = (lineAlpha * 0.8f).toInt()

        val aim = aimResult
        if (aim != null) {
            drawFromAimResult(canvas, aim)
            return
        }

        // Fallback: try YOLO detections
        if (detections.isNotEmpty()) {
            drawFromYolo(canvas)
        }
    }

    private fun drawFromAimResult(canvas: Canvas, aim: ScreenAnalyzer.AimResult) {
        // Map capture coordinates to screen coordinates
        val sx = captureScaleX
        val sy = captureScaleY

        val cx = aim.cueBallX * sx
        val cy = aim.cueBallY * sy
        val radius = aim.ballRadius * sx
        val dirX = aim.aimDirX
        val dirY = aim.aimDirY

        val boundsLeft = aim.tableLeft * sx
        val boundsTop = aim.tableTop * sy
        val boundsRight = aim.tableRight * sx
        val boundsBottom = aim.tableBottom * sy

        // Map ball positions
        val balls = aim.ballPositions.map { Pair(it.first * sx, it.second * sy) }

        // Raycast: check if aim hits any ball
        var closestHitT = Float.MAX_VALUE
        var hitBallIdx = -1

        for (i in balls.indices) {
            val bx = balls[i].first
            val by = balls[i].second

            val lx = bx - cx
            val ly = by - cy
            val tca = lx * dirX + ly * dirY
            if (tca < 0) continue

            val d2 = (lx * lx + ly * ly) - (tca * tca)
            val hitRadius = radius * 2f
            val radius2 = hitRadius * hitRadius
            if (d2 > radius2) continue

            val thc = sqrt(radius2 - d2)
            val t0 = tca - thc
            if (t0 < closestHitT && t0 > 0) {
                closestHitT = t0
                hitBallIdx = i
            }
        }

        if (hitBallIdx >= 0) {
            // Hit a ball
            val impactX = cx + dirX * closestHitT
            val impactY = cy + dirY * closestHitT

            // Aim line: cue ball -> impact point
            canvas.drawLine(cx, cy, impactX, impactY, aimLinePaint)
            // Ghost ball at impact
            canvas.drawCircle(impactX, impactY, radius, ghostBallPaint)

            // Target ball trajectory
            val bx = balls[hitBallIdx].first
            val by = balls[hitBallIdx].second
            val nx = bx - impactX
            val ny = by - impactY
            val nLen = sqrt(nx * nx + ny * ny)
            if (nLen > 0) {
                val targetDirX = nx / nLen
                val targetDirY = ny / nLen

                // Draw target ball path with bank bounces
                drawRay(canvas, bx, by, targetDirX, targetDirY,
                    boundsLeft, boundsTop, boundsRight, boundsBottom,
                    radius, targetLinePaint, if (showBankShots) 3 else 0)

                // Cue ball reflection after impact
                if (showCueReflection) {
                    val tangentX = -targetDirY
                    val tangentY = targetDirX
                    val dot = dirX * tangentX + dirY * tangentY
                    val refDirX = if (dot > 0) tangentX else -tangentX
                    val refDirY = if (dot > 0) tangentY else -tangentY

                    drawRay(canvas, impactX, impactY, refDirX, refDirY,
                        boundsLeft, boundsTop, boundsRight, boundsBottom,
                        radius, cueReflectPaint, 0)
                }
            }
        } else {
            // No ball hit - aim line goes to wall with bank bounces
            drawRay(canvas, cx, cy, dirX, dirY,
                boundsLeft, boundsTop, boundsRight, boundsBottom,
                radius, aimLinePaint, if (showBankShots) 3 else 0)
        }
    }

    private fun drawFromYolo(canvas: Canvas) {
        val scaleX = width / 640f
        val scaleY = height / 640f

        var cueBall: YoloDetector.Detection? = null
        var guideline: YoloDetector.Detection? = null
        var playArea: YoloDetector.Detection? = null
        val targetBalls = mutableListOf<YoloDetector.Detection>()

        for (det in detections) {
            when (det.label) {
                6 -> cueBall = det
                2 -> guideline = det
                4 -> playArea = det
                0 -> targetBalls.add(det)
            }
        }

        if (cueBall == null || guideline == null) return

        val cx = cueBall.x * scaleX
        val cy = cueBall.y * scaleY
        val radius = (cueBall.w * scaleX) / 2f

        val gx = guideline.x * scaleX
        val gy = guideline.y * scaleY
        val dx = gx - cx
        val dy = gy - cy
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0) return
        val dirX = dx / len
        val dirY = dy / len

        var boundsLeft = 0f
        var boundsTop = 0f
        var boundsRight = width.toFloat()
        var boundsBottom = height.toFloat()

        if (playArea != null) {
            boundsLeft = (playArea.x - playArea.w / 2) * scaleX
            boundsTop = (playArea.y - playArea.h / 2) * scaleY
            boundsRight = (playArea.x + playArea.w / 2) * scaleX
            boundsBottom = (playArea.y + playArea.h / 2) * scaleY
        }

        // Raycast against balls
        var closestHitT = Float.MAX_VALUE
        var hitBall: YoloDetector.Detection? = null

        for (ball in targetBalls) {
            val bx = ball.x * scaleX
            val by = ball.y * scaleY
            val lx = bx - cx
            val ly = by - cy
            val tca = lx * dirX + ly * dirY
            if (tca < 0) continue
            val d2 = (lx * lx + ly * ly) - (tca * tca)
            val hitRadius = radius * 2f
            if (d2 > hitRadius * hitRadius) continue
            val thc = sqrt(hitRadius * hitRadius - d2)
            val t0 = tca - thc
            if (t0 < closestHitT && t0 > 0) {
                closestHitT = t0
                hitBall = ball
            }
        }

        if (hitBall != null) {
            val impactX = cx + dirX * closestHitT
            val impactY = cy + dirY * closestHitT
            canvas.drawLine(cx, cy, impactX, impactY, aimLinePaint)
            canvas.drawCircle(impactX, impactY, radius, ghostBallPaint)

            val bx = hitBall.x * scaleX
            val by = hitBall.y * scaleY
            val nx = bx - impactX
            val ny = by - impactY
            val nLen = sqrt(nx * nx + ny * ny)
            if (nLen > 0) {
                val tDirX = nx / nLen
                val tDirY = ny / nLen
                drawRay(canvas, bx, by, tDirX, tDirY, boundsLeft, boundsTop, boundsRight, boundsBottom, radius, targetLinePaint, if (showBankShots) 3 else 0)

                if (showCueReflection) {
                    val tangentX = -tDirY
                    val tangentY = tDirX
                    val dot = dirX * tangentX + dirY * tangentY
                    drawRay(canvas, impactX, impactY,
                        if (dot > 0) tangentX else -tangentX,
                        if (dot > 0) tangentY else -tangentY,
                        boundsLeft, boundsTop, boundsRight, boundsBottom, radius, cueReflectPaint, 0)
                }
            }
        } else {
            drawRay(canvas, cx, cy, dirX, dirY, boundsLeft, boundsTop, boundsRight, boundsBottom, radius, aimLinePaint, if (showBankShots) 3 else 0)
        }
    }

    private fun drawRay(canvas: Canvas, startX: Float, startY: Float, dirX: Float, dirY: Float,
                        left: Float, top: Float, right: Float, bottom: Float,
                        radius: Float, paint: Paint, maxBounces: Int) {
        var cx = startX
        var cy = startY
        var dx = dirX
        var dy = dirY
        val usePaint = if (maxBounces > 0) paint else paint

        for (i in 0..maxBounces) {
            var tX = Float.MAX_VALUE
            var tY = Float.MAX_VALUE

            if (dx > 0) tX = (right - radius - cx) / dx
            else if (dx < 0) tX = (left + radius - cx) / dx

            if (dy > 0) tY = (bottom - radius - cy) / dy
            else if (dy < 0) tY = (top + radius - cy) / dy

            if (tX <= 0 && tY <= 0) break
            if (tX <= 0) tX = Float.MAX_VALUE
            if (tY <= 0) tY = Float.MAX_VALUE

            val t = minOf(tX, tY)
            if (t == Float.MAX_VALUE || t <= 0) break
            val hitVertical = tX < tY

            val endX = cx + dx * t
            val endY = cy + dy * t

            val drawPaint = if (i == 0) paint else bankBouncePaint
            canvas.drawLine(cx, cy, endX, endY, drawPaint)

            if (i < maxBounces) {
                if (hitVertical) dx = -dx else dy = -dy
                cx = endX
                cy = endY
            }
        }
    }
}

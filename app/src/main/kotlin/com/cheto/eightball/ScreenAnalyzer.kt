package com.cheto.eightball

import android.graphics.Bitmap
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance pixel-based screen analyzer for 8 Ball Pool.
 * Optimized with table caching, smart sampling, and minimal allocations.
 */
class ScreenAnalyzer {

    data class AimResult(
        val cueBallX: Float,
        val cueBallY: Float,
        val aimDirX: Float,
        val aimDirY: Float,
        val tableLeft: Float,
        val tableTop: Float,
        val tableRight: Float,
        val tableBottom: Float,
        val ballPositions: List<Pair<Float, Float>>,
        val ballRadius: Float
    )

    // Cached table bounds - table doesn't move between frames
    private var cachedTableLeft = -1
    private var cachedTableTop = -1
    private var cachedTableRight = -1
    private var cachedTableBottom = -1
    private var tableCacheFrames = 0
    private val TABLE_CACHE_LIFETIME = 30 // Recalculate every 30 frames

    // Pre-allocated arrays to avoid GC
    private val hsv = FloatArray(3)
    private var pixelBuffer: IntArray? = null

    // Thresholds
    private val WHITE_THRESH = 215
    private val TABLE_HUE_MIN = 170f
    private val TABLE_HUE_MAX = 215f
    private val TABLE_SAT_MIN = 0.15f
    private val BALL_SAT_MIN = 0.35f

    // Pre-computed radial scan directions (every 3 degrees)
    private val scanAngles: Int = 120
    private val cosTable = FloatArray(scanAngles)
    private val sinTable = FloatArray(scanAngles)

    init {
        for (i in 0 until scanAngles) {
            val rad = Math.toRadians((i * 3).toDouble())
            cosTable[i] = cos(rad).toFloat()
            sinTable[i] = sin(rad).toFloat()
        }
    }

    fun analyze(bitmap: Bitmap): AimResult? {
        val w = bitmap.width
        val h = bitmap.height

        // Reuse pixel buffer if same size
        if (pixelBuffer == null || pixelBuffer!!.size != w * h) {
            pixelBuffer = IntArray(w * h)
        }
        val pixels = pixelBuffer!!
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Step 1: Get table bounds (cached)
        tableCacheFrames++
        if (cachedTableLeft < 0 || tableCacheFrames >= TABLE_CACHE_LIFETIME) {
            findTableBounds(pixels, w, h)
            tableCacheFrames = 0
        }

        val tL = cachedTableLeft
        val tT = cachedTableTop
        val tR = cachedTableRight
        val tB = cachedTableBottom

        if (tR - tL < w * 0.25f || tB - tT < h * 0.15f) return null

        // Step 2: Find cue ball (white cluster) - sample every 6th pixel for speed
        var bestCx = 0f
        var bestCy = 0f
        var bestClusterSize = 0
        var bestRadius = 12f

        // Scan for white pixel clusters
        val step = 6
        val whiteGridW = (tR - tL) / step + 1
        val whiteGridH = (tB - tT) / step + 1
        val whiteGrid = BooleanArray(whiteGridW * whiteGridH)
        
        for (gy in 0 until whiteGridH) {
            val y = tT + gy * step
            if (y >= h) break
            val rowOff = y * w
            for (gx in 0 until whiteGridW) {
                val x = tL + gx * step
                if (x >= w) break
                val px = pixels[rowOff + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (r > WHITE_THRESH && g > WHITE_THRESH && b > WHITE_THRESH) {
                    whiteGrid[gy * whiteGridW + gx] = true
                }
            }
        }

        // Simple flood-fill to find clusters in grid
        val visited = BooleanArray(whiteGridW * whiteGridH)
        for (startIdx in whiteGrid.indices) {
            if (!whiteGrid[startIdx] || visited[startIdx]) continue
            
            var sumX = 0L
            var sumY = 0L
            var count = 0
            var minX = Int.MAX_VALUE
            var maxX = 0
            var minY = Int.MAX_VALUE
            var maxY = 0

            // BFS flood fill
            val queue = ArrayDeque<Int>(64)
            queue.add(startIdx)
            visited[startIdx] = true

            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst()
                val gx = idx % whiteGridW
                val gy = idx / whiteGridW
                val px = tL + gx * step
                val py = tT + gy * step

                sumX += px
                sumY += py
                count++
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py

                // Check 4 neighbors
                val neighbors = intArrayOf(
                    if (gx > 0) idx - 1 else -1,
                    if (gx < whiteGridW - 1) idx + 1 else -1,
                    if (gy > 0) idx - whiteGridW else -1,
                    if (gy < whiteGridH - 1) idx + whiteGridW else -1
                )
                for (n in neighbors) {
                    if (n >= 0 && n < whiteGrid.size && whiteGrid[n] && !visited[n]) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
            }

            // Check if this cluster looks like a ball (roughly circular, decent size)
            val clusterW = maxX - minX
            val clusterH = maxY - minY
            val aspect = if (clusterH > 0) clusterW.toFloat() / clusterH else 0f

            if (count > bestClusterSize && count >= 3 && aspect in 0.4f..2.5f) {
                bestClusterSize = count
                bestCx = sumX.toFloat() / count
                bestCy = sumY.toFloat() / count
                bestRadius = ((clusterW + clusterH) / 4f).coerceIn(8f, 40f)
            }
        }

        if (bestClusterSize < 3) return null

        val cueBallX = bestCx
        val cueBallY = bestCy
        val ballRadius = bestRadius

        // Step 3: Find aim direction with radial scan from cue ball
        val scanDist = (tR - tL) / 3 // Scan up to 1/3 of table width
        var bestAngleIdx = -1
        var bestLineScore = 0

        for (ai in 0 until scanAngles) {
            val dx = cosTable[ai]
            val dy = sinTable[ai]
            var lineScore = 0
            var gapCount = 0

            var d = (ballRadius + 4).toInt()
            while (d < scanDist) {
                val sx = (cueBallX + dx * d).toInt()
                val sy = (cueBallY + dy * d).toInt()

                if (sx < tL || sx >= tR || sy < tT || sy >= tB) break
                if (sx < 0 || sx >= w || sy < 0 || sy >= h) break

                val px = pixels[sy * w + sx]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF

                if (r > WHITE_THRESH && g > WHITE_THRESH && b > WHITE_THRESH) {
                    lineScore++
                    gapCount = 0
                } else {
                    gapCount++
                    if (gapCount > 15) break // Too big a gap, line ended
                }
                d += 3 // Step by 3 pixels for speed
            }

            if (lineScore > bestLineScore) {
                bestLineScore = lineScore
                bestAngleIdx = ai
            }
        }

        if (bestLineScore < 4 || bestAngleIdx < 0) return null

        val aimDirX = cosTable[bestAngleIdx]
        val aimDirY = sinTable[bestAngleIdx]

        // Step 4: Find colored balls - smart sampling only inside table
        val balls = findBallsFast(pixels, w, h, tL, tT, tR, tB, cueBallX, cueBallY, ballRadius)

        return AimResult(
            cueBallX = cueBallX,
            cueBallY = cueBallY,
            aimDirX = aimDirX,
            aimDirY = aimDirY,
            tableLeft = tL.toFloat(),
            tableTop = tT.toFloat(),
            tableRight = tR.toFloat(),
            tableBottom = tB.toFloat(),
            ballPositions = balls,
            ballRadius = ballRadius
        )
    }

    private fun findTableBounds(pixels: IntArray, w: Int, h: Int) {
        var left = w
        var top = h
        var right = 0
        var bottom = 0

        // Sample every 8th pixel for speed
        for (y in 0 until h step 8) {
            val rowOff = y * w
            for (x in 0 until w step 8) {
                val px = pixels[rowOff + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)

                if (hsv[0] in TABLE_HUE_MIN..TABLE_HUE_MAX && hsv[1] > TABLE_SAT_MIN && hsv[2] > 0.3f) {
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                }
            }
        }

        cachedTableLeft = left + 8
        cachedTableTop = top + 8
        cachedTableRight = right - 8
        cachedTableBottom = bottom - 8
    }

    private fun findBallsFast(pixels: IntArray, w: Int, h: Int, tl: Int, tt: Int, tr: Int, tb: Int, cueBallX: Float, cueBallY: Float, cueBallR: Float): List<Pair<Float, Float>> {
        val balls = mutableListOf<Pair<Float, Float>>()
        val gridSize = 30 // Divide table into grid cells
        val gridW = (tr - tl) / gridSize + 1
        val gridH = (tb - tt) / gridSize + 1
        val cellCounts = IntArray(gridW * gridH)
        val cellSumX = LongArray(gridW * gridH)
        val cellSumY = LongArray(gridW * gridH)

        // Scan with large step, accumulate into grid cells
        for (y in tt until tb step 5) {
            if (y >= h) break
            val rowOff = y * w
            for (x in tl until tr step 5) {
                if (x >= w) break
                val px = pixels[rowOff + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)

                // Colored balls: high saturation, not table blue, decent brightness
                if (hsv[1] > BALL_SAT_MIN && hsv[2] > 0.4f && hsv[2] < 0.95f) {
                    if (hsv[0] !in TABLE_HUE_MIN..TABLE_HUE_MAX) {
                        val gx = (x - tl) / gridSize
                        val gy = (y - tt) / gridSize
                        if (gx in 0 until gridW && gy in 0 until gridH) {
                            val idx = gy * gridW + gx
                            cellCounts[idx]++
                            cellSumX[idx] += x
                            cellSumY[idx] += y
                        }
                    }
                }
            }
        }

        // Extract ball positions from cells with enough hits
        for (i in cellCounts.indices) {
            if (cellCounts[i] >= 3) {
                val bx = cellSumX[i].toFloat() / cellCounts[i]
                val by = cellSumY[i].toFloat() / cellCounts[i]

                // Skip if too close to cue ball
                val dx = bx - cueBallX
                val dy = by - cueBallY
                if (sqrt(dx * dx + dy * dy) < cueBallR * 2.5f) continue

                balls.add(Pair(bx, by))
            }
        }

        return balls
    }

    /** Call when game scene changes (e.g. new match) to force table re-detection */
    fun resetCache() {
        cachedTableLeft = -1
        tableCacheFrames = TABLE_CACHE_LIFETIME
    }
}

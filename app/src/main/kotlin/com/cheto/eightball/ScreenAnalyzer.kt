package com.cheto.eightball

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pixel-based screen analyzer for 8 Ball Pool.
 * Detects the cue ball position and aim line direction
 * by scanning for white pixels on the game table.
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

    // Thresholds
    private val WHITE_THRESHOLD = 220  // R,G,B all above this = white
    private val TABLE_BLUE_MIN_H = 170f // Hue range for table blue
    private val TABLE_BLUE_MAX_H = 210f
    private val TABLE_BLUE_MIN_S = 0.2f
    private val BALL_COLOR_SAT_MIN = 0.3f // Balls are colorful (high saturation)

    fun analyze(bitmap: Bitmap): AimResult? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Step 1: Find the table area (blue region)
        val tableRect = findTableBounds(pixels, w, h)
        val tLeft = tableRect[0].toFloat()
        val tTop = tableRect[1].toFloat()
        val tRight = tableRect[2].toFloat()
        val tBottom = tableRect[3].toFloat()

        if (tRight - tLeft < w * 0.3f || tBottom - tTop < h * 0.2f) {
            // Table not found or too small
            return null
        }

        // Step 2: Find white pixels on the table (cue ball + aim line)
        val whitePixels = mutableListOf<Pair<Int, Int>>()
        for (y in tTop.toInt()..tBottom.toInt()) {
            for (x in tLeft.toInt()..tRight.toInt()) {
                if (x < 0 || x >= w || y < 0 || y >= h) continue
                val px = pixels[y * w + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (r > WHITE_THRESHOLD && g > WHITE_THRESHOLD && b > WHITE_THRESHOLD) {
                    whitePixels.add(Pair(x, y))
                }
            }
        }

        if (whitePixels.size < 10) return null

        // Step 3: Cluster white pixels to find cue ball vs aim line
        // The cue ball is a dense cluster; the aim line is a thin line of white pixels
        val clusters = clusterPoints(whitePixels, 15)

        // Find the largest cluster = cue ball
        val sortedClusters = clusters.sortedByDescending { it.size }
        if (sortedClusters.isEmpty()) return null

        val cueBallCluster = sortedClusters[0]
        val cueBallX = cueBallCluster.map { it.first }.average().toFloat()
        val cueBallY = cueBallCluster.map { it.second }.average().toFloat()
        val cueBallRadius = estimateRadius(cueBallCluster)

        // Step 4: Find aim line pixels (white pixels NOT in the cue ball cluster)
        // These are thinner scattered white pixels forming a line
        val aimPixels = mutableListOf<Pair<Int, Int>>()
        for (cluster in sortedClusters.drop(0)) {
            // Include small clusters and elongated shapes (aim line segments)
            if (cluster.size < cueBallCluster.size * 0.5f) {
                aimPixels.addAll(cluster)
            }
        }

        // Also include isolated white pixels near the cue ball direction
        val allLinePixels = mutableListOf<Pair<Int, Int>>()
        allLinePixels.addAll(aimPixels)

        // Step 5: Calculate aim direction using RANSAC-like approach
        // Find the best line through cue ball center and the aim pixels
        var bestDirX = 0f
        var bestDirY = -1f // Default: aim up
        var bestScore = 0

        if (allLinePixels.size >= 3) {
            // Try multiple candidate angles based on aim pixel positions
            for (pixel in allLinePixels) {
                val dx = pixel.first - cueBallX
                val dy = pixel.second - cueBallY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < cueBallRadius * 1.5f) continue // Too close to cue ball center
                if (dist > 0) {
                    val ndx = dx / dist
                    val ndy = dy / dist

                    // Score: count how many aim pixels are close to this direction line
                    var score = 0
                    for (p in allLinePixels) {
                        val px = p.first - cueBallX
                        val py = p.second - cueBallY
                        val proj = px * ndx + py * ndy
                        if (proj < 0) continue // Behind cue ball
                        val perpDist = kotlin.math.abs(px * ndy - py * ndx)
                        if (perpDist < 8f) score++
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestDirX = ndx
                        bestDirY = ndy
                    }
                }
            }
        }

        if (bestScore < 3) {
            // Not enough evidence for aim direction
            // Try using the white line by scanning outward from cue ball
            val scanResult = scanForWhiteLine(pixels, w, h, cueBallX.toInt(), cueBallY.toInt(), cueBallRadius.toInt(), tLeft.toInt(), tTop.toInt(), tRight.toInt(), tBottom.toInt())
            if (scanResult != null) {
                bestDirX = scanResult.first
                bestDirY = scanResult.second
            } else {
                return null // Can't determine aim
            }
        }

        // Step 6: Find colored balls on the table
        val ballPositions = findColoredBalls(pixels, w, h, tLeft.toInt(), tTop.toInt(), tRight.toInt(), tBottom.toInt(), cueBallX, cueBallY, cueBallRadius)

        return AimResult(
            cueBallX = cueBallX,
            cueBallY = cueBallY,
            aimDirX = bestDirX,
            aimDirY = bestDirY,
            tableLeft = tLeft,
            tableTop = tTop,
            tableRight = tRight,
            tableBottom = tBottom,
            ballPositions = ballPositions,
            ballRadius = cueBallRadius
        )
    }

    private fun findTableBounds(pixels: IntArray, w: Int, h: Int): IntArray {
        var left = w
        var top = h
        var right = 0
        var bottom = 0
        val hsv = FloatArray(3)

        // Sample every 4th pixel for speed
        for (y in 0 until h step 4) {
            for (x in 0 until w step 4) {
                val px = pixels[y * w + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)

                // Table felt is blue-ish
                if (hsv[0] in TABLE_BLUE_MIN_H..TABLE_BLUE_MAX_H && hsv[1] > TABLE_BLUE_MIN_S && hsv[2] > 0.3f) {
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                }
            }
        }

        // Add small margin
        return intArrayOf(left + 10, top + 10, right - 10, bottom - 10)
    }

    private fun clusterPoints(points: List<Pair<Int, Int>>, maxDist: Int): List<List<Pair<Int, Int>>> {
        val visited = BooleanArray(points.size)
        val clusters = mutableListOf<List<Pair<Int, Int>>>()
        val maxDist2 = maxDist * maxDist

        for (i in points.indices) {
            if (visited[i]) continue
            val cluster = mutableListOf<Pair<Int, Int>>()
            val queue = ArrayDeque<Int>()
            queue.add(i)
            visited[i] = true

            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst()
                cluster.add(points[idx])

                for (j in points.indices) {
                    if (visited[j]) continue
                    val dx = points[idx].first - points[j].first
                    val dy = points[idx].second - points[j].second
                    if (dx * dx + dy * dy <= maxDist2) {
                        visited[j] = true
                        queue.add(j)
                    }
                }
            }
            clusters.add(cluster)
        }
        return clusters
    }

    private fun estimateRadius(cluster: List<Pair<Int, Int>>): Float {
        val cx = cluster.map { it.first }.average()
        val cy = cluster.map { it.second }.average()
        val maxDist = cluster.maxOf { sqrt(((it.first - cx) * (it.first - cx) + (it.second - cy) * (it.second - cy)).toDouble()) }
        return maxDist.toFloat().coerceAtLeast(10f)
    }

    private fun scanForWhiteLine(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int, tl: Int, tt: Int, tr: Int, tb: Int): Pair<Float, Float>? {
        // Scan radially outward from cue ball center in all directions
        var bestAngle = 0f
        var bestCount = 0
        val scanDist = 150 // pixels to scan outward

        for (angleDeg in 0 until 360 step 2) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = kotlin.math.cos(rad).toFloat()
            val dy = kotlin.math.sin(rad).toFloat()
            var count = 0

            for (d in (radius + 5)..scanDist) {
                val sx = (cx + dx * d).toInt()
                val sy = (cy + dy * d).toInt()
                if (sx < tl || sx > tr || sy < tt || sy > tb) break
                if (sx < 0 || sx >= w || sy < 0 || sy >= h) break

                val px = pixels[sy * w + sx]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (r > WHITE_THRESHOLD && g > WHITE_THRESHOLD && b > WHITE_THRESHOLD) {
                    count++
                }
            }

            if (count > bestCount) {
                bestCount = count
                bestAngle = angleDeg.toFloat()
            }
        }

        if (bestCount < 5) return null

        val rad = Math.toRadians(bestAngle.toDouble())
        return Pair(kotlin.math.cos(rad).toFloat(), kotlin.math.sin(rad).toFloat())
    }

    private fun findColoredBalls(pixels: IntArray, w: Int, h: Int, tl: Int, tt: Int, tr: Int, tb: Int, cueBallX: Float, cueBallY: Float, cueBallRadius: Float): List<Pair<Float, Float>> {
        val balls = mutableListOf<Pair<Float, Float>>()
        val hsv = FloatArray(3)
        val candidates = mutableListOf<Pair<Int, Int>>()

        // Scan for highly saturated pixels (colored balls)
        for (y in tt until tb step 3) {
            for (x in tl until tr step 3) {
                if (x < 0 || x >= w || y < 0 || y >= h) continue
                val px = pixels[y * w + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)

                // Colored balls have high saturation and moderate brightness
                if (hsv[1] > BALL_COLOR_SAT_MIN && hsv[2] > 0.4f && hsv[2] < 0.95f) {
                    // Exclude table blue
                    if (hsv[0] !in TABLE_BLUE_MIN_H..TABLE_BLUE_MAX_H) {
                        candidates.add(Pair(x, y))
                    }
                }
            }
        }

        // Cluster colored pixels to find individual balls
        val clusters = clusterPoints(candidates, 20)
        for (cluster in clusters) {
            if (cluster.size < 8) continue // Too small, likely noise
            val bx = cluster.map { it.first }.average().toFloat()
            val by = cluster.map { it.second }.average().toFloat()
            
            // Skip if too close to cue ball
            val dx = bx - cueBallX
            val dy = by - cueBallY
            if (sqrt(dx * dx + dy * dy) < cueBallRadius * 2f) continue

            balls.add(Pair(bx, by))
        }

        return balls
    }
}

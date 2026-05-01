package com.cheto.eightball

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.Collections

class YoloDetector(context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String = "images"

    init {
        val bytes = context.assets.open("best.onnx").readBytes()
        val opts = OrtSession.SessionOptions()
        session = env.createSession(bytes, opts)
        inputName = session?.inputNames?.iterator()?.next() ?: "images"
    }

    data class Detection(val label: Int, val confidence: Float, val x: Float, val y: Float, val w: Float, val h: Float)

    fun detect(bitmap: Bitmap): List<Detection> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val floatBuffer = FloatBuffer.allocate(1 * 3 * 640 * 640)
        
        val pixels = IntArray(640 * 640)
        scaledBitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640)
        
        for (i in 0 until 640 * 640) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255.0f
            val g = ((p shr 8) and 0xFF) / 255.0f
            val b = (p and 0xFF) / 255.0f
            
            floatBuffer.put(i, r)
            floatBuffer.put(640 * 640 + i, g)
            floatBuffer.put(2 * 640 * 640 + i, b)
        }
        
        floatBuffer.rewind()
        
        val inputShape = longArrayOf(1, 3, 640, 640)
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, inputShape)
        val result = session?.run(Collections.singletonMap(inputName, inputTensor))
        
        val output = result?.get(0)?.value as? Array<Array<FloatArray>>
        val detections = mutableListOf<Detection>()
        
        if (output != null && output.isNotEmpty()) {
            val numAnchors = output[0][0].size
            val numClasses = output[0].size - 4
            
            for (i in 0 until numAnchors) {
                var maxConf = 0f
                var bestClass = -1
                
                for (c in 0 until numClasses) {
                    val conf = output[0][4 + c][i]
                    if (conf > maxConf) {
                        maxConf = conf
                        bestClass = c
                    }
                }
                
                if (maxConf > 0.4f) { // confidence threshold
                    val cx = output[0][0][i]
                    val cy = output[0][1][i]
                    val w = output[0][2][i]
                    val h = output[0][3][i]
                    detections.add(Detection(bestClass, maxConf, cx, cy, w, h))
                }
            }
        }
        
        result?.close()
        inputTensor.close()
        
        return nms(detections, 0.45f)
    }

    private fun nms(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()
        val active = BooleanArray(sorted.size) { true }
        
        for (i in sorted.indices) {
            if (!active[i]) continue
            keep.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (active[j]) {
                    if (iou(sorted[i], sorted[j]) > iouThreshold) {
                        active[j] = false
                    }
                }
            }
        }
        return keep
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = maxOf(a.x - a.w / 2, b.x - b.w / 2)
        val y1 = maxOf(a.y - a.h / 2, b.y - b.h / 2)
        val x2 = minOf(a.x + a.w / 2, b.x + b.w / 2)
        val y2 = minOf(a.y + a.h / 2, b.y + b.h / 2)
        
        val w = maxOf(0f, x2 - x1)
        val h = maxOf(0f, y2 - y1)
        
        val intersection = w * h
        if (intersection <= 0) return 0f
        val areaA = a.w * a.h
        val areaB = b.w * b.h
        
        return intersection / (areaA + areaB - intersection)
    }
}

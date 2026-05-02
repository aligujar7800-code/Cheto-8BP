package com.cheto.eightball

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var guidelineView: GuidelineView? = null
    private var floatingBubble: View? = null
    private var menuView: View? = null

    // Screen Capture Variables
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // AI Model
    private var yoloDetector: YoloDetector? = null
    private var lastProcessedTime = 0L
    private val FRAME_INTERVAL_MS = 150L // Process every 150ms for responsive tracking
    private var processingThread: HandlerThread? = null
    private var processingHandler: Handler? = null

    // Auto-Hide Loop
    private var isGameInForeground = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // CRITICAL: startForeground MUST be called IMMEDIATELY in onCreate
        // On Android 14+ use SPECIAL_USE first (no media projection token needed yet)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1, createNotification())
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "startForeground failed, falling back", e)
            try { startForeground(1, createNotification()) } catch (_: Exception) {}
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Background thread for AI processing
        try {
            processingThread = HandlerThread("YoloProcessing").also { it.start() }
            processingHandler = Handler(processingThread!!.looper)

            // Load AI model on background thread to avoid blocking
            processingHandler?.post {
                try {
                    yoloDetector = YoloDetector(this)
                    Log.d("OverlayService", "✅ YOLO Model loaded successfully")
                } catch (e: Exception) {
                    Log.e("OverlayService", "❌ Failed to load YOLO Model", e)
                    yoloDetector = null
                }
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to start processing thread", e)
        }

        try { setupGuidelines() } catch (e: Exception) { Log.e("OverlayService", "setupGuidelines failed", e) }
        try { setupBubble() } catch (e: Exception) { Log.e("OverlayService", "setupBubble failed", e) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent != null) {
                val resultCode = intent.getIntExtra("RESULT_CODE", 0)
                val data: Intent? = intent.getParcelableExtra("DATA")
                if (resultCode != 0 && data != null) {
                    // MUST upgrade to MEDIA_PROJECTION type BEFORE calling getMediaProjection()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                            Log.d("OverlayService", ">>> Upgraded to MEDIA_PROJECTION foreground type")
                        } catch (e: Exception) {
                            Log.e("OverlayService", "Could not upgrade to media projection FGS type", e)
                        }
                    }
                    
                    // NOW setup screen capture (getMediaProjection will work)
                    setupScreenCapture(resultCode, data)
                }
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "onStartCommand error", e)
        }
        
        return START_STICKY
    }

    private var screenWidth = 0
    private var screenHeight = 0
    private val screenAnalyzer = ScreenAnalyzer()

    private fun setupScreenCapture(resultCode: Int, data: Intent) {
        try {
            Log.d("OverlayService", ">>> setupScreenCapture called with resultCode=$resultCode")
            updateDebug("STARTING CAPTURE...")
            
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e("OverlayService", "MediaProjection is null!")
                updateDebug("ERROR: MediaProjection NULL")
                return
            }
            Log.d("OverlayService", ">>> MediaProjection obtained OK")
            
            val width: Int
            val height: Int
            val density: Int
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                width = bounds.width()
                height = bounds.height()
                density = resources.displayMetrics.densityDpi
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                width = metrics.widthPixels
                height = metrics.heightPixels
                density = metrics.densityDpi
            }

            var w = width
            var h = height
            
            // Force landscape dimensions since 8 Ball Pool is always landscape.
            // If we capture in portrait, Android letterboxes the landscape game into a portrait buffer, ruining the coordinates.
            if (w < h) {
                w = height
                h = width
            }

            screenWidth = w
            screenHeight = h
            Log.d("OverlayService", ">>> Screen: ${w}x${h}, density=$density")

            // Capture at 1/2 resolution (1/3 is too low for white line detection)
            val captureWidth = w / 2
            val captureHeight = h / 2
            Log.d("OverlayService", ">>> Capture size: ${captureWidth}x${captureHeight}")

            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
            
            // Android 14+ requires registering a callback BEFORE createVirtualDisplay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d("OverlayService", ">>> MediaProjection stopped by system")
                        updateDebug("CAPTURE STOPPED BY SYSTEM")
                        try {
                            virtualDisplay?.release()
                            imageReader?.close()
                        } catch (_: Exception) {}
                    }
                }, Handler(mainLooper))
                Log.d("OverlayService", ">>> MediaProjection callback registered (Android 14+)")
            }
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                captureWidth, captureHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            
            if (virtualDisplay == null) {
                Log.e("OverlayService", "VirtualDisplay is null!")
                updateDebug("ERROR: VirtualDisplay NULL")
                return
            }
            Log.d("OverlayService", ">>> VirtualDisplay created OK")
            updateDebug("CAPTURE READY - Waiting for frames...")

            var frameNum = 0

            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val now = System.currentTimeMillis()
                        if (now - lastProcessedTime >= FRAME_INTERVAL_MS) {
                            lastProcessedTime = now
                            frameNum++
                            try {
                                val planes = image.planes
                                val buffer = planes[0].buffer
                                val pixelStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPadding = rowStride - pixelStride * image.width
                                
                                val bitmap = Bitmap.createBitmap(
                                    image.width + rowPadding / pixelStride,
                                    image.height,
                                    Bitmap.Config.ARGB_8888
                                )
                                bitmap.copyPixelsFromBuffer(buffer)
                                
                                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                                if (bitmap != croppedBitmap) bitmap.recycle()
                                image.close()

                                val currentFrameNum = frameNum

                                processingHandler?.post {
                                    try {
                                        // PRIMARY: Pixel-based ScreenAnalyzer
                                        val aimResult = screenAnalyzer.analyze(croppedBitmap)
                                        
                                        val scX = screenWidth.toFloat() / croppedBitmap.width.toFloat()
                                        val scY = screenHeight.toFloat() / croppedBitmap.height.toFloat()
                                        
                                        if (aimResult != null) {
                                            Log.d("OverlayService", ">>> Frame #$currentFrameNum: AIM FOUND! CueBall=(${aimResult.cueBallX}, ${aimResult.cueBallY}) Dir=(${aimResult.aimDirX}, ${aimResult.aimDirY})")
                                            
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                guidelineView?.captureScaleX = scX
                                                guidelineView?.captureScaleY = scY
                                                guidelineView?.aimResult = aimResult
                                                guidelineView?.frameCount = currentFrameNum
                                                guidelineView?.debugStatus = "AIM DETECTED ✓"
                                                guidelineView?.invalidate()
                                            }
                                        } else {
                                            if (currentFrameNum % 10 == 0) {
                                                Log.d("OverlayService", ">>> Frame #$currentFrameNum: No aim detected, bitmap=${croppedBitmap.width}x${croppedBitmap.height}")
                                            }
                                            
                                            // FALLBACK: Try YOLO
                                            val detector = yoloDetector
                                            if (detector != null) {
                                                val detections = detector.detect(croppedBitmap)
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    guidelineView?.aimResult = null
                                                    guidelineView?.detections = detections
                                                    guidelineView?.frameCount = currentFrameNum
                                                    guidelineView?.debugStatus = "YOLO: ${detections.size} objs"
                                                    guidelineView?.invalidate()
                                                }
                                            } else {
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    guidelineView?.aimResult = null
                                                    guidelineView?.detections = emptyList()
                                                    guidelineView?.frameCount = currentFrameNum
                                                    guidelineView?.debugStatus = "SCANNING... (no aim line visible)"
                                                    guidelineView?.invalidate()
                                                }
                                            }
                                        }
                                        
                                        croppedBitmap.recycle()
                                    } catch (e: Exception) {
                                        Log.e("OverlayService", "Frame analysis error", e)
                                        updateDebug("ERROR: ${e.message}")
                                        try { croppedBitmap.recycle() } catch (_: Exception) {}
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("OverlayService", "Frame processing error", e)
                                updateDebug("FRAME ERROR: ${e.message}")
                                try { image.close() } catch (_: Exception) {}
                            }
                        } else {
                            image.close()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OverlayService", "Image listener error", e)
                }
            }, processingHandler)
        } catch (e: Exception) {
            Log.e("OverlayService", "setupScreenCapture failed completely", e)
            updateDebug("CAPTURE FAILED: ${e.message}")
        }
    }

    private fun updateDebug(msg: String) {
        Log.d("OverlayService", "DEBUG: $msg")
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                guidelineView?.debugStatus = msg
                guidelineView?.invalidate()
            }
        } catch (_: Exception) {}
    }

    private fun setupGuidelines() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        guidelineView = GuidelineView(this)
        guidelineView?.visibility = View.VISIBLE // Make visible by default
        try {
            windowManager.addView(guidelineView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBubble() {
        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.x = 100
        bubbleParams.y = 100

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingBubble = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setBackgroundColor(0x88000000.toInt())
            setPadding(20, 20, 20, 20)
        }

        floatingBubble?.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0
            private var lastY = 0
            private var initialX = 0
            private var initialY = 0
            private var startTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        lastX = event.rawX.toInt()
                        lastY = event.rawY.toInt()
                        startTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = initialX + (event.rawX.toInt() - lastX)
                        bubbleParams.y = initialY + (event.rawY.toInt() - lastY)
                        try {
                            windowManager.updateViewLayout(floatingBubble, bubbleParams)
                        } catch (e: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (System.currentTimeMillis() - startTime < 200) {
                            toggleMenu()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            floatingBubble?.visibility = View.VISIBLE // Make visible by default
            windowManager.addView(floatingBubble, bubbleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVisibilityChecker() {
        scope.launch(Dispatchers.IO) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (isActive) {
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 2000 // Check last 2 seconds
                
                val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
                var currentApp = ""
                val event = android.app.usage.UsageEvents.Event()
                
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                        currentApp = event.packageName
                    }
                }

                val wasInForeground = isGameInForeground
                isGameInForeground = (currentApp == "com.miniclip.eightballpool")

                if (isGameInForeground != wasInForeground) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        updateOverlayVisibility(isGameInForeground)
                    }
                }
                delay(1000) // Check every 1 second
            }
        }
    }

    private fun updateOverlayVisibility(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        guidelineView?.visibility = visibility
        floatingBubble?.visibility = visibility
        if (!visible && menuView != null) {
            toggleMenu() // close menu if game is backgrounded
        }
    }

    private fun toggleMenu() {
        if (menuView == null) {
            val menuParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            menuParams.gravity = Gravity.CENTER

            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            menuView = inflater.inflate(R.layout.overlay_menu, null)
            
            // Bank Shots toggle
            menuView?.findViewById<android.widget.Switch>(R.id.switchBankShots)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showBankShots = isChecked
                guidelineView?.invalidate()
            }
            
            // Cue Reflection toggle
            menuView?.findViewById<android.widget.Switch>(R.id.switchCueReflection)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showCueReflection = isChecked
                guidelineView?.invalidate()
            }
            
            // Opacity slider
            menuView?.findViewById<android.widget.SeekBar>(R.id.seekBarOpacity)?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    guidelineView?.lineAlpha = progress.coerceIn(30, 255)
                    guidelineView?.invalidate()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
            
            // Close button
            menuView?.findViewById<android.widget.Button>(R.id.btnClose)?.setOnClickListener {
                stopSelf()
            }

            try {
                windowManager.addView(menuView, menuParams)
            } catch (e: Exception) {
                Log.e("OverlayService", "Failed to add menu view", e)
            }
        } else {
            try {
                windowManager.removeView(menuView)
            } catch (_: Exception) {}
            menuView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "OverlayServiceChannel",
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "OverlayServiceChannel")
            .setContentTitle("Cheto Overlay Active")
            .setContentText("The guidelines are running...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { processingThread?.quitSafely() } catch (_: Exception) {}
        
        try { guidelineView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { floatingBubble?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { menuView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
    }
}

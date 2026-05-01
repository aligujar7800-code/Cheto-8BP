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
    private val FRAME_INTERVAL_MS = 300L // Process every 300ms to avoid overload
    private var processingThread: HandlerThread? = null
    private var processingHandler: Handler? = null

    // Auto-Hide Loop
    private var isGameInForeground = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Initialize AI Model
        try {
            yoloDetector = YoloDetector(this)
            Log.d("OverlayService", "✅ YOLO Model loaded successfully")
        } catch (e: Exception) {
            Log.e("OverlayService", "❌ Failed to load YOLO Model", e)
        }

        // Background thread for AI processing
        processingThread = HandlerThread("YoloProcessing").also { it.start() }
        processingHandler = Handler(processingThread!!.looper)

        setupGuidelines()
        setupBubble()
        startVisibilityChecker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val data: Intent? = intent.getParcelableExtra("DATA")
            if (resultCode != 0 && data != null) {
                setupScreenCapture(resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun setupScreenCapture(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val density = metrics.densityDpi
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // We capture in lower resolution for performance (e.g., /2)
        val captureWidth = width / 2
        val captureHeight = height / 2

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            captureWidth, captureHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val now = System.currentTimeMillis()
                if (now - lastProcessedTime >= FRAME_INTERVAL_MS && yoloDetector != null) {
                    lastProcessedTime = now
                    try {
                        // Convert Image to Bitmap
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
                        
                        // Crop to actual width (remove padding)
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                        if (bitmap != croppedBitmap) bitmap.recycle()

                        image.close()

                        // Run inference on background thread
                        processingHandler?.post {
                            try {
                                val detections = yoloDetector!!.detect(croppedBitmap)
                                croppedBitmap.recycle()
                                
                                // Update UI on main thread
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    guidelineView?.detections = detections
                                    guidelineView?.invalidate()
                                }
                            } catch (e: Exception) {
                                Log.e("OverlayService", "AI inference error", e)
                                croppedBitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OverlayService", "Frame processing error", e)
                        image.close()
                    }
                } else {
                    image.close()
                }
            }
        }, processingHandler)
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
        guidelineView?.visibility = View.GONE
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
            floatingBubble?.visibility = View.GONE
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
            
            menuView?.findViewById<CheckBox>(R.id.cbHorizontal)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showHorizontal = isChecked
                guidelineView?.invalidate()
            }
            menuView?.findViewById<CheckBox>(R.id.cbVertical)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showVertical = isChecked
                guidelineView?.invalidate()
            }
            menuView?.findViewById<CheckBox>(R.id.cbCircle)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showCircle = isChecked
                guidelineView?.invalidate()
            }
            menuView?.findViewById<CheckBox>(R.id.cbPowerGuide)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showPowerGuide = isChecked
                guidelineView?.invalidate()
            }
            menuView?.findViewById<CheckBox>(R.id.cbFullScan)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showFullScan = isChecked
                guidelineView?.invalidate()
            }
            menuView?.findViewById<CheckBox>(R.id.cbBankShots)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showBankShots = isChecked
                guidelineView?.invalidate()
            }
            menuView?.findViewById<CheckBox>(R.id.cbSpinCurve)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showSpinCurve = isChecked
                guidelineView?.invalidate()
            }
            menuView?.findViewById<CheckBox>(R.id.cbAutoAim)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.autoAimEnabled = isChecked
                guidelineView?.invalidate()
            }

            // Update Break Chance in Menu
            menuView?.findViewById<android.widget.TextView>(R.id.tvBreakChance)?.text = "${guidelineView?.breakChance}%"

            windowManager.addView(menuView, menuParams)
        } else {
            windowManager.removeView(menuView)
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
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        processingThread?.quitSafely()
        
        guidelineView?.let { windowManager.removeView(it) }
        floatingBubble?.let { windowManager.removeView(it) }
        menuView?.let { windowManager.removeView(it) }
    }
}

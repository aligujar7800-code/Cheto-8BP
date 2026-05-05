package com.cheto.eightball

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * OverlayService: Handles the drawing of guidelines and memory data extraction.
 * Screen recording has been removed in favor of direct Memory Hooking (Cheto style).
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var guidelineView: GuidelineView? = null
    private var floatingBubble: View? = null
    private var menuView: View? = null

    // Memory Hooking & Processing
    private var memoryManager = MemoryManager.getInstance()
    private var isMemoryMode = true // AUTO-ENABLED BY DEFAULT
    private var lastProcessedTime = 0L
    private val FRAME_INTERVAL_MS = 100L // 10 FPS for guidelines (smooth enough)
    
    private var processingThread: HandlerThread? = null
    private var processingHandler: Handler? = null

    // Auto-Hide Loop
    private var isGameInForeground = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // startForeground is mandatory for overlays on Android 10+
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, createNotification())
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "startForeground failed", e)
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Initialize Background Thread for Memory Scanning
        processingThread = HandlerThread("MemoryProcessing").also { it.start() }
        processingHandler = Handler(processingThread!!.looper)

        setupGuidelines()
        setupBubble()
        startVisibilityChecker()
        
        // AUTO-INIT HOOK
        processingHandler?.post {
            if (memoryManager.initHook()) {
                Log.d("OverlayService", "✅ Cheto Memory Hook Initialized")
                startMemoryLoop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private var serviceStartTime = 0L

    /**
     * The Main Loop: Periodically reads memory data and updates the UI.
     */
    private fun startMemoryLoop() {
        serviceStartTime = System.currentTimeMillis()
        
        processingHandler?.post(object : Runnable {
            override fun run() {
                if (processingThread?.isAlive != true) return

                val now = System.currentTimeMillis()
                
                // CRITICAL SAFETY: Wait 5 seconds after service start before reading memory.
                // This prevents crashes during the volatile game launch phase.
                if (now - serviceStartTime < 5000) {
                    processingHandler?.postDelayed(this, 100)
                    return
                }

                if (now - lastProcessedTime >= FRAME_INTERVAL_MS) {
                    lastProcessedTime = now

                    try {
                        val memResult: MemoryManager.AimResult? = memoryManager.readGameData()
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (memResult != null) {
                                guidelineView?.aimResult = memResult
                                guidelineView?.debugStatus = "CHETO: ACTIVE ✓"
                            } else {
                                guidelineView?.debugStatus = "CHETO: SCANNING..."
                            }
                            guidelineView?.invalidate()
                        }
                    } catch (e: Exception) {
                        Log.e("OverlayService", "Error in memory loop", e)
                    }
                }
                
                // Continue loop
                processingHandler?.postDelayed(this, 16)
            }
        })
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
        try {
            windowManager.addView(guidelineView, params)
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to add guideline view", e)
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

        floatingBubble = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setBackgroundColor(0xAA1A1A2E.toInt())
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
            windowManager.addView(floatingBubble, bubbleParams)
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to add bubble", e)
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
                     menuView?.findViewById<android.widget.Switch>(R.id.switchBankShots)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showBankShots = isChecked
                guidelineView?.invalidate()
            }
            
            menuView?.findViewById<android.widget.Switch>(R.id.switchCueReflection)?.setOnCheckedChangeListener { _, isChecked ->
                guidelineView?.showCueReflection = isChecked
                guidelineView?.invalidate()
            }

            menuView?.findViewById<android.widget.Switch>(R.id.switchAlwaysBreak)?.setOnCheckedChangeListener { _, isChecked ->
                memoryManager.forceBreak(isChecked)
            }
            
            menuView?.findViewById<android.widget.SeekBar>(R.id.seekBarOpacity)?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    guidelineView?.lineAlpha = progress.coerceIn(30, 255)
                    guidelineView?.invalidate()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
            
            menuView?.findViewById<android.widget.Button>(R.id.btnClose)?.setOnClickListener {
                toggleMenu()
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

    private fun startVisibilityChecker() {
        scope.launch(Dispatchers.IO) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (isActive) {
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 2000
                
                val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
                var currentApp = ""
                val event = android.app.usage.UsageEvents.Event()
                
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                        currentApp = event.packageName
                    }
                }

                isGameInForeground = (currentApp == "com.miniclip.eightballpool")

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val visibility = if (isGameInForeground) View.VISIBLE else View.GONE
                    guidelineView?.visibility = visibility
                    floatingBubble?.visibility = visibility
                }
                delay(1000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "OverlayServiceChannel",
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "OverlayServiceChannel")
            .setContentTitle("Cheto Engine Active")
            .setContentText("Memory Hooking is running in background...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { processingThread?.quitSafely() } catch (_: Exception) {}
        try { guidelineView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { floatingBubble?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { menuView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
    }
}

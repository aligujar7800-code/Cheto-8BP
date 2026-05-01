package com.cheto.eightball

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var guidelineView: GuidelineView? = null
    private var floatingBubble: View? = null
    private var menuView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        setupGuidelines()
        setupBubble()
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
            windowManager.addView(floatingBubble, bubbleParams)
        } catch (e: Exception) {
            e.printStackTrace()
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
        guidelineView?.let { windowManager.removeView(it) }
        floatingBubble?.let { windowManager.removeView(it) }
        menuView?.let { windowManager.removeView(it) }
    }
}

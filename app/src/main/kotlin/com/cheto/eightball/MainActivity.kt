package com.cheto.eightball

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234
    private val NOTIFICATION_PERMISSION_REQ_CODE = 5678

    private fun startApp() {
        // Step 1: Start overlay service FIRST while app is still in foreground.
        // Starting a foreground service from the background on Android 12+ causes a fatal crash.
        try {
            startOverlayService()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        // Step 2: Launch the game after the service has successfully started
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            launchGame()
        }, 1000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        
        // Load and start pulsing animation on the launch button
        val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)
        btnStart.startAnimation(pulseAnimation)

        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                if (checkNotificationPermission()) {
                    if (checkUsageStatsPermission()) {
                        startApp()
                    } else {
                        requestUsageStatsPermission()
                    }
                } else {
                    requestNotificationPermission()
                }
            } else {
                requestOverlayPermission()
            }
        }

        // Check for updates on startup
        lifecycleScope.launch {
            UpdateManager(this@MainActivity).checkForUpdates()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQ_CODE
            )
        }
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        Toast.makeText(this, "Please allow Usage Access for Auto-Hide feature.", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun launchGame() {
        // Attempt to install the game into our Virtual Space first
        VirtualManager.installGameToVirtualSpace(this)
        
        // Launch the game from within our Virtual Space
        VirtualManager.launchGameFromVirtualSpace(this)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (checkOverlayPermission()) {
                startApp()
            } else {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

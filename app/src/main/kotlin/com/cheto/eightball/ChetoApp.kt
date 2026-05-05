package com.cheto.eightball

import android.app.Application
import android.content.Context
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

/**
 * ChetoApp: The main application class.
 * Initializes the Virtual Container (BlackBox) engine for memory hooking.
 */
class ChetoApp : Application() {

    override fun attachBaseContext(base: Context) {
        // Step 1: Bypass hidden API restrictions as EARLY as possible.
        // On newer Android versions, even super.attachBaseContext() can trigger checks.
        try {
            HiddenApiBypass.unseal()
        } catch (e: Throwable) {
            Log.e("ChetoApp", "Critical: HiddenApiBypass failed", e)
        }

        super.attachBaseContext(base)
        
        try {
            // Step 2: Initialize BlackBox virtual engine for all processes
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String {
                    return base.packageName
                }

                override fun isHideRoot(): Boolean {
                    return true // Stealth mode
                }

                override fun isHideXposed(): Boolean {
                    return true // Stealth mode
                }
            })
            Log.d("ChetoApp", "✅ Virtual Engine Attached to ${base.packageName}")
        } catch (e: Throwable) {
            Log.e("ChetoApp", "❌ Failed to attach Virtual Engine", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Only initialize the core if it hasn't been initialized yet
            BlackBoxCore.get().doCreate()
            Log.d("ChetoApp", "✅ Virtual Engine Created Successfully!")
        } catch (e: Throwable) {
            Log.e("ChetoApp", "❌ Failed to create Virtual Engine", e)
        }
    }
}

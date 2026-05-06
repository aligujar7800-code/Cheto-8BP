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
        // MUST BE FIRST LINE: Bypass hidden API restrictions before ANY engine code is loaded.
        // If this is delayed, BlackBoxCore static initialization will fail causing NoClassDefFoundError.
        try {
            HiddenApiBypass.unseal()
        } catch (_: Throwable) {}

        super.attachBaseContext(base)
        
        try {
            // Standard initialization using the app's own package name
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String {
                    return base.packageName
                }
            })
        } catch (e: Throwable) {
            Log.e("ChetoApp", "Failed to attach engine", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            BlackBoxCore.get().doCreate()
        } catch (e: Throwable) {
            Log.e("ChetoApp", "Failed to create engine", e)
        }
    }
}

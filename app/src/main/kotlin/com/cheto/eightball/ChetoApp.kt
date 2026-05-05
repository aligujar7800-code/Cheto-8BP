package com.cheto.eightball

import android.app.Application
import android.content.Context
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

/**
 * ChetoApp: The main application class.
 * This is where we will initialize the Virtual Container (BlackBox) engine.
 */
class ChetoApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        
        val processName = getCurrentProcessName()
        Log.d("ChetoApp", "Current Process: $processName")

        // Only initialize BlackBox on the main process
        if (base.packageName == processName) {
            try {
                BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                    override fun getHostPackageName(): String {
                        return base.packageName
                    }
                })
                Log.d("ChetoApp", "Virtual Engine Base Context Attached")
            } catch (e: Exception) {
                Log.e("ChetoApp", "Failed to attach Virtual Engine", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        val processName = getCurrentProcessName()
        // Only initialize BlackBox on the main process
        if (packageName == processName) {
            try {
                BlackBoxCore.get().doCreate()
                Log.d("ChetoApp", "Virtual Engine Created Successfully!")
            } catch (e: Exception) {
                Log.e("ChetoApp", "Failed to create Virtual Engine", e)
            }
        }
    }

    private fun getCurrentProcessName(): String? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            // Fallback for older versions
            try {
                val declaredField = Class.forName("android.app.ActivityThread")
                    .getDeclaredField("currentProcessName")
                declaredField.isAccessible = true
                declaredField.get(null) as String
            } catch (e: Exception) {
                null
            }
        }
    }
}

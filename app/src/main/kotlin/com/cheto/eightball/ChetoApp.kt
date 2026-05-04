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
        
        // Only initialize BlackBox on the main process
        if (base.packageName == getProcessName(base)) {
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
        
        // Only initialize BlackBox on the main process
        if (packageName == getProcessName(this)) {
            try {
                BlackBoxCore.get().doCreate()
                Log.d("ChetoApp", "Virtual Engine Created Successfully!")
            } catch (e: Exception) {
                Log.e("ChetoApp", "Failed to create Virtual Engine", e)
            }
        }
    }

    private fun getProcessName(context: Context): String? {
        val pid = android.os.Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (processInfo in am.runningAppProcesses) {
            if (processInfo.pid == pid) {
                return processInfo.processName
            }
        }
        return null
    }
}

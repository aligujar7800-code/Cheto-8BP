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

    companion object {
        // Store the FIRST initialization error so VirtualManager can display it
        @JvmStatic
        var initError: Throwable? = null
            private set
        
        @JvmStatic
        var initErrorDetail: String = ""
            private set
    }

    override fun attachBaseContext(base: Context) {
        // MUST BE FIRST LINE: Bypass hidden API restrictions before ANY engine code is loaded.
        try {
            HiddenApiBypass.unseal()
        } catch (_: Throwable) {}

        super.attachBaseContext(base)
        
        try {
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String {
                    return base.packageName
                }
            })
        } catch (e: Throwable) {
            initError = e
            // Walk the full exception chain to find the ROOT cause
            val sb = StringBuilder()
            sb.appendLine("=== ENGINE INIT FAILED ===")
            var current: Throwable? = e
            var depth = 0
            while (current != null && depth < 10) {
                sb.appendLine("[$depth] ${current.javaClass.name}: ${current.message}")
                current = current.cause
                depth++
            }
            initErrorDetail = sb.toString()
            Log.e("ChetoApp", initErrorDetail)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Only proceed if attachBaseContext succeeded
        if (initError == null) {
            try {
                BlackBoxCore.get().doCreate()
            } catch (e: Throwable) {
                initError = e
                val sb = StringBuilder()
                sb.appendLine("=== ENGINE CREATE FAILED ===")
                var current: Throwable? = e
                var depth = 0
                while (current != null && depth < 10) {
                    sb.appendLine("[$depth] ${current.javaClass.name}: ${current.message}")
                    current = current.cause
                    depth++
                }
                initErrorDetail = sb.toString()
                Log.e("ChetoApp", initErrorDetail)
            }
        }
    }
}

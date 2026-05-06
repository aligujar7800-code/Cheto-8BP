package com.cheto.eightball

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import kotlin.system.exitProcess

/**
 * ChetoApp: The main application class.
 * Initializes the Virtual Container (BlackBox) engine and handles global crashes.
 */
class ChetoApp : Application() {

    companion object {
        @JvmStatic
        var initError: Throwable? = null
            private set
        
        @JvmStatic
        var initErrorDetail: String = ""
            private set
    }

    override fun attachBaseContext(base: Context) {
        // Setup Global Crash Handler to show errors on screen
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Handler(Looper.getMainLooper()).post {
                val errorMsg = "CRASH IN ${thread.name}: ${throwable.javaClass.simpleName}\n${throwable.message}"
                Log.e("ChetoCrash", errorMsg, throwable)
                // We try to show a Toast before dying
                Toast.makeText(base, errorMsg, Toast.LENGTH_LONG).show()
            }
            // Give time for Toast then call old handler
            Thread.sleep(3000)
            oldHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }

        // MUST BE FIRST LINE: Bypass hidden API restrictions
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
            val sb = StringBuilder()
            sb.appendLine("=== ENGINE INIT FAILED ===")
            var current: Throwable? = e
            var depth = 0
            while (current != null && depth < 5) {
                sb.appendLine("[$depth] ${current.javaClass.simpleName}: ${current.message}")
                current = current.cause
                depth++
            }
            initErrorDetail = sb.toString()
            Log.e("ChetoApp", initErrorDetail)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        if (initError == null) {
            try {
                BlackBoxCore.get().doCreate()
            } catch (e: Throwable) {
                initError = e
                Log.e("ChetoApp", "Failed to create engine", e)
            }
        }
    }
}

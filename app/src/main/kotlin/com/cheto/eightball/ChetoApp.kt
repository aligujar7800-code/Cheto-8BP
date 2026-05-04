package com.cheto.eightball

import android.app.Application
import android.content.Context
import android.util.Log

/**
 * ChetoApp: The main application class.
 * This is where we will initialize the Virtual Container (BlackBox) engine.
 */
class ChetoApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        
        try {
            // =================================================================
            // VIRTUAL CONTAINER ENGINE (BlackBox) INITIALIZATION
            // =================================================================
            // When you add the blackbox.aar to your libs folder, uncomment this:
            /*
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String {
                    return base.packageName
                }
            })
            */
            Log.d("ChetoApp", "Virtual Engine Base Context Attached")
        } catch (e: Exception) {
            Log.e("ChetoApp", "Failed to attach Virtual Engine", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // When you add the blackbox.aar, uncomment this:
            /*
            BlackBoxCore.get().doCreate()
            Log.d("ChetoApp", "Virtual Engine Created Successfully!")
            */
        } catch (e: Exception) {
            Log.e("ChetoApp", "Failed to create Virtual Engine", e)
        }
    }
}

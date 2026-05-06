package com.cheto.eightball

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.pm.InstallResult

/**
 * VirtualManager: Handles the installation and launching of the game
 * inside our custom BlackBox virtual container.
 */
object VirtualManager {

    private const val GAME_PACKAGE = "com.miniclip.eightballpool"
    private const val USER_ID = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGameFromVirtualSpace(context: Context) {
        showToast(context, "Step 1: Checking Engine...")
        
        val initErr = ChetoApp.initError
        if (initErr != null) {
            showToast(context, "Engine Error: ${initErr.javaClass.simpleName}")
        }

        Thread {
            try {
                // Check if game is installed
                showToast(context, "Step 2: Checking Installation...")
                val isInstalled = try {
                    BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)
                } catch (t: Throwable) {
                    showToast(context, "Core check failed: ${t.message}")
                    false
                }

                if (!isInstalled) {
                    showToast(context, "Step 3: Installing Game...")
                    val result = BlackBoxCore.get().installPackageAsUser(GAME_PACKAGE, USER_ID)
                    if (!result.success) {
                        showToast(context, "Install Failed: ${result.msg}")
                        return@Thread
                    }
                }
                
                // FORCE STOP existing process
                showToast(context, "Step 4: Killing old process...")
                try {
                    BlackBoxCore.get().stopPackage(GAME_PACKAGE, USER_ID)
                    Thread.sleep(800) // Give it time to die
                } catch (e: Exception) {
                    Log.e("VirtualManager", "Stop failed", e)
                }

                showToast(context, "Step 5: Launching APK...")
                val success = BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
                
                if (success) {
                    showToast(context, "✅ LAUNCH SUCCESS!")
                } else {
                    showToast(context, "❌ LAUNCH FAILED (Internal)")
                }
            } catch (t: Throwable) {
                Log.e("VirtualManager", "Fatal Launch error", t)
                showToast(context, "FATAL: ${t.javaClass.simpleName}")
            }
        }.start()
    }
}

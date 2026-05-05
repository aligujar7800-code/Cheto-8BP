package com.cheto.eightball

import android.content.Context
import android.content.Intent
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

    /**
     * Installs the game into the virtual space if it's not already installed.
     */
    fun installGameToVirtualSpace(context: Context) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Launch Started: Step 1...", Toast.LENGTH_SHORT).show()
            }

            if (!BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Step 2: Installing Game in Container...", Toast.LENGTH_SHORT).show()
                }
                val result: InstallResult = BlackBoxCore.get().installPackageAsUser(GAME_PACKAGE, USER_ID)
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (result.success) {
                        Toast.makeText(context, "Installation SUCCESS!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Installation FAILED: ${result.msg}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Log.d("VirtualManager", "Game already installed.")
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "Install error", e)
        }
    }

    fun launchGameFromVirtualSpace(context: Context) {
        // Step 1: UI Feedback on Main Thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "Step 1: Connecting to Engine...", Toast.LENGTH_SHORT).show()
        }

        // Step 2: Run heavy binder calls on a BACKGROUND thread
        Thread {
            try {
                Log.d("VirtualManager", "Background: Fetching launch intent...")
                val launchIntent = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(GAME_PACKAGE, USER_ID)
                
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Step 2: Intent Retrieved!", Toast.LENGTH_SHORT).show()
                    }
                    
                    // Step 3: Switch to Main Thread ONLY for starting activity and Toast
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            context.startActivity(launchIntent)
                            Toast.makeText(context, "Launch: Signal Sent!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Activity Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }, 500)
                } else {
                    // Fallback on background thread
                    val success = BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Fallback Launch: $success", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("VirtualManager", "Launch fatal error", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Engine Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

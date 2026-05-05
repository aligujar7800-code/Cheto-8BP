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
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Step 1: Bypassing Checks... Launching!", Toast.LENGTH_SHORT).show()
            }

            // Final Launch Command
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val launchIntent = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(GAME_PACKAGE, USER_ID)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        BlackBoxCore.getBActivityManager().startActivity(launchIntent, USER_ID)
                        Toast.makeText(context, "Launch: Signal Sent!", Toast.LENGTH_SHORT).show()
                    } else {
                        // If intent is null, maybe it's not installed. Try fallback launch.
                        val success = BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
                        if (!success) {
                            Toast.makeText(context, "Launch: Not installed? Installing now...", Toast.LENGTH_SHORT).show()
                            installGameToVirtualSpace(context)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Launch Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }, 500)
        } catch (e: Exception) {
            Log.e("VirtualManager", "Launch error", e)
        }
    }
}

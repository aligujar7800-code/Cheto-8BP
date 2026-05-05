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
                Toast.makeText(context, "Step 1: Checking Virtual Container...", Toast.LENGTH_SHORT).show()
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

    /**
     * Launches the game from the virtual space.
     */
    fun launchGameFromVirtualSpace(context: Context) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Step 3: Preparing Launch...", Toast.LENGTH_SHORT).show()
            }

            if (BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)) {
                // Force stop stale processes
                try {
                    BlackBoxCore.get().stopPackage(GAME_PACKAGE, USER_ID)
                } catch (_: Exception) {}

                // Final Launch Command
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Toast.makeText(context, "Step 4: Sending Launch Signal...", Toast.LENGTH_SHORT).show()
                        
                        val launchIntent = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(GAME_PACKAGE, USER_ID)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            BlackBoxCore.getBActivityManager().startActivity(launchIntent, USER_ID)
                            Toast.makeText(context, "Launch Sent! Waiting for Game...", Toast.LENGTH_SHORT).show()
                        } else {
                            // Fallback
                            val success = BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
                            Toast.makeText(context, "Fallback Launch: $success", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Launch Fatal Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }, 1000)
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Error: Game not found in container!", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "Launch error", e)
        }
    }
}

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
            Log.d("VirtualManager", "Checking if game is installed in virtual space...")
            if (!BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)) {
                Log.i("VirtualManager", "Game not found. Installing $GAME_PACKAGE...")
                val result: InstallResult = BlackBoxCore.get().installPackageAsUser(GAME_PACKAGE, USER_ID)
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (result.success) {
                        Log.i("VirtualManager", "Installation SUCCESSful")
                        Toast.makeText(context, "Virtual Space: Game Installed successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("VirtualManager", "Installation FAILED: ${result.msg}")
                        Toast.makeText(context, "Virtual Space: Installation Failed: ${result.msg}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.d("VirtualManager", "Game is already installed in container.")
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "Critical error during installation", e)
        }
    }

    /**
     * Launches the game from the virtual space.
     */
    fun launchGameFromVirtualSpace(context: Context) {
        try {
            Log.d("VirtualManager", "Attempting to launch game...")
            if (BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)) {
                Log.i("VirtualManager", "Game found. Preparing clean launch...")
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Virtual Space: Launching Game...", Toast.LENGTH_SHORT).show()
                }
                
                // Professional Step: Force stop the game first to clear any stale processes
                try {
                    BlackBoxCore.get().stopPackage(GAME_PACKAGE, USER_ID)
                } catch (_: Exception) {}

                // Professional Step: Direct BActivityManager launch with detailed reporting
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Log.i("VirtualManager", "Fetching direct intent for $GAME_PACKAGE...")
                        val launchIntent = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(GAME_PACKAGE, USER_ID)
                        
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                BlackBoxCore.getBActivityManager().startActivity(launchIntent, USER_ID)
                                Log.d("VirtualManager", "✅ Direct BActivityManager.startActivity called")
                                Toast.makeText(context, "Launch: Success! Waiting for window...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("VirtualManager", "startActivity failed", e)
                                Toast.makeText(context, "Launch Error (Activity): ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Log.e("VirtualManager", "❌ Still could not find launch intent")
                            Toast.makeText(context, "Launch Error: Game Intent is NULL", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("VirtualManager", "Direct launch failed", e)
                        Toast.makeText(context, "Launch Error (Fatal): ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }, 1000)
            } else {
                Log.w("VirtualManager", "Game not installed. Triggering install flow...")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Game NOT in container. Installing now...", Toast.LENGTH_SHORT).show()
                }
                installGameToVirtualSpace(context)
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "Critical error during launch", e)
        }
    }
}

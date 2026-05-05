package com.cheto.eightball

import android.content.Context
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
                if (result.success) {
                    Log.i("VirtualManager", "Installation SUCCESSful")
                    Toast.makeText(context, "Virtual Space: Game Installed successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("VirtualManager", "Installation FAILED: ${result.msg}")
                    Toast.makeText(context, "Virtual Space: Installation Failed: ${result.msg}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("VirtualManager", "Game is already installed in container.")
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "Critical error during installation", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, "Virtual Space: Launching Game...", Toast.LENGTH_SHORT).show()
                
                // Professional Step: Force stop the game first to clear any stale processes
                try {
                    BlackBoxCore.get().stopPackage(GAME_PACKAGE, USER_ID)
                    Log.d("VirtualManager", "Stopped existing game process for clean start")
                } catch (e: Exception) {
                    Log.w("VirtualManager", "Stop package failed (normal if not running)")
                }

                // Add a tiny delay to stabilize launch on some devices
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
                        Log.d("VirtualManager", "launchApk command sent to engine")
                    } catch (e: Exception) {
                        Log.e("VirtualManager", "Launch execution failed", e)
                        Toast.makeText(context, "Launch Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, 800)
            } else {
                Log.w("VirtualManager", "Game not installed. Triggering install flow...")
                Toast.makeText(context, "Installing game first...", Toast.LENGTH_SHORT).show()
                installGameToVirtualSpace(context)
                
                Log.i("VirtualManager", "Retrying launch after install...")
                Thread.sleep(1000)
                BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "Critical error during launch", e)
            Toast.makeText(context, "Launch Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

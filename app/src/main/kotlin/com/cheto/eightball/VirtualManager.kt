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
            if (!BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)) {
                val result: InstallResult = BlackBoxCore.get().installPackageAsUser(GAME_PACKAGE, USER_ID)
                if (result.success) {
                    Toast.makeText(context, "Virtual Space: Game Installed successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Virtual Space: Installation Failed: ${result.msg}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("VirtualManager", "Game already installed in container.")
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "BlackBox Error", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Launches the game from the virtual space.
     */
    fun launchGameFromVirtualSpace(context: Context) {
        try {
            if (BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)) {
                Toast.makeText(context, "Virtual Space: Launching Game...", Toast.LENGTH_SHORT).show()
                BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
            } else {
                Toast.makeText(context, "Game not installed in Virtual Space. Installing first...", Toast.LENGTH_SHORT).show()
                installGameToVirtualSpace(context)
                // Try launching again after installation
                BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
            }
        } catch (e: Exception) {
            Log.e("VirtualManager", "Launch Error", e)
            Toast.makeText(context, "Launch Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

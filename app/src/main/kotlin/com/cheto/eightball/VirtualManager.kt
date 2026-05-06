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
        // Check if engine initialization had issues - warn but still try
        val initErr = ChetoApp.initError
        if (initErr != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Engine warning: ${initErr.message}", Toast.LENGTH_SHORT).show()
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "Launching Game...", Toast.LENGTH_SHORT).show()
        }

        Thread {
            try {
                // Check if game is installed in virtual container
                val isInstalled = try {
                    BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)
                } catch (t: Throwable) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Engine Error: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                    false
                }

                if (!isInstalled) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Installing game in container...", Toast.LENGTH_SHORT).show()
                    }
                    val result = BlackBoxCore.get().installPackageAsUser(GAME_PACKAGE, USER_ID)
                    if (!result.success) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "Install Failed: ${result.msg}", Toast.LENGTH_LONG).show()
                        }
                        return@Thread
                    }
                }
                
                // FORCE STOP existing process for clean re-launch
                try {
                    BlackBoxCore.get().stopPackage(GAME_PACKAGE, USER_ID)
                    Thread.sleep(500)
                } catch (_: Throwable) {}

                // Use BlackBox's own launch method - this goes through the virtual
                // activity manager which properly handles process creation & lifecycle
                val success = BlackBoxCore.get().launchApk(GAME_PACKAGE, USER_ID)
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (success) {
                        Toast.makeText(context, "Game Launched!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Launch failed - could not get intent", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (t: Throwable) {
                Log.e("VirtualManager", "Launch error", t)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Fatal: ${t.javaClass.simpleName} - ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

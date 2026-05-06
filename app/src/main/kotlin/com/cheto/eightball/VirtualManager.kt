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
            // Don't block - still try to launch, engine might have recovered
        }

        // UI Feedback
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "Initializing Engine... Please wait", Toast.LENGTH_SHORT).show()
        }

        // Run on background thread
        Thread {
            try {
                // Give the engine 2 seconds to warm up and start its service
                Thread.sleep(2000)

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Step 1: Connecting...", Toast.LENGTH_SHORT).show()
                }

                // Check if engine is actually alive by calling a lightweight method
                val isInstalled = try {
                    BlackBoxCore.get().isInstalled(GAME_PACKAGE, USER_ID)
                } catch (t: Throwable) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Engine Check Error: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                    false
                }

                if (!isInstalled) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Game not in container. Installing...", Toast.LENGTH_SHORT).show()
                    }
                    val result = BlackBoxCore.get().installPackageAsUser(GAME_PACKAGE, USER_ID)
                    if (!result.success) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "Install Failed: ${result.msg}", Toast.LENGTH_LONG).show()
                        }
                        return@Thread
                    }
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Step 2: Intent Retrieved!", Toast.LENGTH_SHORT).show()
                }

                val launchIntent = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(GAME_PACKAGE, USER_ID)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        context.startActivity(launchIntent)
                        Toast.makeText(context, "Launch: Success!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Error: Could not generate intent", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (t: Throwable) {
                Log.e("VirtualManager", "Launch error", t)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Fatal Error: ${t.javaClass.simpleName} - ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

package com.cheto.eightball

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MemoryManager: Handles direct memory reading from the game process.
 * This is the "Hooking" approach mentioned by the user.
 * 
 * Note: Real implementation requires Root or a Virtual Space to read 
 * another process's memory.
 */
class MemoryManager {

    companion object {
        private const val GAME_PACKAGE = "com.miniclip.eightballpool"
        private const val LIB_NAME = "libGame.so"
        
        // AOB Signature for Ball Array (Example pattern used in 8BP)
        // This pattern looks for a sequence of floats that represent ball properties
        private val BALL_SIGNATURE = byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F, 0x00, 0x00, 0x00, 0x00) 

        private var instance: MemoryManager? = null
        fun getInstance() = instance ?: synchronized(this) {
            instance ?: MemoryManager().also { instance = it }
        }

        init {
            try {
                System.loadLibrary("cheto-native")
                Log.d("MemoryManager", "✅ Native Library Loaded")
            } catch (e: Exception) {
                Log.e("MemoryManager", "❌ Failed to load native library", e)
            }
        }
    }

    private var gamePid: Int = -1
    private var isHooked = false

    /**
     * AimResult: Data structure for holding ball positions and predicted paths.
     */
    data class AimResult(
        val balls: List<BallPos>,
        val cueAngle: Float = 0f,
        val isMyTurn: Boolean = true
    )
    
    data class BallPos(val x: Float, val y: Float, val type: Int)

    private var ballListAddress: Long = 0L

    /**
     * Attempts to find the game process and "hook" into it.
     */
    fun initHook(): Boolean {
        gamePid = findProcessId(GAME_PACKAGE)
        if (gamePid == -1) {
            Log.e("MemoryManager", "Game not found!")
            return false
        }
        
        Log.d("MemoryManager", "Game found at PID: $gamePid")
        
        val libRange = findModuleRange(gamePid, LIB_NAME)
        if (libRange != null) {
            MemoryOffsets.LIB_GAME_BASE = libRange.first
            
            // Perform signature scan to find Ball Array automatically
            val foundAddress = scanForSignature(gamePid, libRange.first, libRange.second, BALL_SIGNATURE)
            if (foundAddress != 0L) {
                Log.d("MemoryManager", "✅ Auto-Scanner: Found Ball Array at ${foundAddress.toString(16)}")
                ballListAddress = foundAddress
            }
        }
        
        isHooked = true
        return true
    }

    /**
     * Reads ball data directly from RAM and converts it for the UI.
     */
    fun readGameData(): AimResult? {
        try {
            if (!isHooked) {
                // Try to hook if not already hooked
                val pid = findProcessId(GAME_PACKAGE)
                if (pid != -1) {
                    Log.i("MemoryManager", "Found Game PID: $pid. Attempting to hook...")
                    initHook()
                }
                return null
            }

            if (gamePid == -1 || ballListAddress == 0L) return null
            
            // Anti-Ban Security Check
            if (!nativeCheckSecurity()) {
                Log.w("MemoryManager", "Security check failed!")
                return null
            }

            // Read Stick Angle (Float)
            val cueAngle = 0f 
            
            // Call native function to read all balls
            val rawData = try {
                nativeReadBalls(gamePid, ballListAddress)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("MemoryManager", "Native library not loaded!", e)
                return null
            }
            
            val balls = mutableListOf<BallPos>()
            if (rawData.isNotEmpty()) {
                for (i in 0 until rawData.size / 2) {
                    val x = rawData[i * 2]
                    val y = rawData[i * 2 + 1]
                    if (x != 0f || y != 0f) {
                        balls.add(BallPos(x, y, i))
                    }
                }
            }
            
            return if (balls.isNotEmpty()) AimResult(balls, cueAngle) else null
        } catch (e: Exception) {
            Log.e("MemoryManager", "Critical Error in readGameData: ${e.message}")
            return null
        }
    }

    private fun findModuleRange(pid: Int, moduleName: String): Pair<Long, Long>? {
        try {
            val mapsFile = File("/proc/$pid/maps")
            if (!mapsFile.exists()) return null
            
            val lines = mapsFile.readLines()
            for (line in lines) {
                if (line.contains(moduleName) && line.contains("r-xp")) {
                    val parts = line.split(" ")[0].split("-")
                    val start = parts[0].toLong(16)
                    val end = parts[1].toLong(16)
                    return Pair(start, end)
                }
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error reading maps", e)
        }
        return null
    }

    private fun scanForSignature(pid: Int, start: Long, end: Long, signature: ByteArray): Long {
        Log.d("MemoryManager", "Native Scanning ${end - start} bytes for signature...")
        return nativeScanSignature(pid, start, end, signature)
    }

    private external fun nativeScanSignature(pid: Int, start: Long, end: Long, signature: ByteArray): Long
    private external fun nativeReadBalls(pid: Int, ballListAddress: Long): FloatArray
    private external fun nativeCheckSecurity(): Boolean
    private external fun nativeWriteInt(pid: Int, address: Long, value: Int): Boolean

    fun forceBreak(enabled: Boolean) {
        if (!isHooked || gamePid == -1) return
        val turnStateAddress = MemoryOffsets.LIB_GAME_BASE + 0xABC123 
        if (enabled) {
            nativeWriteInt(gamePid, turnStateAddress, 1)
        }
    }

    private var cachedPid: Int = -1
    private var lastPidCheck: Long = 0

    private fun findProcessId(packageName: String): Int {
        val now = System.currentTimeMillis()
        // Only re-scan /proc every 3 seconds to avoid system-level kills on Android 14+
        if (cachedPid != -1 && now - lastPidCheck < 3000) {
            return cachedPid
        }
        
        lastPidCheck = now
        val procDir = File("/proc")
        val files = procDir.listFiles() ?: return -1
        for (file in files) {
            if (file.isDirectory && file.name.all { it.isDigit() }) {
                try {
                    val cmdline = File(file, "cmdline").readText().trimEnd('\u0000')
                    if (cmdline == packageName) {
                        cachedPid = file.name.toInt()
                        return cachedPid
                    }
                } catch (e: Exception) {}
            }
        }
        cachedPid = -1
        return -1
    }
    
    fun isReady(): Boolean = isHooked
}

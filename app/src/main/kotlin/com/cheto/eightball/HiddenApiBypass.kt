package com.cheto.eightball

import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * HiddenApiBypass: Replaces me.weishu:free_reflection library.
 * Allows BlackBox to access Android's hidden/restricted APIs
 * by calling VMRuntime.setHiddenApiExemptions.
 * 
 * This is essential for virtual engines on Android 9 (P) and above.
 */
object HiddenApiBypass {

    private const val TAG = "HiddenApiBypass"

    fun unseal(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // No restrictions before Android P
            return true
        }

        return try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime: Method = vmRuntimeClass.getDeclaredMethod("getRuntime")
            getRuntime.isAccessible = true
            val vmRuntime = getRuntime.invoke(null)

            val setHiddenApiExemptions: Method = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions", Array<String>::class.java
            )
            setHiddenApiExemptions.isAccessible = true
            // "L" exempts ALL hidden APIs (every class starts with L in dex)
            setHiddenApiExemptions.invoke(vmRuntime, arrayOf("L") as Any)
            
            Log.d(TAG, "✅ Hidden API restrictions bypassed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to bypass hidden API restrictions", e)
            // Fallback: try the meta-reflection approach
            tryMetaReflection()
        }
    }

    /**
     * Fallback: Use "meta-reflection" trick.
     * Get a Method object for getDeclaredMethod via reflection,
     * which gives us a "trusted" invoker that bypasses checks.
     */
    private fun tryMetaReflection(): Boolean {
        return try {
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java
            )
            
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = getDeclaredMethod.invoke(
                vmRuntimeClass, "getRuntime", null
            ) as Method
            val vmRuntime = getRuntime.invoke(null)

            val setHiddenApiExemptions = getDeclaredMethod.invoke(
                vmRuntimeClass, "setHiddenApiExemptions",
                arrayOf(Array<String>::class.java)
            ) as Method
            setHiddenApiExemptions.invoke(vmRuntime, arrayOf("L") as Any)
            
            Log.d(TAG, "✅ Hidden API bypassed via meta-reflection")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Meta-reflection also failed", e)
            false
        }
    }
}

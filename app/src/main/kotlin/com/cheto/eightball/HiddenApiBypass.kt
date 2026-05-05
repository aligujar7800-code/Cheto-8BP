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
            return true
        }

        try {
            Log.d(TAG, "Attempting Hidden API bypass...")
            
            // Method 1: The standard VMRuntime approach
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime")
            getRuntime.isAccessible = true
            val vmRuntime = getRuntime.invoke(null)

            val setHiddenApiExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions", Array<String>::class.java
            )
            setHiddenApiExemptions.isAccessible = true
            setHiddenApiExemptions.invoke(vmRuntime, arrayOf("L"))
            
            Log.i(TAG, "✅ Hidden API bypassed (Standard)")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Standard bypass failed, trying Meta-Reflection...")
            return tryMetaReflection()
        }
    }

    private fun tryMetaReflection(): Boolean {
        try {
            // Method 2: Reflection on reflection (Trusted Class approach)
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod("getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java)

            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as Method
            val vmRuntime = getRuntime.invoke(null)

            val setHiddenApiExemptions = getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", arrayOf(Array<String>::class.java)) as Method
            setHiddenApiExemptions.invoke(vmRuntime, arrayOf("L"))
            
            Log.i(TAG, "✅ Hidden API bypassed (Meta-Reflection)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ ALL Hidden API bypass methods failed!", e)
            return false
        }
    }
}

package com.cheto.eightball

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val UPDATE_URL = "https://raw.githubusercontent.com/aligujar7800-code/Cheto-8BP/main/update.json"

    suspend fun checkForUpdates() {
        try {
            val request = Request.Builder().url(UPDATE_URL).build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            
            if (response.isSuccessful) {
                val jsonData = response.body?.string()
                if (jsonData != null) {
                    val jsonObject = JSONObject(jsonData)
                    val latestVersionCode = jsonObject.getInt("versionCode")
                    val latestVersionName = jsonObject.getString("versionName")
                    val apkUrl = jsonObject.getString("apkUrl")
                    val releaseNotes = jsonObject.optString("releaseNotes", "New version available!")

                    val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                    } else {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                    }

                    if (latestVersionCode > currentVersionCode) {
                        withContext(Dispatchers.Main) {
                            showUpdateDialog(latestVersionName, releaseNotes, apkUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showUpdateDialog(versionName: String, notes: String, apkUrl: String) {
        AlertDialog.Builder(context)
            .setTitle("New Update Available: v$versionName")
            .setMessage(notes)
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallApk(apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_LONG).show()
        
        // Simple download logic using Coroutines (could be improved with DownloadManager)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(apkUrl).build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val apkFile = File(context.cacheDir, "update.apk")
                    val fos = FileOutputStream(apkFile)
                    fos.write(response.body?.bytes())
                    fos.close()
                    
                    withContext(Dispatchers.Main) {
                        installApk(apkFile)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "Please allow installation from this source and try again.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

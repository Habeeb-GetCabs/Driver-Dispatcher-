package com.example.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.remote.FirebaseSyncManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class UpdateInfo(
    val updateAvailable: Boolean = false,
    val forceUpdate: Boolean = false,
    val downloadUrl: String = ""
)

object AppUpdater {
    private const val TAG = "AppUpdater"
    private val _updateInfo = MutableStateFlow(UpdateInfo())
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo

    private var downloadId: Long = -1L

    fun checkForUpdates() {
        val dbRef = FirebaseDatabase.getInstance(com.example.data.remote.FirebaseSyncManager.FIREBASE_URL).getReference("app_metadata")
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val latestVersionCode = snapshot.child("latest_version_code").getValue(Int::class.java) ?: 0
                    val forceUpdate = snapshot.child("force_update").getValue(Boolean::class.java) ?: false
                    val apkUrl = snapshot.child("apk_url").getValue(String::class.java) ?: ""

                    if (latestVersionCode > BuildConfig.VERSION_CODE && apkUrl.isNotBlank()) {
                        _updateInfo.value = UpdateInfo(
                            updateAvailable = true,
                            forceUpdate = forceUpdate,
                            downloadUrl = apkUrl
                        )
                    } else {
                        _updateInfo.value = UpdateInfo()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking for updates: ${e.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Update check cancelled: ${error.message}")
            }
        })
    }

    fun downloadAndInstallUpdate(context: Context, url: String) {
        try {
            val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            if (destination.exists()) {
                destination.delete()
            }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading App Update")
                .setDescription("Downloading latest version of Get Cabs Driver")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destination))

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId && context != null) {
                        installApk(context, destination)
                        context.unregisterReceiver(this)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update: ${e.message}")
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK: ${e.message}")
        }
    }
}

package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.audio.IlaiyaraajaRingtonePlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class DispatchAlertAction {
    data class Accept(val orderId: String) : DispatchAlertAction()
    data class Decline(val orderId: String) : DispatchAlertAction()
    data class Timeout(val orderId: String) : DispatchAlertAction()
}

object DispatchAlertController {
    private val _actionFlow = MutableSharedFlow<DispatchAlertAction>(extraBufferCapacity = 10)
    val actionFlow = _actionFlow.asSharedFlow()

    fun emitAction(action: DispatchAlertAction) {
        _actionFlow.tryEmit(action)
    }

    fun startAlert(
        context: Context,
        orderId: String,
        passengerName: String,
        pickupAddress: String,
        destinationAddress: String,
        estimatedFare: Double,
        currencySymbol: String = "$",
        ringtoneId: String = "ACCORDION_GROOVE"
    ) {
        val intent = Intent(context, DispatchAlertService::class.java).apply {
            action = DispatchAlertService.ACTION_START_ALERT
            putExtra("orderId", orderId)
            putExtra("passengerName", passengerName)
            putExtra("pickupAddress", pickupAddress)
            putExtra("destinationAddress", destinationAddress)
            putExtra("estimatedFare", estimatedFare)
            putExtra("currencySymbol", currencySymbol)
            putExtra("ringtoneId", ringtoneId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopAlert(context: Context) {
        val intent = Intent(context, DispatchAlertService::class.java).apply {
            action = DispatchAlertService.ACTION_STOP_ALERT
        }
        context.startService(intent)
    }
}

class DispatchAlertService : Service() {

    companion object {
        const val TAG = "DispatchAlertService"
        const val CHANNEL_ID = "dispatch_high_priority_alert_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START_ALERT = "com.example.service.ACTION_START_ALERT"
        const val ACTION_STOP_ALERT = "com.example.service.ACTION_STOP_ALERT"
        const val ACTION_ACCEPT_TRIP = "com.example.service.ACTION_ACCEPT_TRIP"
        const val ACTION_DECLINE_TRIP = "com.example.service.ACTION_DECLINE_TRIP"
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var timeoutJob: Job? = null

    private var vibrator: Vibrator? = null
    private var currentOrderId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initVibrator()
    }

    private fun initVibrator() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init vibrator: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALERT -> {
                val orderId = intent.getStringExtra("orderId") ?: return START_NOT_STICKY
                val passengerName = intent.getStringExtra("passengerName") ?: "Passenger"
                val pickupAddress = intent.getStringExtra("pickupAddress") ?: "Pickup Location"
                val destinationAddress = intent.getStringExtra("destinationAddress") ?: "Destination Location"
                val estimatedFare = intent.getDoubleExtra("estimatedFare", 25.0)
                val currencySymbol = intent.getStringExtra("currencySymbol") ?: "$"
                val ringtoneId = intent.getStringExtra("ringtoneId") ?: "ACCORDION_GROOVE"

                currentOrderId = orderId
                startForegroundAlert(orderId, passengerName, pickupAddress, destinationAddress, estimatedFare, currencySymbol, ringtoneId)
            }
            ACTION_ACCEPT_TRIP -> {
                val orderId = intent.getStringExtra("orderId") ?: currentOrderId ?: ""
                Log.d(TAG, "Accepted trip from alert notification: $orderId")
                stopAudioAndVibration()
                if (orderId.isNotBlank()) {
                    DispatchAlertController.emitAction(DispatchAlertAction.Accept(orderId))
                }
                stopForegroundAndSelf()
            }
            ACTION_DECLINE_TRIP -> {
                val orderId = intent.getStringExtra("orderId") ?: currentOrderId ?: ""
                Log.d(TAG, "Declined trip from alert notification: $orderId")
                stopAudioAndVibration()
                if (orderId.isNotBlank()) {
                    DispatchAlertController.emitAction(DispatchAlertAction.Decline(orderId))
                }
                stopForegroundAndSelf()
            }
            ACTION_STOP_ALERT -> {
                stopAudioAndVibration()
                stopForegroundAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundAlert(
        orderId: String,
        passengerName: String,
        pickupAddress: String,
        destinationAddress: String,
        estimatedFare: Double,
        currencySymbol: String,
        ringtoneId: String
    ) {
        // Start continuous looping audio ringtone
        IlaiyaraajaRingtonePlayer.playLoop(this, ringtoneId)

        // Start continuous looping vibration pattern
        startContinuousVibration()

        // Build Full-screen Intent Notification
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("orderId", orderId)
            putExtra("openAlertModal", true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action PendingIntent
        val acceptIntent = Intent(this, DispatchAlertService::class.java).apply {
            action = ACTION_ACCEPT_TRIP
            putExtra("orderId", orderId)
        }
        val acceptPendingIntent = PendingIntent.getService(
            this,
            102,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action PendingIntent
        val declineIntent = Intent(this, DispatchAlertService::class.java).apply {
            action = ACTION_DECLINE_TRIP
            putExtra("orderId", orderId)
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            103,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedFare = String.format("%.2f", estimatedFare)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("🚕 INCOMING TRIP DISPATCH: $orderId")
            .setContentText("Pickup: $pickupAddress • Fare: $currencySymbol$formattedFare")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Passenger: $passengerName\n" +
                "📍 Pickup: $pickupAddress\n" +
                "🏁 Drop: $destinationAddress\n" +
                "💵 Fare: $currencySymbol$formattedFare"
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setLocalOnly(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_add, "ACCEPT TRIP", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DECLINE", declinePendingIntent)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())

        // Launch 30-Second Dispatch Timeout Coroutine
        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(30_000)
            Log.d(TAG, "30-second dispatch alert timeout reached for order $orderId")
            stopAudioAndVibration()
            DispatchAlertController.emitAction(DispatchAlertAction.Timeout(orderId))
            stopForegroundAndSelf()
        }
    }

    private fun startContinuousVibration() {
        try {
            val pattern = longArrayOf(0, 1000, 500, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration error: ${e.message}")
        }
    }

    private fun stopAudioAndVibration() {
        try {
            IlaiyaraajaRingtonePlayer.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio or vibration: ${e.message}")
        }
    }

    private fun stopForegroundAndSelf() {
        timeoutJob?.cancel()
        timeoutJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "High-Priority Dispatch Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full screen overlay notifications and audio ringtone for incoming taxi dispatch orders."
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioAndVibration()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

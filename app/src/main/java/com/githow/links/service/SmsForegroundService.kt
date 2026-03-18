package com.githow.links.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.githow.links.MainActivity
import com.githow.links.R

/**
 * SmsForegroundService
 *
 * Runs as a foreground service with a persistent notification.
 * This is the ONLY reliable way to keep the app process alive on Android
 * so the SmsReceiver can capture M-PESA messages even when:
 *   - The app is not open
 *   - The phone has been idle for hours
 *   - Battery optimisation is aggressive (Tecno, Infinix, Samsung, etc.)
 *
 * The foreground service tells Android: "This process is doing something
 * important — do not kill it." The system is legally required to respect this.
 *
 * Usage:
 *   Start:  SmsForegroundService.start(context)
 *   Stop:   SmsForegroundService.stop(context)
 */
class SmsForegroundService : Service() {

    companion object {
        private const val TAG = "LINKS_SERVICE"
        const val CHANNEL_ID = "links_foreground_service"
        const val NOTIFICATION_ID = 9001

        // Actions
        private const val ACTION_START = "ACTION_START"
        private const val ACTION_STOP = "ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, SmsForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "✅ SmsForegroundService start requested")
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            Log.d(TAG, "SmsForegroundService stop requested")
        }

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            @Suppress("DEPRECATION")
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == SmsForegroundService::class.java.name }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SmsForegroundService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "SmsForegroundService stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Start or restart — promote to foreground immediately
                startForeground(NOTIFICATION_ID, buildNotification())
                Log.d(TAG, "✅ SmsForegroundService running in foreground")
            }
        }

        // START_STICKY: if the OS kills us, restart automatically
        // This is critical — it means even if Android kills the process,
        // it will be relaunched and the foreground service will resume
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away from recents — restart the service
        // This handles the case where the user explicitly closes the app
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed — scheduling restart")
        val restartIntent = Intent(applicationContext, SmsForegroundService::class.java).apply {
            action = ACTION_START
        }
        val pendingIntent = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LINKS SMS Monitor",
                NotificationManager.IMPORTANCE_LOW   // LOW = no sound, minimal UI
            ).apply {
                description = "Keeps LINKS running to capture M-PESA messages"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Tap the notification to open the app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LINKS is active")
            .setContentText("Monitoring for M-PESA messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)           // Cannot be dismissed by swipe
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
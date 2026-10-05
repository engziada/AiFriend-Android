package com.ziaada.aifriend.byd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BydConnectionService : Service() {
    private val TAG = "BydConnectionService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionManager: BydConnectionManager? = null

    companion object {
        const val CHANNEL_ID = "aifriend_byd_connection"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connectionManager = BydConnectionManager(this)
        connectionManager?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        connectionManager?.stop()
        scope.coroutineContext[Job]?.cancel()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Friend BYD Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection to vehicle systems"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Friend")
            .setContentText("Connected to vehicle")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
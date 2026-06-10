package com.rgbws2812.controller.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rgbws2812.controller.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MediaProjectionCaptureService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        isForeground.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationId,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NotificationId, notification())
        }
        isForeground.value = true
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "系统音频采集",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_audio_capture)
            .setContentTitle("RGB 彩灯音乐律动")
            .setContentText("正在采集系统音频电平")
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val ChannelId = "media_projection_capture"
        private const val NotificationId = 1001
        private val isForeground = MutableStateFlow(false)

        fun start(context: Context) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        suspend fun startAndAwaitForeground(context: Context): Boolean {
            start(context)
            return withTimeoutOrNull(2_000L) {
                isForeground.filter { it }.first()
                true
            } == true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionCaptureService::class.java))
        }
    }
}

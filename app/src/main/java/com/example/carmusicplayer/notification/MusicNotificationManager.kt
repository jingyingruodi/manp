package com.example.carmusicplayer.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.carmusicplayer.MainActivity
import com.example.carmusicplayer.Music
import com.example.carmusicplayer.MusicService
import com.example.carmusicplayer.R

/**
 * 重构后的通知管理器
 */
class MusicNotificationManager(private val service: MusicService) {

    companion object {
        const val CHANNEL_ID = "car_music_channel_v3"
        const val NOTIFICATION_ID = 1001 
        
        const val ACTION_PREV = "action_prev"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_STOP = "action_stop"
    }

    private val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 清理旧渠道
            try { 
                notificationManager.deleteNotificationChannel("music_channel_control") 
                notificationManager.deleteNotificationChannel("car_music_player_channel")
            } catch (_: Exception) {}

            val name = "车载音乐播放"
            val descriptionText = "显示播放控制"
            val importance = NotificationManager.IMPORTANCE_LOW 
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(music: Music?, isPlaying: Boolean): Notification {
        val contentIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            service, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(service, MusicService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            service, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(service, MusicService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(service, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(service, MusicService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(service, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(service, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(service, 3, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val iconPrev = android.R.drawable.ic_media_previous
        val iconNext = android.R.drawable.ic_media_next
        val iconPlayPause = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val strPlayPause = if (isPlaying) "暂停" else "播放"

        val mediaStyle = MediaStyle()
            .setMediaSession(service.getMediaSessionToken())
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPendingIntent)

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play) 
            .setContentTitle(music?.title ?: "车载音乐")
            .setContentText(music?.artist ?: "准备播放...")
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)

        try {
            val largeIcon = BitmapFactory.decodeResource(service.resources, R.drawable.vinyl_record)
            builder.setLargeIcon(largeIcon)
        } catch (e: Exception) {
            // 忽略
        }

        builder.addAction(iconPrev, "上一曲", prevPendingIntent)
        builder.addAction(iconPlayPause, strPlayPause, playPausePendingIntent)
        builder.addAction(iconNext, "下一曲", nextPendingIntent)

        return builder.build()
    }

    fun updateNotification(music: Music?, isPlaying: Boolean) {
        val notification = buildNotification(music, isPlaying)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun cancelAll() {
        notificationManager.cancelAll()
    }
}

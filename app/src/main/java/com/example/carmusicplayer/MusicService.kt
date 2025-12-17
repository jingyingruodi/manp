package com.example.carmusicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    private lateinit var mediaPlayer: MediaPlayer
    private val binder = MusicBinder()
    private var currentMusic: Music? = null
    private var isPlaying = false

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        mediaPlayer.setOnCompletionListener {
            onMusicCompletion()
        }
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun playMusic(music: Music) {
        try {
            mediaPlayer.reset()
            mediaPlayer = MediaPlayer.create(this, music.resourceId)
            currentMusic = music
            startMusic()
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startMusic() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
            updateNotification()
        }
    }

    fun pauseMusic() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            updateNotification()
        }
    }

    fun resumeMusic() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
            updateNotification()
        }
    }

    fun stopMusic() {
        mediaPlayer.stop()
        isPlaying = false
        updateNotification()
    }

    fun seekTo(position: Int) {
        mediaPlayer.seekTo(position)
    }

    fun getCurrentPosition(): Int = mediaPlayer.currentPosition

    fun getDuration(): Int = mediaPlayer.duration

    fun isMusicPlaying(): Boolean = mediaPlayer.isPlaying

    fun getCurrentMusic(): Music? = currentMusic

    fun setOnCompletionListener(listener: MediaPlayer.OnCompletionListener) {
        mediaPlayer.setOnCompletionListener(listener)
    }

    private fun onMusicCompletion() {
        isPlaying = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "music_channel",
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Pause",
                getPendingIntent("PAUSE")
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Play",
                getPendingIntent("PLAY")
            )
        }

        val nextAction = NotificationCompat.Action(
            R.drawable.ic_skip_next,
            "Next",
            getPendingIntent("NEXT")
        )

        val prevAction = NotificationCompat.Action(
            R.drawable.ic_skip_previous,
            "Previous",
            getPendingIntent("PREVIOUS")
        )

        val notification = NotificationCompat.Builder(this, "music_channel")
            .setContentTitle(currentMusic?.title ?: "Car Music Player")
            .setContentText(currentMusic?.artist ?: "No music playing")
            .setSmallIcon(R.drawable.ic_play)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.vinyl_record))
            .setContentIntent(pendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(1, notification)
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            putExtra("ACTION", action)
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("ACTION")?.let { action ->
            when (action) {
                "PLAY" -> resumeMusic()
                "PAUSE" -> pauseMusic()
                "NEXT" -> {
                    // 这里需要与Activity通信来获取下一首歌曲
                    // 暂时不做实现，由Activity处理
                }
                "PREVIOUS" -> {
                    // 这里需要与Activity通信来获取上一首歌曲
                    // 暂时不做实现，由Activity处理
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}
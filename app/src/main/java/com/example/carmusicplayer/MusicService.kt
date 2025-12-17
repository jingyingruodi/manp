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
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException

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
        Log.d("MusicService", "Service创建")
        createNotificationChannel()
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer.setOnCompletionListener {
            Log.d("MusicService", "音乐播放完成")
            onMusicCompletion()
        }
        Log.d("MusicService", "MediaPlayer初始化完成")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("MusicService", "Service被绑定")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "Service启动命令")

        // 启动为前台服务
        startForegroundService()

        return START_STICKY
    }

    fun playMusic(music: Music) {
        try {
            Log.d("MusicService", "===== 开始播放音乐 =====")
            Log.d("MusicService", "音乐标题: ${music.title}")
            Log.d("MusicService", "是否为导入: ${music.isImported}")
            Log.d("MusicService", "资源ID: ${music.resourceId}")
            Log.d("MusicService", "文件路径: ${music.filePath}")

            // 停止并重置当前的MediaPlayer
            resetMediaPlayer()

            if (music.isImported) {
                // 播放导入的音乐文件
                Log.d("MusicService", "使用文件路径播放导入音乐")
                playImportedMusic(music)
            } else {
                // 播放内置的音乐
                Log.d("MusicService", "使用资源ID播放内置音乐")
                playBuiltInMusic(music)
            }

            currentMusic = music
            startMusic()
            updateNotification()

            Log.d("MusicService", "===== 播放成功开始 =====")

        } catch (e: Exception) {
            Log.e("MusicService", "播放音乐失败!", e)

            // 显示更详细的错误信息
            when (e) {
                is IOException -> {
                    Log.e("MusicService", "IO错误: ${e.message}")
                    throw IOException("播放失败: ${e.message}")
                }
                is IllegalStateException -> {
                    Log.e("MusicService", "状态错误: ${e.message}")
                    throw IllegalStateException("播放失败: ${e.message}")
                }
                else -> {
                    Log.e("MusicService", "未知错误: ${e.message}")
                    throw Exception("播放失败: ${e.message}")
                }
            }
        }
    }

    private fun resetMediaPlayer() {
        try {
            if (::mediaPlayer.isInitialized) {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                    Log.d("MusicService", "停止当前播放")
                }
                mediaPlayer.reset()
                Log.d("MusicService", "MediaPlayer已重置")
            } else {
                initMediaPlayer()
            }
        } catch (e: Exception) {
            Log.e("MusicService", "重置MediaPlayer失败", e)
            // 创建新的MediaPlayer
            initMediaPlayer()
        }
    }

    private fun playImportedMusic(music: Music) {
        // 检查文件是否存在
        val file = File(music.filePath)
        Log.d("MusicService", "文件是否存在: ${file.exists()}")
        Log.d("MusicService", "文件绝对路径: ${file.absolutePath}")
        Log.d("MusicService", "文件大小: ${file.length()} 字节")

        if (!file.exists()) {
            Log.e("MusicService", "错误: 文件不存在!")
            throw IOException("文件不存在: ${music.filePath}")
        }

        if (!file.canRead()) {
            Log.e("MusicService", "错误: 文件不可读!")
            throw IOException("文件不可读: ${music.filePath}")
        }

        // 设置数据源
        mediaPlayer.setDataSource(music.filePath)
        Log.d("MusicService", "数据源设置完成")

        // 准备播放
        mediaPlayer.prepare()
        Log.d("MusicService", "MediaPlayer准备完成")
    }

    private fun playBuiltInMusic(music: Music) {
        if (music.resourceId <= 0) {
            Log.e("MusicService", "错误: 无效的资源ID!")
            throw IllegalArgumentException("无效的资源ID: ${music.resourceId}")
        }

        // 创建新的MediaPlayer来播放资源
        val player = MediaPlayer.create(this, music.resourceId)
        if (player == null) {
            Log.e("MusicService", "错误: MediaPlayer创建失败!")
            throw IllegalStateException("MediaPlayer创建失败，资源ID: ${music.resourceId}")
        }

        // 释放旧的MediaPlayer，使用新的
        if (::mediaPlayer.isInitialized) {
            try {
                mediaPlayer.release()
            } catch (e: Exception) {
                Log.e("MusicService", "释放旧MediaPlayer失败", e)
            }
        }

        mediaPlayer = player
        Log.d("MusicService", "内置音乐MediaPlayer创建成功")

        // 重新设置完成监听器
        mediaPlayer.setOnCompletionListener {
            Log.d("MusicService", "内置音乐播放完成")
            onMusicCompletion()
        }
    }

    fun startMusic() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
            updateNotification()
            Log.d("MusicService", "音乐开始播放")
        }
    }

    fun pauseMusic() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            updateNotification()
            Log.d("MusicService", "音乐暂停")
        }
    }

    fun resumeMusic() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
            updateNotification()
            Log.d("MusicService", "音乐恢复播放")
        }
    }

    fun stopMusic() {
        if (::mediaPlayer.isInitialized) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            isPlaying = false
            updateNotification()
            Log.d("MusicService", "音乐停止")
        }
    }

    fun seekTo(position: Int) {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.seekTo(position)
            Log.d("MusicService", "跳转到位置: $position")
        }
    }

    fun getCurrentPosition(): Int {
        return if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.currentPosition
        } else {
            0
        }
    }

    fun getDuration(): Int {
        return if (::mediaPlayer.isInitialized) {
            mediaPlayer.duration
        } else {
            0
        }
    }

    fun isMusicPlaying(): Boolean {
        return if (::mediaPlayer.isInitialized) {
            mediaPlayer.isPlaying
        } else {
            false
        }
    }

    fun getCurrentMusic(): Music? = currentMusic

    fun setOnCompletionListener(listener: MediaPlayer.OnCompletionListener) {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.setOnCompletionListener(listener)
        }
    }

    private fun onMusicCompletion() {
        isPlaying = false
        Log.d("MusicService", "音乐播放完成，停止前台服务")
        stopForeground(true)
        stopSelf()
    }

    private fun startForegroundService() {
        try {
            val notification = createNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d("MusicService", "前台服务启动成功")
        } catch (e: Exception) {
            Log.e("MusicService", "启动前台服务失败", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音乐播放器",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制"
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("MusicService", "通知渠道创建成功")
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentMusic?.title ?: "车载音乐播放器")
            .setContentText(currentMusic?.artist ?: "未播放音乐")
            .setSmallIcon(R.drawable.ic_play)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.vinyl_record))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        try {
            val notification = createNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("MusicService", "通知更新")
        } catch (e: Exception) {
            Log.e("MusicService", "更新通知失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MusicService", "Service销毁")

        if (::mediaPlayer.isInitialized) {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
                Log.d("MusicService", "MediaPlayer已释放")
            } catch (e: Exception) {
                Log.e("MusicService", "释放MediaPlayer失败", e)
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_channel"
    }
}
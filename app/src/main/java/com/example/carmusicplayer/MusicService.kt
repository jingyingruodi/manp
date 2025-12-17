package com.example.carmusicplayer

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.example.carmusicplayer.notification.MusicNotificationManager
import java.io.File
import java.io.IOException

class MusicService : Service() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var notificationManager: MusicNotificationManager
    private lateinit var mediaSession: MediaSessionCompat 
    private val binder = MusicBinder()
    
    // 播放列表数据
    private var musicList: List<Music> = ArrayList()
    private var currentPosition: Int = -1
    
    private var isPlaying = false
    
    // 监听器
    private var externalCompletionListener: MediaPlayer.OnCompletionListener? = null
    private var onMusicChangeListener: ((Music) -> Unit)? = null
    private var onPlayStateChangeListener: ((Boolean) -> Unit)? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "Service创建")
        
        // 1. 初始化 NotificationManager
        notificationManager = MusicNotificationManager(this)

        // 2. 初始化 MediaSession
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resumeMusic() }
                override fun onPause() { pauseMusic() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onStop() { stopMusic() }
            })
        }

        // 3. 立即启动前台服务（关键修复：防止服务被杀）
        // buildNotification 可能需要一个 Music 对象，这里传 null，manager 内部处理
        try {
            startForeground(MusicNotificationManager.NOTIFICATION_ID, 
                notificationManager.buildNotification(null, false))
        } catch (e: Exception) {
            Log.e("MusicService", "启动前台服务失败", e)
        }

        // 4. 初始化播放器
        initMediaPlayer()
    }

    fun getMediaSessionToken() = mediaSession.sessionToken

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        setupCompletionListener()
    }

    private fun setupCompletionListener() {
        mediaPlayer.setOnCompletionListener { mp ->
            Log.d("MusicService", "音乐播放完成")
            playNext()
            externalCompletionListener?.onCompletion(mp)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("MusicService", "收到命令: $action")
        
        if (action != null) {
            when (action) {
                MusicNotificationManager.ACTION_PREV -> playPrevious()
                MusicNotificationManager.ACTION_NEXT -> playNext()
                MusicNotificationManager.ACTION_STOP -> stopMusic()
                MusicNotificationManager.ACTION_PLAY_PAUSE -> {
                    if (isPlaying) pauseMusic() else resumeMusic()
                }
                // 兼容旧的Action定义，防止有地方没改过来
                "com.example.carmusicplayer.ACTION_PLAY" -> resumeMusic()
                "com.example.carmusicplayer.ACTION_PAUSE" -> pauseMusic()
            }
        }
        
        // 确保服务在前台，更新通知
        updateNotification()
        
        return START_NOT_STICKY
    }

    fun setPlaylist(list: List<Music>) {
        this.musicList = list
    }
    
    fun getPlaylist(): List<Music> = musicList

    fun playMusicAt(position: Int) {
        if (musicList.isEmpty() || position < 0 || position >= musicList.size) return
        currentPosition = position
        playMusic(musicList[position])
    }

    private fun playMusic(music: Music) {
        try {
            Log.d("MusicService", "开始播放: ${music.title}")
            
            safeResetMediaPlayer()

            if (music.isImported) {
                val file = File(music.filePath)
                if (!file.exists() || !file.canRead()) throw IOException("文件不可读")
                mediaPlayer.setDataSource(music.filePath)
            } else {
                val player = MediaPlayer.create(this, music.resourceId) ?: throw IllegalStateException("创建失败")
                if (::mediaPlayer.isInitialized) mediaPlayer.release()
                mediaPlayer = player
            }
            
            // 如果是 create() 创建的已经 prepared 了，只有 dataSource 方式需要 prepare
            if (music.isImported) {
                mediaPlayer.prepare()
            }
            
            setupCompletionListener()
            mediaPlayer.start()

            isPlaying = true
            updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING)
            updateNotification()
            
            onMusicChangeListener?.invoke(music)
            onPlayStateChangeListener?.invoke(true)

        } catch (e: Exception) {
            Log.e("MusicService", "播放失败", e)
            isPlaying = false
            updateMediaSessionState(PlaybackStateCompat.STATE_ERROR)
            updateNotification()
        }
    }
    
    fun playNext() {
        if (musicList.isEmpty()) return
        val nextPos = (currentPosition + 1) % musicList.size
        playMusicAt(nextPos)
    }

    fun playPrevious() {
        if (musicList.isEmpty()) return
        val prevPos = if (currentPosition - 1 < 0) musicList.size - 1 else currentPosition - 1
        playMusicAt(prevPos)
    }

    private fun safeResetMediaPlayer() {
        try {
            if (::mediaPlayer.isInitialized) {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.reset()
            } else {
                initMediaPlayer()
            }
        } catch (e: Exception) {
            initMediaPlayer()
        }
    }

    fun pauseMusic() {
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            updateMediaSessionState(PlaybackStateCompat.STATE_PAUSED)
            updateNotification()
            onPlayStateChangeListener?.invoke(false)
        }
    }

    fun resumeMusic() {
        if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
            updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING)
            updateNotification()
            onPlayStateChangeListener?.invoke(true)
        } else if (!isPlaying && currentPosition != -1) {
            playMusicAt(currentPosition)
        }
    }

    fun stopMusic() {
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        isPlaying = false
        updateMediaSessionState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(true)
        notificationManager.cancelAll()
    }
    
    private fun updateMediaSessionState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, getCurrentPosition().toLong(), 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }
    
    private fun updateNotification() {
        val currentMusic = getCurrentMusic()
        // 无论如何都要更新通知，保持服务在前台
        try {
            // 注意：这里使用 updateNotification 方法，它内部调用 notify
            notificationManager.updateNotification(currentMusic, isPlaying)
        } catch (e: Exception) {
            Log.e("MusicService", "更新通知失败", e)
        }
    }

    fun getCurrentMusic(): Music? {
        if (currentPosition >= 0 && currentPosition < musicList.size) {
            return musicList[currentPosition]
        }
        return null
    }

    fun seekTo(position: Int) {
        if (::mediaPlayer.isInitialized) mediaPlayer.seekTo(position)
    }

    fun getCurrentPosition(): Int = if (::mediaPlayer.isInitialized) try { mediaPlayer.currentPosition } catch (e: Exception) { 0 } else 0
    fun getDuration(): Int = if (::mediaPlayer.isInitialized) try { mediaPlayer.duration } catch (e: Exception) { 0 } else 0
    fun isMusicPlaying(): Boolean = isPlaying

    fun setOnMusicChangeListener(listener: (Music) -> Unit) {
        this.onMusicChangeListener = listener
    }
    
    fun setOnPlayStateChangeListener(listener: (Boolean) -> Unit) {
        this.onPlayStateChangeListener = listener
    }
    
    fun setOnCompletionListener(listener: MediaPlayer.OnCompletionListener) {
        this.externalCompletionListener = listener
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        mediaSession.release()
        notificationManager.cancelAll()
    }
}

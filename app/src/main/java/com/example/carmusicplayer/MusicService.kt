package com.example.carmusicplayer

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.carmusicplayer.notification.MusicNotificationManager
import java.io.File
import java.io.IOException

class MusicService : Service() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var notificationManager: MusicNotificationManager
    private val binder = MusicBinder()
    
    // 播放列表数据
    private var musicList: List<Music> = ArrayList()
    private var currentPosition: Int = -1
    
    private var isPlaying = false
    
    // 监听器
    private var externalCompletionListener: MediaPlayer.OnCompletionListener? = null
    // UI更新回调
    private var onMusicChangeListener: ((Music) -> Unit)? = null
    private var onPlayStateChangeListener: ((Boolean) -> Unit)? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "Service创建")
        
        notificationManager = MusicNotificationManager(this)
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        setupCompletionListener()
    }

    private fun setupCompletionListener() {
        mediaPlayer.setOnCompletionListener { mp ->
            Log.d("MusicService", "音乐播放完成")
            // 自动播放下一首
            playNext()
            
            // 通知外部
            externalCompletionListener?.onCompletion(mp)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("MusicService", "收到命令: $action")
        
        when (action) {
            MusicNotificationManager.ACTION_PLAY -> resumeMusic()
            MusicNotificationManager.ACTION_PAUSE -> pauseMusic()
            MusicNotificationManager.ACTION_PREV -> playPrevious()
            MusicNotificationManager.ACTION_NEXT -> playNext()
            MusicNotificationManager.ACTION_STOP -> stopMusic()
        }
        
        // 确保前台服务一直运行，哪怕是暂停状态，只要没Stop
        val currentMusic = getCurrentMusic()
        if (currentMusic != null) {
            startForeground(MusicNotificationManager.NOTIFICATION_ID, 
                notificationManager.createNotification(currentMusic, isPlaying))
        }
        
        return START_STICKY
    }

    // 设置播放列表
    fun setPlaylist(list: List<Music>) {
        this.musicList = list
    }
    
    // 获取当前播放列表
    fun getPlaylist(): List<Music> = musicList

    // 播放指定位置的音乐
    fun playMusicAt(position: Int) {
        if (musicList.isEmpty() || position < 0 || position >= musicList.size) return
        
        currentPosition = position
        playMusic(musicList[position])
    }

    private fun playMusic(music: Music) {
        try {
            Log.d("MusicService", "开始播放: ${music.title}")
            resetMediaPlayer()

            if (music.isImported) {
                playImportedMusic(music)
            } else {
                playBuiltInMusic(music)
            }

            isPlaying = true
            updateNotification()
            
            // 通知UI更新
            onMusicChangeListener?.invoke(music)
            onPlayStateChangeListener?.invoke(true)

        } catch (e: Exception) {
            Log.e("MusicService", "播放失败", e)
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

    private fun resetMediaPlayer() {
        try {
            if (::mediaPlayer.isInitialized) {
                mediaPlayer.reset()
            } else {
                initMediaPlayer()
            }
        } catch (e: Exception) {
            initMediaPlayer()
        }
    }

    private fun playImportedMusic(music: Music) {
        val file = File(music.filePath)
        if (!file.exists() || !file.canRead()) throw IOException("文件不可读")
        mediaPlayer.setDataSource(music.filePath)
        mediaPlayer.prepare()
        setupCompletionListener()
        mediaPlayer.start()
    }

    private fun playBuiltInMusic(music: Music) {
        val player = MediaPlayer.create(this, music.resourceId) ?: throw IllegalStateException("创建失败")
        if (::mediaPlayer.isInitialized) mediaPlayer.release()
        mediaPlayer = player
        setupCompletionListener()
        mediaPlayer.start()
    }

    fun pauseMusic() {
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            updateNotification()
            onPlayStateChangeListener?.invoke(false)
        }
    }

    fun resumeMusic() {
        // 如果当前有音乐但没播放，尝试恢复
        if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            mediaPlayer.start()
            isPlaying = true
            updateNotification()
            onPlayStateChangeListener?.invoke(true)
        } else if (!isPlaying && currentPosition != -1) {
             // 如果MediaPlayer被重置了但我们有记录，尝试重新播放
             playMusicAt(currentPosition)
        }
    }

    fun stopMusic() {
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        isPlaying = false
        stopForeground(true)
    }
    
    private fun updateNotification() {
        val currentMusic = getCurrentMusic()
        if (currentMusic != null) {
            notificationManager.updateNotification(currentMusic, isPlaying)
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

    fun getCurrentPosition(): Int = if (::mediaPlayer.isInitialized) mediaPlayer.currentPosition else 0
    fun getDuration(): Int = if (::mediaPlayer.isInitialized) mediaPlayer.duration else 0
    fun isMusicPlaying(): Boolean = isPlaying

    // 各种监听器设置
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
        if (::mediaPlayer.isInitialized) mediaPlayer.release()
        notificationManager.cancelNotification()
    }
}

package com.example.carmusicplayer

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var musicAdapter: MusicAdapter
    private lateinit var ivVinyl: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var tvNowPlayingTitle: TextView
    private lateinit var tvNowPlayingArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    private lateinit var musicService: MusicService
    private var isServiceBound = false
    private val updateHandler = Handler(Looper.getMainLooper())
    private var rotationAnimator: ObjectAnimator? = null

    private val musicList: MutableList<Music> by lazy {
        MusicManager.getAllMusic(this).toMutableList()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as MusicService.MusicBinder
                musicService = binder.getService()
                isServiceBound = true
                
                // 1. 同步播放列表给 Service
                musicService.setPlaylist(musicList)

                // 2. 监听音乐变化（来自通知栏切歌等）
                musicService.setOnMusicChangeListener { music ->
                    updateUIForMusic(music)
                }
                
                // 3. 监听播放状态变化
                musicService.setOnPlayStateChangeListener { isPlaying ->
                    updatePlayPauseButton(isPlaying)
                    if (isPlaying) {
                        resumeVinylRotation()
                        updateHandler.post(updateSeekBar)
                    } else {
                        pauseVinylRotation()
                        updateHandler.removeCallbacks(updateSeekBar)
                    }
                }

                // 4. 恢复 UI 状态
                val currentMusic = musicService.getCurrentMusic()
                if (currentMusic != null) {
                    updateUIForMusic(currentMusic)
                    if (musicService.isMusicPlaying()) {
                        resumeVinylRotation()
                        updatePlayPauseButton(true)
                        updateHandler.post(updateSeekBar)
                    } else {
                        updatePlayPauseButton(false)
                        // 即使暂停也更新一次进度条位置
                        seekBar.progress = musicService.getCurrentPosition()
                        tvCurrentTime.text = formatTime(musicService.getCurrentPosition().toLong())
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Service连接失败", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
        }
    }

    private val updateSeekBar = object : Runnable {
        override fun run() {
            if (isServiceBound && musicService.isMusicPlaying()) {
                val currentTime = musicService.getCurrentPosition()
                seekBar.progress = currentTime
                tvCurrentTime.text = formatTime(currentTime.toLong())
                updateHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupListeners()
        bindMusicService()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        ivVinyl = findViewById(R.id.ivVinyl)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)
        seekBar = findViewById(R.id.seekBar)
        tvNowPlayingTitle = findViewById(R.id.tvNowPlayingTitle)
        tvNowPlayingArtist = findViewById(R.id.tvNowPlayingArtist)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(musicList) { music ->
            val position = musicList.indexOf(music)
            if (position != -1 && isServiceBound) {
                musicService.playMusicAt(position)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = musicAdapter
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            if (!isServiceBound) return@setOnClickListener
            
            if (musicService.isMusicPlaying()) {
                musicService.pauseMusic()
            } else {
                musicService.resumeMusic()
            }
        }

        btnPrev.setOnClickListener {
            if (isServiceBound) musicService.playPrevious()
        }

        btnNext.setOnClickListener {
            if (isServiceBound) musicService.playNext()
        }

        btnBack.setOnClickListener { finish() }

        findViewById<Button>(R.id.dao_ru).setOnClickListener {
            val intent = Intent(this, ImportMusicActivity::class.java)
            startActivityForResult(intent, IMPORT_MUSIC_REQUEST)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                updateHandler.removeCallbacks(updateSeekBar)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (isServiceBound) musicService.seekTo(seekBar.progress)
                updateHandler.post(updateSeekBar)
            }
        })
    }

    private fun updateUIForMusic(music: Music) {
        tvNowPlayingTitle.text = music.title
        tvNowPlayingArtist.text = music.artist
        tvTotalTime.text = music.getFormattedDuration()
        
        seekBar.max = music.duration.toInt()
        seekBar.progress = 0
        tvCurrentTime.text = "0:00"

        val position = musicList.indexOfFirst { it.title == music.title }
        if (position != -1) {
            musicAdapter.setSelectedPosition(position)
        }
        
        startVinylRotation()
    }

    private fun updatePlayPauseButton(playing: Boolean) {
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun startVinylRotation() {
        if (rotationAnimator == null) {
            rotationAnimator = ObjectAnimator.ofFloat(ivVinyl, View.ROTATION, 0f, 360f).apply {
                duration = 3000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }
        if (musicService.isMusicPlaying() && !rotationAnimator!!.isStarted) {
            rotationAnimator?.start()
        }
    }

    private fun pauseVinylRotation() {
        rotationAnimator?.pause()
    }

    private fun resumeVinylRotation() {
        if (rotationAnimator?.isPaused == true) {
            rotationAnimator?.resume()
        } else {
            rotationAnimator?.start()
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = millis / 1000 / 60
        val seconds = millis / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    companion object {
        private const val IMPORT_MUSIC_REQUEST = 1001
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_MUSIC_REQUEST && resultCode == RESULT_OK) {
            val newMusicList = MusicManager.getAllMusic(this)
            musicList.clear()
            musicList.addAll(newMusicList)
            musicAdapter.updateData(musicList)
            if (isServiceBound) {
                musicService.setPlaylist(musicList)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isServiceBound) bindMusicService()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        updateHandler.removeCallbacks(updateSeekBar)
    }
}

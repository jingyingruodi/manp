package com.example.carmusicplayer

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
    private var isPlaying = false
    private var currentPosition = 0
    private val updateHandler = Handler(Looper.getMainLooper())
    private var rotationAnimator: ObjectAnimator? = null

    private val musicList = listOf(
        Music(1, "Drive", "The Cars", 195000, R.raw.aoteman),
        Music(2, "Highway to Hell", "AC/DC", 208000, R.raw.doushiyeguiren),
        Music(3, "Born to Be Wild", "Steppenwolf", 213000, R.raw.game),
        Music(4, "Life is a Highway", "Rascal Flatts", 268000, R.raw.mingyunjiaoxiangqu),
        Music(5, "I'm Gonna Be (500 Miles)", "The Proclaimers", 217000, R.raw.zaijianzhongguohai)
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true

            // 设置播放完成监听器
            musicService.setOnCompletionListener {
                playNext()
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

        // 绑定音乐服务
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        startService(intent)
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
            if (position != -1) {
                playMusic(position)
                musicAdapter.setSelectedPosition(position)
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = musicAdapter
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pauseMusic()
            } else {
                if (isServiceBound && musicService.isMusicPlaying()) {
                    resumeMusic()
                } else if (musicList.isNotEmpty()) {
                    playMusic(currentPosition)
                }
            }
        }

        btnPrev.setOnClickListener {
            playPrevious()
        }

        btnNext.setOnClickListener {
            playNext()
        }

        btnBack.setOnClickListener {
            finish()
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
                if (isServiceBound) {
                    musicService.seekTo(seekBar.progress)
                }
                updateHandler.post(updateSeekBar)
            }
        })
    }

    private fun playMusic(position: Int) {
        currentPosition = position
        val music = musicList[position]

        if (isServiceBound) {
            musicService.playMusic(music)
        }

        tvNowPlayingTitle.text = music.title
        tvNowPlayingArtist.text = music.artist
        tvTotalTime.text = music.getFormattedDuration()
        seekBar.max = music.duration.toInt()
        seekBar.progress = 0

        startVinylRotation()
        musicAdapter.setSelectedPosition(position)
        updatePlayPauseButton(true)
        isPlaying = true

        // 开始更新进度条
        updateHandler.post(updateSeekBar)
    }

    private fun startMusic() {
        if (isServiceBound) {
            musicService.startMusic()
        }
        isPlaying = true
        updatePlayPauseButton(true)
        updateHandler.post(updateSeekBar)
        resumeVinylRotation()
    }

    private fun pauseMusic() {
        if (isServiceBound) {
            musicService.pauseMusic()
        }
        isPlaying = false
        updatePlayPauseButton(false)
        updateHandler.removeCallbacks(updateSeekBar)
        pauseVinylRotation()
    }

    private fun resumeMusic() {
        if (isServiceBound) {
            musicService.resumeMusic()
        }
        isPlaying = true
        updatePlayPauseButton(true)
        updateHandler.post(updateSeekBar)
        resumeVinylRotation()
    }

    private fun playNext() {
        val nextPosition = (currentPosition + 1) % musicList.size
        playMusic(nextPosition)
    }

    private fun playPrevious() {
        val prevPosition = if (currentPosition - 1 < 0) {
            musicList.size - 1
        } else {
            currentPosition - 1
        }
        playMusic(prevPosition)
    }

    private fun updatePlayPauseButton(playing: Boolean) {
        if (playing) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun startVinylRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = ObjectAnimator.ofFloat(ivVinyl, View.ROTATION, 0f, 360f).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun pauseVinylRotation() {
        rotationAnimator?.pause()
    }

    private fun resumeVinylRotation() {
        rotationAnimator?.resume()
    }

    private fun formatTime(millis: Long): String {
        val minutes = millis / 1000 / 60
        val seconds = millis / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        updateHandler.removeCallbacks(updateSeekBar)
        rotationAnimator?.cancel()
    }
}
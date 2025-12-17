package com.example.carmusicplayer

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
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
import java.io.IOException

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

    // 动态获取音乐列表
    private val musicList: MutableList<Music> by lazy {
        MusicManager.getAllMusic(this).toMutableList()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MainActivity", "Service连接成功")
            try {
                val binder = service as MusicService.MusicBinder
                musicService = binder.getService()
                isServiceBound = true

                // 设置播放完成监听器
                musicService.setOnCompletionListener {
                    playNext()
                }

                Log.d("MainActivity", "Service绑定完成，可以播放音乐")

                // 恢复之前的播放状态
                val currentMusic = musicService.getCurrentMusic()
                if (currentMusic != null && musicService.isMusicPlaying()) {
                    val position = musicList.indexOfFirst { it.title == currentMusic.title }
                    if (position != -1) {
                        currentPosition = position
                        isPlaying = true
                        updatePlayPauseButton(true)
                        startVinylRotation()

                        // 更新UI
                        tvNowPlayingTitle.text = currentMusic.title
                        tvNowPlayingArtist.text = currentMusic.artist
                        tvTotalTime.text = currentMusic.getFormattedDuration()
                        seekBar.max = musicService.getDuration()
                        seekBar.progress = musicService.getCurrentPosition()

                        musicAdapter.setSelectedPosition(position)
                        updateHandler.post(updateSeekBar)
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Service连接失败", e)
                isServiceBound = false
                Toast.makeText(this@MainActivity, "音乐服务启动失败", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("MainActivity", "Service断开连接")
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

        Log.d("MainActivity", "Activity创建")

        initViews()
        setupRecyclerView()
        setupListeners()

        // 绑定音乐服务
        bindMusicService()
    }

    private fun bindMusicService() {
        Log.d("MainActivity", "开始绑定音乐服务")
        try {
            val intent = Intent(this, MusicService::class.java)

            // Android 8.0+ 需要使用startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            Log.d("MainActivity", "Service已启动")

            // 绑定服务
            val bindResult = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d("MainActivity", "绑定结果: $bindResult")

            if (!bindResult) {
                Log.e("MainActivity", "绑定Service失败")
                Toast.makeText(this, "音乐服务启动失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "绑定Service异常", e)
            Toast.makeText(this, "音乐服务启动异常", Toast.LENGTH_SHORT).show()
        }
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
            Log.d("MainActivity", "播放/暂停按钮点击")
            Log.d("MainActivity", "Service绑定状态: $isServiceBound")
            Log.d("MainActivity", "当前播放状态: $isPlaying")

            if (!isServiceBound) {
                Toast.makeText(this, "音乐服务未就绪", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isPlaying) {
                pauseMusic()
            } else {
                if (musicList.isEmpty()) {
                    Toast.makeText(this, "请先选择音乐", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 如果没有正在播放的音乐，播放当前选中的或第一首
                if (!musicService.isMusicPlaying()) {
                    if (currentPosition < 0 || currentPosition >= musicList.size) {
                        currentPosition = 0
                    }
                    playMusic(currentPosition)
                } else {
                    resumeMusic()
                }
            }
        }

        btnPrev.setOnClickListener {
            if (isServiceBound && musicList.isNotEmpty()) {
                playPrevious()
            } else {
                Toast.makeText(this, "音乐服务未就绪", Toast.LENGTH_SHORT).show()
            }
        }

        btnNext.setOnClickListener {
            if (isServiceBound && musicList.isNotEmpty()) {
                playNext()
            } else {
                Toast.makeText(this, "音乐服务未就绪", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }

        // 添加导入按钮监听
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
                if (isServiceBound) {
                    musicService.seekTo(seekBar.progress)
                }
                updateHandler.post(updateSeekBar)
            }
        })
    }

    private fun playMusic(position: Int) {
        Log.d("MainActivity", "尝试播放音乐，位置: $position")
        Log.d("MainActivity", "Service绑定状态: $isServiceBound")
        Log.d("MainActivity", "音乐列表大小: ${musicList.size}")

        if (!isServiceBound) {
            Log.e("MainActivity", "播放失败: Service未绑定")
            Toast.makeText(this, "音乐服务未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        if (position < 0 || position >= musicList.size) {
            Log.e("MainActivity", "播放失败: 位置无效 $position")
            Toast.makeText(this, "无效的播放位置", Toast.LENGTH_SHORT).show()
            return
        }

        currentPosition = position
        val music = musicList[position]

        try {
            Log.d("MainActivity", "开始播放: ${music.title}")
            Log.d("MainActivity", "是否为导入音乐: ${music.isImported}")
            Log.d("MainActivity", "文件路径: ${music.filePath}")

            // 验证导入音乐文件是否存在
            if (music.isImported) {
                val file = File(music.filePath)
                if (!file.exists()) {
                    Log.e("MainActivity", "导入音乐文件不存在: ${music.filePath}")
                    Toast.makeText(this, "音乐文件不存在，请重新导入", Toast.LENGTH_LONG).show()
                    return
                }
                if (!file.canRead()) {
                    Log.e("MainActivity", "导入音乐文件不可读: ${music.filePath}")
                    Toast.makeText(this, "音乐文件无法读取", Toast.LENGTH_LONG).show()
                    return
                }
            }

            musicService.playMusic(music)

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

            Log.d("MainActivity", "播放命令发送成功")

        } catch (e: Exception) {
            Log.e("MainActivity", "播放失败", e)

            // 显示更友好的错误信息
            val errorMessage = when {
                e.message?.contains("文件不存在") == true -> "音乐文件不存在"
                e.message?.contains("prepare") == true -> "音乐文件格式不支持"
                e.message?.contains("Permission") == true -> "没有文件访问权限"
                else -> "播放失败: ${e.message}"
            }

            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

            // 如果是导入音乐的问题，重新加载列表
            if (music.isImported) {
                refreshMusicList()
            }
        }
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

    // 导入音乐相关常量和方法
    companion object {
        private const val IMPORT_MUSIC_REQUEST = 1001
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_MUSIC_REQUEST && resultCode == RESULT_OK) {
            // 刷新音乐列表
            refreshMusicList()
            Toast.makeText(this, "音乐列表已更新", Toast.LENGTH_SHORT).show()
        }
    }

    // 刷新音乐列表
    private fun refreshMusicList() {
        // 重新获取音乐列表
        val newMusicList = MusicManager.getAllMusic(this)

        // 清空并重新添加
        musicList.clear()
        musicList.addAll(newMusicList)

        // 更新适配器
        musicAdapter.updateData(musicList)

        Log.d("MainActivity", "音乐列表刷新完成，新列表大小: ${musicList.size}")

        // 如果有导入的音乐，显示提示
        val importedCount = musicList.count { it.isImported }
        if (importedCount > 0) {
            Toast.makeText(this, "已加载 $importedCount 首导入音乐", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "Activity恢复，Service绑定状态: $isServiceBound")

        // 如果Service未绑定，尝试重新绑定
        if (!isServiceBound) {
            bindMusicService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "Activity销毁")

        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
                isServiceBound = false
                Log.d("MainActivity", "Service已解绑")
            } catch (e: Exception) {
                Log.e("MainActivity", "解绑Service失败", e)
            }
        }

        updateHandler.removeCallbacks(updateSeekBar)
        rotationAnimator?.cancel()
    }
}
package com.example.carmusicplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ImportMusicActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MusicFileAdapter
    private lateinit var tvEmptyHint: TextView
    private lateinit var btnBack: View
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnAddMusic: Button

    private val musicFiles = mutableListOf<MusicFile>()
    private var nextId = 1

    // 注册文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importMusicFile(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_music)

        initViews()
        setupRecyclerView()
        setupListeners()
        loadExistingMusic()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewMusicFiles)
        tvEmptyHint = findViewById(R.id.tvEmptyHint)
        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        btnAddMusic = findViewById(R.id.btnAddMusic)
    }

    private fun setupRecyclerView() {
        adapter = MusicFileAdapter(musicFiles) { musicFile ->
            deleteMusicFile(musicFile)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        btnSave.setOnClickListener {
            saveAndReturn()
        }

        btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        btnAddMusic.setOnClickListener {
            openFilePicker()
        }
    }

    private fun loadExistingMusic() {
        Log.d("ImportMusic", "开始加载现有音乐")

        // 1. 加载原有的内置音乐
        val rawFiles = listOf(
            Pair("ディザーチューン（Dither Tune）.mp3", R.raw.dithertune)
        )

        rawFiles.forEachIndexed { index, (fileName, resId) ->
            musicFiles.add(
                MusicFile(
                    id = nextId++,
                    fileName = fileName,
                    filePath = "android.resource://$packageName/$resId",
                    fileSize = (2 + index) * 1024 * 1024L,
                    isRawResource = true
                )
            )
        }

        // 2. 加载之前已保存的导入音乐
        loadSavedImportedMusic()

        updateEmptyState()
        adapter.updateData(musicFiles)

        Log.d("ImportMusic", "加载完成，总音乐文件数: ${musicFiles.size}")
        Log.d("ImportMusic", "其中导入音乐数: ${musicFiles.count { !it.isRawResource }}")
    }

    private fun loadSavedImportedMusic() {
        try {
            // 从SharedPreferences加载之前保存的导入音乐
            val importedList = MusicManager.getImportedMusicData(this)
            Log.d("ImportMusic", "从SharedPreferences加载导入音乐: ${importedList.size} 首")

            importedList.forEach { imported ->
                // 检查文件是否还存在
                val file = File(imported.filePath)
                if (file.exists() && file.canRead()) {
                    musicFiles.add(
                        MusicFile(
                            id = nextId++,
                            fileName = imported.fileName,
                            filePath = imported.filePath,
                            fileSize = imported.fileSize,
                            isRawResource = false
                        )
                    )
                    Log.d("ImportMusic", "加载已保存的导入音乐: ${imported.fileName}")
                } else {
                    Log.w("ImportMusic", "导入音乐文件不存在或不可读: ${imported.fileName}")
                }
            }
        } catch (e: Exception) {
            Log.e("ImportMusic", "加载保存的导入音乐失败", e)
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }

        filePickerLauncher.launch(intent)
    }

    private fun importMusicFile(uri: Uri) {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    val fileName = cursor.getString(displayNameIndex) ?: "unknown.mp3"
                    val fileSize = cursor.getLong(sizeIndex)

                    Log.d("ImportMusic", "选择文件: $fileName, 大小: $fileSize 字节")

                    // 检查是否已存在相同文件名的音乐（忽略路径，只比较文件名）
                    if (musicFiles.any { it.fileName.equals(fileName, ignoreCase = true) }) {
                        Toast.makeText(this, "文件已存在: $fileName", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // 复制文件到应用私有目录
                    val importedFile = copyFileToPrivateStorage(uri, fileName)
                    if (importedFile != null) {
                        val musicFile = MusicFile(
                            id = nextId++,
                            fileName = fileName,
                            filePath = importedFile.absolutePath,
                            fileSize = fileSize,
                            isRawResource = false
                        )

                        musicFiles.add(musicFile)
                        adapter.updateData(musicFiles)
                        updateEmptyState()

                        Log.d("ImportMusic", "导入成功: $fileName, 路径: ${importedFile.absolutePath}")
                        Toast.makeText(this, "导入成功: $fileName", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("ImportMusic", "复制文件失败")
                        Toast.makeText(this, "导入失败: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImportMusic", "导入文件出错", e)
            Toast.makeText(this, "导入文件出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyFileToPrivateStorage(uri: Uri, fileName: String): File? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputDir = File(filesDir, "imported_music")

            if (!outputDir.exists()) {
                outputDir.mkdirs()
                Log.d("ImportMusic", "创建导入目录: ${outputDir.absolutePath}")
            }

            val outputFile = File(outputDir, fileName)
            val outputStream = FileOutputStream(outputFile)

            inputStream?.copyTo(outputStream)

            inputStream?.close()
            outputStream.close()

            Log.d("ImportMusic", "文件复制完成: ${outputFile.absolutePath}, 大小: ${outputFile.length()} 字节")

            outputFile
        } catch (e: Exception) {
            Log.e("ImportMusic", "复制文件失败", e)
            null
        }
    }

    private fun deleteMusicFile(musicFile: MusicFile) {
        // 如果是raw资源，不能删除
        if (musicFile.isRawResource) {
            Toast.makeText(this, "系统内置音乐不能删除", Toast.LENGTH_SHORT).show()
            return
        }

        // 删除文件
        val file = File(musicFile.filePath)
        if (file.exists() && file.delete()) {
            musicFiles.remove(musicFile)
            adapter.updateData(musicFiles)
            updateEmptyState()

            Log.d("ImportMusic", "已删除文件: ${musicFile.fileName}")
            Toast.makeText(this, "已删除: ${musicFile.fileName}", Toast.LENGTH_SHORT).show()
        } else {
            // 如果文件不存在，也移除列表中的记录
            musicFiles.remove(musicFile)
            adapter.updateData(musicFiles)
            updateEmptyState()

            Log.w("ImportMusic", "文件不存在，已从列表中移除: ${musicFile.fileName}")
            Toast.makeText(this, "已移除: ${musicFile.fileName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        if (musicFiles.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvEmptyHint.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyHint.visibility = View.GONE
        }
    }

    private fun saveAndReturn() {
        try {
            // 获取所有导入的音乐文件（排除内置的raw资源）
            val importedMusicFiles = musicFiles.filter { !it.isRawResource }

            Log.d("ImportMusic", "准备保存导入音乐: ${importedMusicFiles.size} 首")
            importedMusicFiles.forEachIndexed { index, file ->
                Log.d("ImportMusic", "导入音乐${index + 1}: ${file.fileName}, 路径: ${file.filePath}")
            }

            // 保存到SharedPreferences
            MusicManager.saveImportedMusic(this, importedMusicFiles)

            Toast.makeText(this, "已保存 ${importedMusicFiles.size} 首导入音乐", Toast.LENGTH_SHORT).show()

            val intent = Intent()
            setResult(RESULT_OK, intent)
            finish()

        } catch (e: Exception) {
            Log.e("ImportMusic", "保存失败", e)
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
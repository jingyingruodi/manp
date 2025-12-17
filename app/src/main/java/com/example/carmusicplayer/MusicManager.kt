package com.example.carmusicplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object MusicManager {
    private const val PREF_NAME = "music_prefs"
    private const val IMPORTED_MUSIC_KEY = "imported_music_list"

    // 获取所有音乐（内置 + 导入）
    fun getAllMusic(context: Context): List<Music> {
        val musicList = mutableListOf<Music>()

        // 1. 添加内置音乐
        musicList.addAll(getBuiltInMusic())

        // 2. 添加导入的音乐
        musicList.addAll(getImportedMusic(context))

        Log.d("MusicManager", "总音乐数: ${musicList.size}")
        return musicList
    }

    // 获取内置音乐
    private fun getBuiltInMusic(): List<Music> {
        return listOf(
            Music(1, "ディザーチューン（Dither Tune）", "DIVELA feat.初音ミク", 223000, R.raw.dithertune)
        )
    }

    // 获取导入的音乐
    private fun getImportedMusic(context: Context): List<Music> {
        val importedMusic = mutableListOf<Music>()
        val importedData = getImportedMusicData(context)

        Log.d("MusicManager", "从存储加载导入音乐数据: ${importedData.size} 条")

        var id = 100 // 从100开始，避免与内置音乐ID冲突
        importedData.forEachIndexed { index, imported ->
            try {
                // 检查文件是否存在
                val file = File(imported.filePath)
                if (!file.exists()) {
                    Log.w("MusicManager", "导入音乐文件不存在: ${imported.fileName}")
                    return@forEachIndexed
                }

                if (!file.canRead()) {
                    Log.w("MusicManager", "导入音乐文件不可读: ${imported.fileName}")
                    return@forEachIndexed
                }

                // 修改：使用 extractMetadataFromMedia 提取详细信息
                val metadata = extractMetadataFromMedia(imported.filePath, imported.fileName)

                importedMusic.add(
                    Music(
                        id = id++,
                        title = metadata.title,
                        artist = metadata.artist, // 显示真实的歌手名
                        duration = metadata.duration,
                        filePath = imported.filePath,
                        isImported = true
                    )
                )

                Log.d("MusicManager", "加载导入音乐: ${metadata.title} - ${metadata.artist}")

            } catch (e: Exception) {
                Log.e("MusicManager", "加载导入音乐失败: ${imported.fileName}", e)
            }
        }

        return importedMusic
    }

    // 辅助数据类
    private data class MetadataResult(val title: String, val artist: String, val duration: Long)

    // 新增：提取媒体元数据（标题、歌手、时长）
    private fun extractMetadataFromMedia(filePath: String, defaultFileName: String): MetadataResult {
        val retriever = MediaMetadataRetriever()
        var title = defaultFileName.substringBeforeLast(".")
        var artist = "未知艺术家" // 默认歌手名
        var duration = 180000L

        try {
            retriever.setDataSource(filePath)
            
            // 提取标题
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!metaTitle.isNullOrEmpty()) {
                title = metaTitle.trim()
            }

            // 提取歌手/艺术家
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!metaArtist.isNullOrEmpty()) {
                artist = metaArtist.trim()
            }

            // 提取时长
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationStr != null) {
                duration = durationStr.toLong()
            }
        } catch (e: Exception) {
            Log.e("MusicManager", "元数据提取失败: $filePath", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        
        return MetadataResult(title, artist, duration)
    }

    // 保存导入的音乐信息
    fun saveImportedMusic(context: Context, musicFiles: List<MusicFile>) {
        val importedData = musicFiles.filter { !it.isRawResource }.map {
            ImportedMusicData(
                fileName = it.fileName,
                filePath = it.filePath,
                fileSize = it.fileSize
            )
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val json = Gson().toJson(importedData)
        editor.putString(IMPORTED_MUSIC_KEY, json)
        editor.commit()
    }

    // 获取导入音乐数据
    fun getImportedMusicData(context: Context): List<ImportedMusicData> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(IMPORTED_MUSIC_KEY, "[]")

        return try {
            val type = object : TypeToken<List<ImportedMusicData>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e("MusicManager", "解析导入音乐数据失败", e)
            emptyList()
        }
    }

    // 导入音乐的数据类
    data class ImportedMusicData(
        val fileName: String,
        val filePath: String,
        val fileSize: Long
    )
}

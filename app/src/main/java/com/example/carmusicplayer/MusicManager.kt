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
        Log.d("MusicManager", "内置音乐: ${musicList.count { !it.isImported }}")
        Log.d("MusicManager", "导入音乐: ${musicList.count { it.isImported }}")

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

                val duration = getMusicDuration(imported.filePath)
                val title = imported.fileName.substringBeforeLast(".")

                importedMusic.add(
                    Music(
                        id = id++,
                        title = title,
                        artist = "本地导入",
                        duration = duration,
                        filePath = imported.filePath,
                        isImported = true
                    )
                )

                Log.d("MusicManager", "成功加载导入音乐: $title, 时长: ${duration}ms")

            } catch (e: Exception) {
                Log.e("MusicManager", "加载导入音乐失败: ${imported.fileName}", e)
            }
        }

        return importedMusic
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

        Log.d("MusicManager", "保存导入音乐数据: ${importedData.size} 条")
        importedData.forEachIndexed { index, data ->
            Log.d("MusicManager", "保存第${index + 1}条: ${data.fileName}, 路径: ${data.filePath}")
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val json = Gson().toJson(importedData)
        editor.putString(IMPORTED_MUSIC_KEY, json)
        val success = editor.commit() // 使用commit确保立即保存

        if (success) {
            Log.d("MusicManager", "保存成功到SharedPreferences")
        } else {
            Log.e("MusicManager", "保存失败到SharedPreferences")
        }
    }

    // 新增：获取导入音乐数据（用于ImportMusicActivity）
    fun getImportedMusicData(context: Context): List<ImportedMusicData> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(IMPORTED_MUSIC_KEY, "[]")

        return try {
            val type = object : TypeToken<List<ImportedMusicData>>() {}.type
            val importedList: List<ImportedMusicData> = Gson().fromJson(json, type)
            importedList
        } catch (e: Exception) {
            Log.e("MusicManager", "解析导入音乐数据失败", e)
            emptyList()
        }
    }

    // 获取音乐时长
    private fun getMusicDuration(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLong() ?: 180000 // 默认3分钟
        } catch (e: Exception) {
            Log.e("MusicManager", "获取音乐时长失败: $filePath", e)
            180000 // 默认3分钟
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e("MusicManager", "释放MediaMetadataRetriever失败", e)
            }
        }
    }

    // 导入音乐的数据类
    data class ImportedMusicData(
        val fileName: String,
        val filePath: String,
        val fileSize: Long
    )
}
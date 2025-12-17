package com.example.carmusicplayer

data class MusicFile(
    val id: Int,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val isRawResource: Boolean = false  // 这是自定义属性，不是依赖
) {
    fun getFormattedSize(): String {
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${String.format("%.1f", fileSize / 1024.0)} KB"
            else -> "${String.format("%.1f", fileSize / (1024 * 1024.0))} MB"
        }
    }
}
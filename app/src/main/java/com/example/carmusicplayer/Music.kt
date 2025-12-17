package com.example.carmusicplayer

data class Music(
    val id: Int,
    val title: String,
    val artist: String,
    val duration: Long, // 毫秒
    val resourceId: Int = 0, // raw资源ID，为0表示不是raw资源
    val filePath: String = "", // 文件路径，用于导入的音乐
    val isImported: Boolean = false // 是否为导入的音乐
) {
    fun getFormattedDuration(): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
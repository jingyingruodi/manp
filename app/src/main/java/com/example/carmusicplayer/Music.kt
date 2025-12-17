package com.example.carmusicplayer

data class Music(
    val id: Int,
    val title: String,
    val artist: String,
    val duration: Long, // 毫秒
    val resourceId: Int // raw资源ID
) {
    fun getFormattedDuration(): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
package com.example.carmusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicAdapter(
    private var musicList: List<Music>,
    private val onItemClick: (Music) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    private var selectedPosition = -1

    inner class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTrackNumber: TextView = itemView.findViewById(R.id.tvTrackNumber)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(musicList[position])
                    selectedPosition = position
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = musicList[position]

        holder.tvTrackNumber.text = (position + 1).toString()
        holder.tvTitle.text = music.title
        holder.tvArtist.text = music.artist
        holder.tvDuration.text = music.getFormattedDuration()

        // 高亮当前播放的歌曲
        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(0xFF2C2C2E.toInt())
            holder.tvTitle.setTextColor(0xFFFFFFFF.toInt())
        } else {
            holder.itemView.setBackgroundColor(0xFF1C1C1E.toInt())
            holder.tvTitle.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    override fun getItemCount(): Int = musicList.size

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    // 添加更新数据的方法
    fun updateData(newList: List<Music>) {
        musicList = newList
        notifyDataSetChanged()
    }
}
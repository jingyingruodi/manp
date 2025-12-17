package com.example.carmusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicFileAdapter(
    private var musicFiles: List<MusicFile>,
    private val onDeleteClick: (MusicFile) -> Unit
) : RecyclerView.Adapter<MusicFileAdapter.MusicFileViewHolder>() {

    inner class MusicFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_file, parent, false)
        return MusicFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicFileViewHolder, position: Int) {
        val musicFile = musicFiles[position]

        holder.tvFileName.text = musicFile.fileName
        holder.tvFileSize.text = musicFile.getFormattedSize()

        holder.btnDelete.setOnClickListener {
            onDeleteClick(musicFile)
        }
    }

    override fun getItemCount(): Int = musicFiles.size

    fun updateData(newList: List<MusicFile>) {
        musicFiles = newList
        notifyDataSetChanged()
    }
}
package com.rame.kameratv

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

/**
 * Grid of camera tiles. Each visible tile plays its RTSP stream live (muted).
 * Tapping / OK on a tile opens it fullscreen via [onClick].
 */
@UnstableApi
class CameraAdapter(
    private val ctx: Context,
    private var items: List<Camera>,
    private val accent: Int,
    private val onLongClick: (Int) -> Unit = {},
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<CameraAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val player: PlayerView = v.findViewById(R.id.tilePlayer)
        val name: TextView = v.findViewById(R.id.tileName)
        var exo: ExoPlayer? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_camera, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cam = items[position]
        holder.name.text = cam.name
        Ui.makeTileInteractive(holder.itemView, accent)
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onLongClick(pos)
            true
        }

        startPlayer(holder, cam)
    }

    private fun startPlayer(holder: VH, cam: Camera) {
        releasePlayer(holder)
        val exo = ExoPlayer.Builder(ctx).build()
        holder.player.player = exo
        holder.player.useController = false
        exo.volume = 0f
        exo.setMediaItem(MediaItem.fromUri(cam.url))
        exo.prepare()
        exo.playWhenReady = true
        holder.exo = exo
    }

    override fun onViewRecycled(holder: VH) {
        releasePlayer(holder)
    }

    private fun releasePlayer(holder: VH) {
        holder.exo?.release()
        holder.exo = null
        holder.player.player = null
    }

    fun update(newItems: List<Camera>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun releaseVisible(rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            val holder = rv.getChildViewHolder(rv.getChildAt(i)) as? VH ?: continue
            releasePlayer(holder)
        }
    }
}

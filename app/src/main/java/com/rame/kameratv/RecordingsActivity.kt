package com.rame.kameratv

/*
 * Moje Kamere - list and play recordings made on the TV.
 * Copyright (c) 2026 Izudin Ramic (Rame).
 */

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class RecordingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var empty: TextView
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private val accent by lazy { CameraStore.getAccentColor(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)
        container = findViewById(R.id.recContainer)
        empty = findViewById(R.id.recEmpty)
        playerView = findViewById(R.id.recPlayer)
        playerView.isFocusable = true
    }

    override fun onResume() {
        super.onResume()
        if (playerView.visibility != View.VISIBLE) refresh()
    }

    private fun refresh() {
        container.removeAllViews()
        val files = Recorder.list(this)
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        for (f in files) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLp.setMargins(0, 0, 0, 12)
            row.layoutParams = rowLp

            val label = Button(this)
            label.isAllCaps = false
            label.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            label.setTextColor(0xFFFFFFFF.toInt())
            label.background = Ui.buttonBg(accent)
            val mb = f.length() / (1024.0 * 1024.0)
            label.text = fmt.format(Date(f.lastModified())) +
                "   (" + String.format(Locale.US, "%.1f", mb) + " MB)"
            val labelLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            labelLp.setMargins(0, 0, 10, 0)
            label.layoutParams = labelLp
            label.isFocusable = true
            label.setOnClickListener { playFile(f) }
            row.addView(label)

            val del = Button(this)
            del.isAllCaps = false
            del.text = getString(R.string.delete)
            del.setTextColor(0xFFFFFFFF.toInt())
            del.background = Ui.buttonBg(accent)
            del.isFocusable = true
            del.setOnClickListener { confirmDelete(f) }
            row.addView(del)

            container.addView(row)
        }
    }

    private fun playFile(f: File) {
        releasePlayer()
        container.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        val exo = ExoPlayer.Builder(this).build()
        playerView.player = exo
        playerView.useController = true
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
        exo.prepare()
        exo.playWhenReady = true
        player = exo
        playerView.requestFocus()
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(f.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                f.delete()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun hidePlayer() {
        releasePlayer()
        playerView.visibility = View.GONE
        container.visibility = View.VISIBLE
        refresh()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playerView.player = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (playerView.visibility == View.VISIBLE) {
            hidePlayer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
}

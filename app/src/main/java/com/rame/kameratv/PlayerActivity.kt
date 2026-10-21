package com.rame.kameratv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Fullscreen view of a single camera. D-pad LEFT / RIGHT switches between cameras.
 */
@UnstableApi
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var title: TextView
    private var cameras: List<Camera> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.fullPlayer)
        title = findViewById(R.id.fullTitle)
        playerView.useController = false
        playerView.isFocusable = false

        cameras = CameraStore.load(this)
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (cameras.size - 1).coerceAtLeast(0))
    }

    override fun onStart() {
        super.onStart()
        if (cameras.isEmpty()) {
            finish()
            return
        }
        val exo = ExoPlayer.Builder(this).build()
        playerView.player = exo
        player = exo
        playAt(index)
    }

    private fun playAt(i: Int) {
        if (cameras.isEmpty()) return
        index = ((i % cameras.size) + cameras.size) % cameras.size
        val cam = cameras[index]
        title.text = cam.name
        player?.apply {
            setMediaItem(MediaItem.fromUri(cam.url))
            prepare()
            playWhenReady = true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (cameras.size > 1) { playAt(index + 1); return true }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (cameras.size > 1) { playAt(index - 1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        playerView.player = null
    }
}

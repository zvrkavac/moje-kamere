package com.rame.kameratv

/*
 * Moje Kamere - fullscreen camera view with PTZ, digital zoom and snapshot.
 * Copyright (c) 2026 Izudin Ramic (Rame).
 */

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var title: TextView
    private lateinit var bar: LinearLayout
    private lateinit var recIndicator: TextView
    private var recBtn: Button? = null
    private var cameras: List<Camera> = emptyList()
    private var index = 0
    private var zoom = 1.0f
    private var accent = 0xFF3B82F6.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        accent = CameraStore.getAccentColor(this)
        playerView = findViewById(R.id.fullPlayer)
        title = findViewById(R.id.fullTitle)
        bar = findViewById(R.id.controlsBar)
        recIndicator = findViewById(R.id.recIndicator)
        recIndicator.visibility = if (Recorder.isRecording()) View.VISIBLE else View.GONE
        playerView.useController = false
        playerView.isFocusable = false

        cameras = CameraStore.load(this)
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (cameras.size - 1).coerceAtLeast(0))

        buildControls()
    }

    private fun buildControls() {
        addBtn("Pret.") { switchCamera(-1) }
        addBtn("◀") { ptz(-0.6f, 0f) }   // left
        addBtn("▲") { ptz(0f, 0.6f) }    // up
        addBtn("▼") { ptz(0f, -0.6f) }   // down
        addBtn("▶") { ptz(0.6f, 0f) }    // right
        addBtn("Zoom +") { setZoom(zoom * 1.3f) }
        addBtn("Zoom -") { setZoom(zoom / 1.3f) }
        addBtn("Slika") { saveSnapshot() }
        recBtn = addBtn(if (Recorder.isRecording()) "Zaustavi" else "Snimaj") { toggleRecord() }
        addBtn("Sljed.") { switchCamera(1) }
    }

    private fun addBtn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 16f
        Ui.makeInteractive(b, accent)
        b.setPadding(28, 16, 28, 16)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(8, 4, 8, 4)
        b.layoutParams = lp
        b.isFocusable = true
        b.setOnClickListener { onClick() }
        bar.addView(b)
        return b
    }

    private fun toggleRecord() {
        if (cameras.isEmpty()) return
        if (Recorder.isRecording()) {
            val saved = Recorder.stop(this)
            recIndicator.visibility = View.GONE
            recBtn?.text = "Snimaj"
            toast(if (saved != null) "Snimak spremljen: $saved" else "Snimanje zaustavljeno")
        } else {
            val err = Recorder.start(this, cameras[index].url)
            if (err == null) {
                recIndicator.visibility = View.VISIBLE
                recBtn?.text = "Zaustavi"
                toast("Snimanje pokrenuto")
            } else {
                toast("Greska pri snimanju: $err")
            }
        }
    }

    private fun setZoom(z: Float) {
        zoom = z.coerceIn(1.0f, 4.0f)
        val tv = playerView.videoSurfaceView as? TextureView ?: return
        tv.pivotX = tv.width / 2f
        tv.pivotY = tv.height / 2f
        tv.scaleX = zoom
        tv.scaleY = zoom
    }

    private fun switchCamera(delta: Int) {
        if (cameras.size <= 1) return
        // recording is tied to the current camera's stream; stop it before switching
        if (Recorder.isRecording()) {
            val saved = Recorder.stop(this)
            recIndicator.visibility = View.GONE
            recBtn?.text = "Snimaj"
            if (saved != null) toast("Snimak spremljen: $saved")
        }
        zoom = 1.0f
        playAt(index + delta)
    }

    private fun ptz(panX: Float, tiltY: Float) {
        if (cameras.isEmpty()) return
        val cam = OnvifPtz.parse(cameras[index].url)
        if (cam == null) {
            toast("PTZ ne radi za ovu kameru")
            return
        }
        OnvifPtz.nudge(cam, panX, tiltY, 0f, 500L) { err ->
            if (err != null) runOnUiThread { toast("PTZ: $err") }
        }
    }

    private fun saveSnapshot() {
        val tv = playerView.videoSurfaceView as? TextureView
        val bmp = tv?.bitmap
        if (bmp == null) {
            toast("Slika trenutno nije dostupna")
            return
        }
        val name = "kamera_" + System.currentTimeMillis() + ".jpg"
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MojeKamere")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    toast("Slika spremljena u Galeriju")
                    return
                }
            }
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: cacheDir
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            toast("Slika spremljena")
        } catch (e: Exception) {
            toast("Greska pri slikanju: " + (e.message ?: ""))
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
        // reset digital zoom view for the new surface
        bar.post { setZoom(zoom) }
    }

    override fun onStop() {
        super.onStop()
        if (Recorder.isRecording()) {
            Recorder.stop(this)
        }
        player?.release()
        player = null
        playerView.player = null
    }
}

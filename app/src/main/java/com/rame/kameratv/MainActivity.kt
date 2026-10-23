package com.rame.kameratv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var empty: TextView
    private var adapter: CameraAdapter? = null
    private var cameras: List<Camera> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.camerasGrid)
        empty = findViewById(R.id.emptyLabel)
        grid.layoutManager = GridLayoutManager(this, CameraStore.getColumns(this))

        val accent = CameraStore.getAccentColor(this)

        styleHeaderButton(findViewById(R.id.settingsButton), accent) {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        styleHeaderButton(findViewById(R.id.scanButton), accent) {
            startActivity(Intent(this@MainActivity, ScanActivity::class.java))
        }
        styleHeaderButton(findViewById(R.id.qrButton), accent) {
            startActivity(Intent(this@MainActivity, QrPairActivity::class.java))
        }
        styleHeaderButton(findViewById(R.id.recordingsButton), accent) {
            startActivity(Intent(this@MainActivity, RecordingsActivity::class.java))
        }

        checkForUpdate()
    }

    /** Give a header button a focus/hover-aware background that changes color. */
    private fun styleHeaderButton(b: Button, accent: Int, onClick: () -> Unit) {
        b.isAllCaps = false
        b.setTextColor(0xFFFFFFFF.toInt())
        b.background = Ui.buttonBg(accent)
        b.setOnClickListener { onClick() }
    }

    private var updatePrompted = false

    private fun checkForUpdate() {
        val url = CameraStore.getUpdateUrl(this)
        if (url.isBlank()) return
        UpdateChecker.check(this, url) { info ->
            if (info == null) return@check
            runOnUiThread {
                if (isFinishing || updatePrompted) return@runOnUiThread
                updatePrompted = true
                val msg = getString(R.string.update_available, info.versionName) +
                    (if (info.notes.isNotBlank()) "\n\n" + info.notes else "")
                AlertDialog.Builder(this)
                    .setTitle(R.string.update_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.update_now) { _, _ ->
                        UpdateChecker.downloadAndInstall(this, info.apkUrl) { err ->
                            runOnUiThread {
                                android.widget.Toast.makeText(this, err, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton(R.string.later, null)
                    .show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        loadCameras()
    }

    private fun loadCameras() {
        (grid.layoutManager as? GridLayoutManager)?.spanCount = CameraStore.getColumns(this)
        cameras = CameraStore.load(this)
        empty.visibility = if (cameras.isEmpty()) View.VISIBLE else View.GONE

        val a = CameraAdapter(
            this,
            cameras,
            CameraStore.getAccentColor(this),
            onLongClick = { index -> showCameraMenu(index) }
        ) { index ->
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra(PlayerActivity.EXTRA_INDEX, index)
            startActivity(i)
        }
        adapter = a
        grid.adapter = a
    }

    /** Long-press (hold OK) on a tile: offer Edit / Delete. */
    private fun showCameraMenu(index: Int) {
        if (index < 0 || index >= cameras.size) return
        val cam = cameras[index]
        val options = arrayOf(getString(R.string.edit), getString(R.string.delete))
        AlertDialog.Builder(this)
            .setTitle(cam.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(cam)
                    1 -> confirmDelete(cam)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(cam: Camera) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_camera, null)
        val nameField = view.findViewById<EditText>(R.id.fieldName)
        val urlField = view.findViewById<EditText>(R.id.fieldUrl)
        nameField.setText(cam.name)
        urlField.setText(cam.url)
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_camera)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameField.text.toString().trim()
                val url = urlField.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(this, R.string.fill_both, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                CameraStore.update(this, cam.copy(name = name, url = url))
                loadCameras()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(cam: Camera) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_camera)
            .setMessage(cam.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                CameraStore.delete(this, cam.id)
                loadCameras()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        adapter?.releaseVisible(grid)
        grid.adapter = null
        adapter = null
    }
}

package com.rame.kameratv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
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
        grid.layoutManager = GridLayoutManager(this, 2)

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.scanButton).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<Button>(R.id.qrButton).setOnClickListener {
            startActivity(Intent(this, QrPairActivity::class.java))
        }

        checkForUpdate()
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
        cameras = CameraStore.load(this)
        empty.visibility = if (cameras.isEmpty()) View.VISIBLE else View.GONE

        val a = CameraAdapter(this, cameras) { index ->
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra(PlayerActivity.EXTRA_INDEX, index)
            startActivity(i)
        }
        adapter = a
        grid.adapter = a
    }

    override fun onStop() {
        super.onStop()
        adapter?.releaseVisible(grid)
        grid.adapter = null
        adapter = null
    }
}

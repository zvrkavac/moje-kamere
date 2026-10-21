package com.rame.kameratv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
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

package com.rame.kameratv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ScanActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var container: LinearLayout
    private lateinit var rescan: Button
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        status = findViewById(R.id.scanStatus)
        progress = findViewById(R.id.scanProgress)
        container = findViewById(R.id.scanContainer)
        rescan = findViewById(R.id.rescanButton)

        rescan.setOnClickListener { startScan() }
        startScan()
    }

    private fun startScan() {
        if (scanning) return
        scanning = true
        container.removeAllViews()
        progress.visibility = View.VISIBLE
        progress.progress = 0
        rescan.isEnabled = false
        status.text = getString(R.string.scanning)

        NetworkScanner.scan(cb = object : NetworkScanner.Callback {
            override fun onProgress(done: Int, total: Int) {
                runOnUiThread {
                    progress.max = total
                    progress.progress = done
                }
            }

            override fun onFound(ip: String) {
                runOnUiThread { addFoundRow(ip) }
            }

            override fun onComplete(found: List<String>) {
                runOnUiThread {
                    scanning = false
                    progress.visibility = View.GONE
                    rescan.isEnabled = true
                    status.text = if (found.isEmpty())
                        getString(R.string.scan_none)
                    else
                        getString(R.string.scan_found, found.size)
                }
            }
        })
    }

    private fun addFoundRow(ip: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_scan_result, container, false)
        row.findViewById<TextView>(R.id.resultIp).text = ip
        row.findViewById<Button>(R.id.resultAdd).setOnClickListener { showAddDialog(ip) }
        container.addView(row)
    }

    private fun showAddDialog(ip: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_camera, null)
        val nameField = view.findViewById<EditText>(R.id.fieldName)
        val urlField = view.findViewById<EditText>(R.id.fieldUrl)
        nameField.setText("Kamera $ip")
        urlField.setText("rtsp://user:pass@$ip:554/stream1")

        AlertDialog.Builder(this)
            .setTitle(R.string.add_camera)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameField.text.toString().trim()
                val url = urlField.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(this, R.string.fill_both, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                CameraStore.add(this, name, url)
                Toast.makeText(this, R.string.camera_added, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkScanner.cancel()
    }
}

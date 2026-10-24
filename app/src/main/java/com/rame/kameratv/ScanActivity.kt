package com.rame.kameratv

/*
 * Moje Kamere - scan the network for cameras of any manufacturer.
 * Uses ONVIF discovery + a port scan, and can auto-fetch the RTSP URL over ONVIF.
 * Copyright (c) 2026 Izudin Ramic (Rame).
 */

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
    private val rows = HashMap<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        status = findViewById(R.id.scanStatus)
        progress = findViewById(R.id.scanProgress)
        container = findViewById(R.id.scanContainer)
        rescan = findViewById(R.id.rescanButton)

        Ui.makeInteractive(rescan, CameraStore.getAccentColor(this))
        rescan.text = getString(R.string.rescan)
        rescan.setOnClickListener { startScan() }
        startScan()
    }

    private fun startScan() {
        if (scanning) return
        scanning = true
        rows.clear()
        container.removeAllViews()
        progress.visibility = View.VISIBLE
        progress.progress = 0
        rescan.isEnabled = false
        status.text = getString(R.string.scanning)

        NetworkScanner.scan(this, object : NetworkScanner.Callback {
            override fun onProgress(done: Int, total: Int) {
                runOnUiThread {
                    progress.max = total
                    progress.progress = done
                }
            }

            override fun onFound(dev: NetworkScanner.Device) {
                runOnUiThread { upsertRow(dev) }
            }

            override fun onComplete(found: List<NetworkScanner.Device>) {
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

    private fun upsertRow(dev: NetworkScanner.Device) {
        val existing = rows[dev.ip]
        val row = existing ?: LayoutInflater.from(this)
            .inflate(R.layout.item_scan_result, container, false).also {
                rows[dev.ip] = it
                container.addView(it)
            }
        val onvif = if (dev.onvifXAddr != null) "  •  ONVIF" else ""
        val label = if (!dev.label.isNullOrBlank()) "  •  ${dev.label}" else ""
        row.findViewById<TextView>(R.id.resultIp).text = dev.ip + onvif + label
        row.findViewById<Button>(R.id.resultAdd).apply {
            Ui.makeInteractive(this, CameraStore.getAccentColor(this@ScanActivity))
            text = getString(R.string.add)
            setOnClickListener { showAddDialog(dev) }
        }
    }

    private fun showAddDialog(dev: NetworkScanner.Device) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_scan_add, null)
        val nameField = view.findViewById<EditText>(R.id.scanName)
        val userField = view.findViewById<EditText>(R.id.scanUser)
        val passField = view.findViewById<EditText>(R.id.scanPass)
        val urlField = view.findViewById<EditText>(R.id.scanUrl)
        nameField.setText(dev.label?.takeIf { it.isNotBlank() } ?: "Kamera ${dev.ip}")

        AlertDialog.Builder(this)
            .setTitle(R.string.add_camera)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameField.text.toString().trim().ifEmpty { "Kamera ${dev.ip}" }
                val user = userField.text.toString().trim()
                val pass = passField.text.toString().trim()
                val typedUrl = urlField.text.toString().trim()

                when {
                    typedUrl.isNotEmpty() -> {
                        CameraStore.add(this, name, typedUrl)
                        Toast.makeText(this, R.string.camera_added, Toast.LENGTH_SHORT).show()
                    }
                    dev.onvifXAddr != null -> {
                        Toast.makeText(this, R.string.fetching_onvif, Toast.LENGTH_SHORT).show()
                        OnvifPtz.fetchStreamUri(dev.onvifXAddr!!, user, pass) { rtsp, _ ->
                            runOnUiThread {
                                if (rtsp != null) {
                                    CameraStore.add(this, name, rtsp)
                                    Toast.makeText(this, R.string.camera_added, Toast.LENGTH_SHORT).show()
                                } else {
                                    CameraStore.add(this, name, fallbackUrl(dev, user, pass))
                                    Toast.makeText(this, R.string.onvif_failed, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    else -> {
                        CameraStore.add(this, name, fallbackUrl(dev, user, pass))
                        Toast.makeText(this, R.string.camera_added, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Best-guess RTSP URL. XM/XVV/XMEye cameras use a very different path format. */
    private fun fallbackUrl(dev: NetworkScanner.Device, user: String, pass: String): String {
        val ip = dev.ip
        val isXm = dev.ports.contains(34567) ||
            (dev.label?.lowercase()?.let { it.contains("xm") || it.contains("xvv") } == true)
        return if (isXm) {
            "rtsp://$ip:554/user=$user&password=$pass&channel=1&stream=0.sdp?real_stream"
        } else {
            val creds = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://$creds$ip:554/stream1"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkScanner.cancel()
    }
}

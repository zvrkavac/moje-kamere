package com.rame.kameratv

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Manage the camera list: add, edit, delete. Usable with a TV remote.
 * Also shows the app version and lets the user check for an update.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var updateStatus: TextView
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        container = findViewById(R.id.camerasContainer)

        findViewById<Button>(R.id.addButton).setOnClickListener {
            showEditDialog(null)
        }

        // Version + update section
        val versionText = findViewById<TextView>(R.id.versionText)
        updateStatus = findViewById(R.id.updateStatus)
        versionText.text = getString(R.string.version_label, versionName(), versionCode())
        updateStatus.text = ""

        findViewById<Button>(R.id.checkUpdateButton).setOnClickListener {
            checkUpdate()
        }

        buildThemeControls()
    }

    private fun buildThemeControls() {
        val accent = CameraStore.getAccentColor(this)

        val colsRow = findViewById<LinearLayout>(R.id.colsRow)
        colsRow.removeAllViews()
        val currentCols = CameraStore.getColumns(this)
        for (c in 1..4) {
            val b = Button(this)
            b.text = c.toString()
            b.isAllCaps = false
            b.setTextColor(0xFFFFFFFF.toInt())
            b.backgroundTintList = ColorStateList.valueOf(if (c == currentCols) accent else 0xFF3A3F4A.toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 12, 0)
            b.layoutParams = lp
            b.setOnClickListener {
                CameraStore.setColumns(this, c)
                recreate()
            }
            colsRow.addView(b)
        }

        val colorRow = findViewById<LinearLayout>(R.id.colorRow)
        colorRow.removeAllViews()
        val colors = listOf(
            "Plava" to "#3B82F6",
            "Zelena" to "#22C55E",
            "Crvena" to "#EF4444",
            "Naranca" to "#F59E0B",
            "Ljubicasta" to "#A855F7"
        )
        for ((name, hex) in colors) {
            val b = Button(this)
            b.text = name
            b.isAllCaps = false
            b.setTextColor(0xFFFFFFFF.toInt())
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 10, 0)
            b.layoutParams = lp
            b.setOnClickListener {
                CameraStore.setAccentHex(this, hex)
                recreate()
            }
            colorRow.addView(b)
        }

        findViewById<TextView>(R.id.copyright).text = "© 2026 Izudin Ramic (Rame)"
    }

    private fun versionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    private fun versionCode(): Long = UpdateChecker.currentVersionCode(this)

    private fun checkUpdate() {
        if (checking) return
        checking = true
        updateStatus.text = getString(R.string.update_checking)
        UpdateChecker.check(this, CameraStore.getUpdateUrl(this)) { info ->
            runOnUiThread {
                checking = false
                if (info == null) {
                    updateStatus.text = getString(R.string.update_up_to_date)
                } else {
                    updateStatus.text = getString(R.string.update_found, info.versionName)
                    AlertDialog.Builder(this)
                        .setTitle(R.string.update_title)
                        .setMessage(getString(R.string.update_available, info.versionName))
                        .setPositiveButton(R.string.update_now) { _, _ ->
                            UpdateChecker.downloadAndInstall(this, info.apkUrl) { err ->
                                runOnUiThread {
                                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .setNegativeButton(R.string.later, null)
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        container.removeAllViews()
        val cameras = CameraStore.load(this)
        if (cameras.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.no_cameras)
            tv.setPadding(16, 24, 16, 24)
            container.addView(tv)
            return
        }
        val inflater = LayoutInflater.from(this)
        for (cam in cameras) {
            val row = inflater.inflate(R.layout.item_settings_camera, container, false)
            row.findViewById<TextView>(R.id.rowName).text = cam.name
            row.findViewById<TextView>(R.id.rowUrl).text = cam.url
            row.findViewById<Button>(R.id.rowEdit).setOnClickListener { showEditDialog(cam) }
            row.findViewById<Button>(R.id.rowDelete).setOnClickListener { confirmDelete(cam) }
            container.addView(row)
        }
    }

    private fun showEditDialog(existing: Camera?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_camera, null)
        val nameField = view.findViewById<EditText>(R.id.fieldName)
        val urlField = view.findViewById<EditText>(R.id.fieldUrl)
        if (existing != null) {
            nameField.setText(existing.name)
            urlField.setText(existing.url)
        } else {
            urlField.setText("rtsp://user:pass@192.168.0.XXX:554/stream1")
        }

        val titleRes = if (existing == null) R.string.add_camera else R.string.edit_camera
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameField.text.toString().trim()
                val url = urlField.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(this, R.string.fill_both, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing == null) {
                    CameraStore.add(this, name, url)
                } else {
                    CameraStore.update(this, existing.copy(name = name, url = url))
                }
                refresh()
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
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

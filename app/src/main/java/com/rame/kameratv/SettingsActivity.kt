package com.rame.kameratv

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
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        container = findViewById(R.id.camerasContainer)

        findViewById<Button>(R.id.addButton).setOnClickListener {
            showEditDialog(null)
        }

        val urlField = findViewById<EditText>(R.id.updateUrlField)
        urlField.setText(CameraStore.getUpdateUrl(this))
        findViewById<Button>(R.id.saveUrlButton).setOnClickListener {
            CameraStore.setUpdateUrl(this, urlField.text.toString())
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
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

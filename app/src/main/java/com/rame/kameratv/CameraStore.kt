package com.rame.kameratv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object CameraStore {

    private const val PREFS = "kameratv_prefs"
    private const val KEY = "cameras"
    private const val KEY_UPDATE_URL = "update_url"

    fun getUpdateUrl(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UPDATE_URL, "") ?: ""
    }

    fun setUpdateUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_UPDATE_URL, url.trim()).apply()
    }

    fun load(ctx: Context): MutableList<Camera> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        if (raw.isNullOrEmpty()) {
            val defaults = defaultCameras()
            save(ctx, defaults)
            return defaults
        }
        val list = mutableListOf<Camera>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Camera(o.getString("id"), o.getString("name"), o.getString("url")))
            }
        } catch (e: Exception) {
            return defaultCameras()
        }
        return list
    }

    fun save(ctx: Context, cams: List<Camera>) {
        val arr = JSONArray()
        for (c in cams) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("name", c.name)
            o.put("url", c.url)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun add(ctx: Context, name: String, url: String) {
        val list = load(ctx)
        list.add(Camera(UUID.randomUUID().toString(), name, url))
        save(ctx, list)
    }

    fun update(ctx: Context, cam: Camera) {
        val list = load(ctx)
        val idx = list.indexOfFirst { it.id == cam.id }
        if (idx >= 0) {
            list[idx] = cam
            save(ctx, list)
        }
    }

    fun delete(ctx: Context, id: String) {
        val list = load(ctx)
        list.removeAll { it.id == id }
        save(ctx, list)
    }

    private fun defaultCameras(): MutableList<Camera> = mutableListOf(
        Camera(UUID.randomUUID().toString(), "Kuca kamera", "rtsp://USER:PASS@192.168.0.103:554/stream1"),
        Camera(UUID.randomUUID().toString(), "Kuca kamera Ulaz", "rtsp://USER:PASS@192.168.0.207:554/stream1")
    )
}

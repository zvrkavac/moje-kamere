package com.rame.kameratv

/*
 * Moje Kamere - Android TV camera viewer.
 * Copyright (c) 2026 Izudin Ramic (Rame). All rights reserved.
 */

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists the camera list and app preferences in SharedPreferences.
 * (c) 2026 Izudin Ramic.
 */
object CameraStore {

    private const val PREFS = "kameratv_prefs"
    private const val KEY = "cameras"
    private const val KEY_UPDATE_URL = "update_url"
    private const val KEY_COLUMNS = "grid_columns"
    private const val KEY_ACCENT = "accent_color"
    private const val DEFAULT_UPDATE_URL =
        "https://github.com/zvrkavac/moje-kamere/releases/latest/download/version.json"
    const val DEFAULT_ACCENT = "#3B82F6"

    fun getUpdateUrl(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL
    }

    fun setUpdateUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_UPDATE_URL, url.trim()).apply()
    }

    /** Grid columns on the home screen (1..4). */
    fun getColumns(ctx: Context): Int {
        val c = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_COLUMNS, 2)
        return c.coerceIn(1, 4)
    }

    fun setColumns(ctx: Context, cols: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COLUMNS, cols.coerceIn(1, 4)).apply()
    }

    /** Accent color as hex string, e.g. "#3B82F6". */
    fun getAccentHex(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCENT, DEFAULT_ACCENT) ?: DEFAULT_ACCENT
    }

    fun getAccentColor(ctx: Context): Int {
        return try {
            Color.parseColor(getAccentHex(ctx))
        } catch (_: Exception) {
            Color.parseColor(DEFAULT_ACCENT)
        }
    }

    fun setAccentHex(ctx: Context, hex: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, hex).apply()
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
        // Lozinka se NE drži ovdje. Upiši pravi RTSP URL (sa korisnikom i lozinkom)
        // u aplikaciji: Postavke -> Uredi.
        Camera(UUID.randomUUID().toString(), "Kuca kamera", "rtsp://USER:PASS@192.168.0.103:554/stream1"),
        Camera(UUID.randomUUID().toString(), "Kuca kamera Ulaz", "rtsp://USER:PASS@192.168.0.207:554/stream1")
    )
}

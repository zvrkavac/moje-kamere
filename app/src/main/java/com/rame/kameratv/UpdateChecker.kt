package com.rame.kameratv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    data class Info(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String)

    fun currentVersionCode(ctx: Context): Long {
        return try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                pi.longVersionCode
            else
                @Suppress("DEPRECATION") pi.versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    fun check(ctx: Context, url: String, callback: (Info?) -> Unit) {
        if (url.isBlank()) {
            callback(null); return
        }
        Thread {
            var result: Info? = null
            try {
                val text = httpGet(url)
                val o = JSONObject(text)
                val vc = o.getLong("versionCode")
                if (vc > currentVersionCode(ctx)) {
                    result = Info(
                        vc,
                        o.optString("versionName", ""),
                        o.optString("apkUrl", ""),
                        o.optString("notes", "")
                    )
                }
            } catch (_: Exception) {
            }
            callback(result)
        }.start()
    }

    fun downloadAndInstall(ctx: Context, apkUrl: String, onError: (String) -> Unit) {
        Thread {
            try {
                val dir = ctx.getExternalFilesDir("update") ?: ctx.cacheDir
                val apk = File(dir, "moje-kamere-update.apk")
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true
                conn.inputStream.use { input ->
                    apk.outputStream().use { out -> input.copyTo(out) }
                }
                conn.disconnect()

                val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    ctx.startActivity(intent)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Greska pri preuzimanju")
            }
        }.start()
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.instanceFollowRedirects = true
        conn.inputStream.use { return it.bufferedReader().readText() }
    }
}

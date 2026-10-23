package com.rame.kameratv

/*
 * Moje Kamere - records a camera's RTSP stream to a file using libVLC.
 * Media3 ExoPlayer cannot record, so libVLC opens a separate connection
 * to the camera purely for recording, while the live view keeps playing.
 * Copyright (c) 2026 Izudin Ramic (Rame).
 */

import android.content.Context
import android.net.Uri
import android.os.Environment
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object Recorder {

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var recording = false
    private var currentDir: File? = null
    private var before: Set<String> = emptySet()

    fun isRecording(): Boolean = recording

    /** Folder where recordings are stored (app-specific external dir, no permission needed). */
    fun dir(ctx: Context): File {
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.cacheDir
        val d = File(base, "MojeKamere")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** List saved recordings, newest first. */
    fun list(ctx: Context): List<File> {
        val files = dir(ctx).listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    /** Start recording the given RTSP url. Returns null on success or an error message. */
    fun start(ctx: Context, url: String): String? {
        if (recording) return "Vec snima"
        return try {
            val options = ArrayList<String>()
            options.add("--rtsp-tcp")
            options.add("--no-audio-time-stretch")
            val vlc = LibVLC(ctx, options)
            val media = Media(vlc, Uri.parse(url))
            media.addOption(":network-caching=400")
            media.addOption(":rtsp-tcp")
            val mp = MediaPlayer(vlc)
            mp.media = media
            media.release()

            val d = dir(ctx)
            currentDir = d
            before = (d.listFiles()?.map { it.name } ?: emptyList()).toSet()

            val startedRecord = AtomicBoolean(false)
            mp.setEventListener { ev ->
                if (ev.type == MediaPlayer.Event.Playing && startedRecord.compareAndSet(false, true)) {
                    try {
                        mp.record(d.absolutePath)
                    } catch (_: Throwable) {
                    }
                }
            }
            mp.play()

            libVlc = vlc
            player = mp
            recording = true
            null
        } catch (e: Throwable) {
            cleanup()
            e.message ?: "Greska pri snimanju"
        }
    }

    /** Stop recording. Returns the saved file name (if detected), or null. */
    fun stop(ctx: Context): String? {
        val mp = player
        try {
            mp?.record(null)
            Thread.sleep(300)
            mp?.stop()
        } catch (_: Throwable) {
        }
        cleanup()

        val d = currentDir ?: dir(ctx)
        val saved = d.listFiles()
            ?.filter { it.isFile && it.name !in before }
            ?.maxByOrNull { it.lastModified() }
            ?.name
        currentDir = null
        before = emptySet()
        return saved
    }

    private fun cleanup() {
        try {
            player?.setEventListener(null)
        } catch (_: Throwable) {
        }
        try {
            player?.release()
        } catch (_: Throwable) {
        }
        try {
            libVlc?.release()
        } catch (_: Throwable) {
        }
        player = null
        libVlc = null
        recording = false
    }
}

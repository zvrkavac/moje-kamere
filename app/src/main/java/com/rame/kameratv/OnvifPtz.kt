package com.rame.kameratv

/*
 * Moje Kamere - ONVIF PTZ control (pan/tilt for cameras like Tapo C500).
 * Copyright (c) 2026 Izudin Ramic (Rame).
 */

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Minimal ONVIF PTZ client: discovers the camera's Media/PTZ services,
 * gets a profile token, and sends ContinuousMove + Stop ("nudge" style).
 *
 * Works with ONVIF cameras that have pan/tilt (e.g. Tapo C500 with ONVIF enabled).
 */
object OnvifPtz {

    data class Cam(val ip: String, val user: String, val pass: String)

    // ip -> Pair(ptzXAddr, profileToken)
    private val cache = HashMap<String, Pair<String, String>>()

    /** Parse rtsp://user:pass@ip:port/path into a Cam. */
    fun parse(rtsp: String): Cam? {
        return try {
            val noScheme = rtsp.substringAfter("://")
            if (!noScheme.contains("@")) return null
            val creds = noScheme.substringBefore("@")
            val hostPart = noScheme.substringAfter("@").substringBefore("/")
            val ip = hostPart.substringBefore(":")
            val user = creds.substringBefore(":")
            val pass = if (creds.contains(":")) creds.substringAfter(":") else ""
            if (ip.isEmpty()) null else Cam(ip, user, pass)
        } catch (_: Exception) {
            null
        }
    }

    /** Move a short "nudge" then stop. Call on any thread; network runs on a worker thread. */
    fun nudge(cam: Cam, panX: Float, tiltY: Float, zoom: Float, durationMs: Long, onResult: (String?) -> Unit) {
        Thread {
            try {
                val (ptzXAddr, token) = resolve(cam)
                continuousMove(ptzXAddr, token, cam, panX, tiltY, zoom)
                Thread.sleep(durationMs)
                stop(ptzXAddr, token, cam)
                onResult(null)
            } catch (e: Exception) {
                onResult(e.message ?: "PTZ greska")
            }
        }.start()
    }

    private fun resolve(cam: Cam): Pair<String, String> {
        cache[cam.ip]?.let { return it }
        val (mediaXAddr, ptzXAddr) = getCapabilities(cam)
        val token = getProfileToken(mediaXAddr, cam)
        val result = Pair(ptzXAddr, token)
        cache[cam.ip] = result
        return result
    }

    private fun getCapabilities(cam: Cam): Pair<String, String> {
        val device = "http://${cam.ip}:2020/onvif/device_service"
        val body = soap(
            cam,
            "<tds:GetCapabilities xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">" +
                "<tds:Category>All</tds:Category></tds:GetCapabilities>"
        )
        val resp = post(device, body)
        // Collect all XAddr values; pick the one that looks like media / ptz.
        val xaddrs = Regex("<[^>]*XAddr>([^<]+)</[^>]*XAddr>").findAll(resp).map { it.groupValues[1] }.toList()
        var media = xaddrs.firstOrNull { it.contains("media", true) }
        var ptz = xaddrs.firstOrNull { it.contains("ptz", true) }
        // Tapo often serves everything from /onvif/service
        val fallback = xaddrs.firstOrNull { it.contains("/onvif/service") }
            ?: "http://${cam.ip}:2020/onvif/service"
        if (media == null) media = fallback
        if (ptz == null) ptz = fallback
        return Pair(media, ptz)
    }

    private fun getProfileToken(mediaXAddr: String, cam: Cam): String {
        val body = soap(
            cam,
            "<trt:GetProfiles xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"/>"
        )
        val resp = post(mediaXAddr, body)
        val m = Regex("token=\"([^\"]+)\"").find(resp)
            ?: Regex("<[^>]*Profiles[^>]*token=\"([^\"]+)\"").find(resp)
        return m?.groupValues?.get(1) ?: throw Exception("Nema ONVIF profila (ukljuci ONVIF u Tapo aplikaciji)")
    }

    private fun continuousMove(ptzXAddr: String, token: String, cam: Cam, x: Float, y: Float, z: Float) {
        val body = soap(
            cam,
            "<tptz:ContinuousMove xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\" " +
                "xmlns:tt=\"http://www.onvif.org/ver10/schema\">" +
                "<tptz:ProfileToken>$token</tptz:ProfileToken>" +
                "<tptz:Velocity>" +
                "<tt:PanTilt x=\"$x\" y=\"$y\"/>" +
                "<tt:Zoom x=\"$z\"/>" +
                "</tptz:Velocity></tptz:ContinuousMove>"
        )
        post(ptzXAddr, body)
    }

    private fun stop(ptzXAddr: String, token: String, cam: Cam) {
        val body = soap(
            cam,
            "<tptz:Stop xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">" +
                "<tptz:ProfileToken>$token</tptz:ProfileToken>" +
                "<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom></tptz:Stop>"
        )
        post(ptzXAddr, body)
    }

    private fun post(urlStr: String, body: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return text
    }

    /** Build a SOAP 1.2 envelope with a WS-Security UsernameToken (digest). */
    private fun soap(cam: Cam, bodyInner: String): String {
        val nonceBytes = ByteArray(16)
        SecureRandom().nextBytes(nonceBytes)
        val nonceB64 = Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val created = sdf.format(Date())
        val digestInput = nonceBytes + created.toByteArray(Charsets.UTF_8) + cam.pass.toByteArray(Charsets.UTF_8)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(digestInput)
        val passDigest = Base64.encodeToString(sha1, Base64.NO_WRAP)
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<s:Header><Security s:mustUnderstand=\"1\" " +
            "xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">" +
            "<UsernameToken><Username>${cam.user}</Username>" +
            "<Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">$passDigest</Password>" +
            "<Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">$nonceB64</Nonce>" +
            "<Created xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">$created</Created>" +
            "</UsernameToken></Security></s:Header>" +
            "<s:Body>$bodyInner</s:Body></s:Envelope>"
    }
}

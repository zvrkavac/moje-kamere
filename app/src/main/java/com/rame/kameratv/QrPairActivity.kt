package com.rame.kameratv

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD

class QrPairActivity : AppCompatActivity() {

    private var server: PairServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_pair)

        val ip = NetworkScanner.localIp()
        val urlView = findViewById<TextView>(R.id.qrUrl)
        val qrImage = findViewById<ImageView>(R.id.qrImage)
        val status = findViewById<TextView>(R.id.qrStatus)

        if (ip == null) {
            urlView.text = getString(R.string.qr_no_network)
            return
        }

        var port = 8766
        var started = false
        for (p in intArrayOf(8766, 8777, 8080, 8888)) {
            try {
                server = PairServer(p)
                server!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                port = p
                started = true
                break
            } catch (_: Exception) {
                server = null
            }
        }

        if (!started) {
            urlView.text = getString(R.string.qr_server_fail)
            return
        }

        val url = "http://$ip:$port/"
        urlView.text = url
        status.text = getString(R.string.qr_instructions)
        try {
            qrImage.setImageBitmap(makeQr(url, 600))
        } catch (_: Exception) {
        }
    }

    private fun makeQr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    override fun onDestroy() {
        super.onDestroy()
        try { server?.stop() } catch (_: Exception) {}
        server = null
    }

    inner class PairServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            return try {
                if (session.uri == "/add") {
                    val p = session.parameters
                    val name = p["name"]?.firstOrNull()?.trim().orEmpty()
                    val ip = p["ip"]?.firstOrNull()?.trim().orEmpty()
                    val user = p["user"]?.firstOrNull()?.trim().orEmpty()
                    val pass = p["pass"]?.firstOrNull()?.trim().orEmpty()
                    var port = p["port"]?.firstOrNull()?.trim().orEmpty()
                    var path = p["path"]?.firstOrNull()?.trim().orEmpty()
                    if (port.isEmpty()) port = "554"
                    if (path.isEmpty()) path = "/stream1"
                    if (!path.startsWith("/")) path = "/$path"

                    if (ip.isEmpty()) {
                        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", formPage("IP adresa je obavezna."))
                    }
                    val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
                    val url = "rtsp://$auth$ip:$port$path"
                    val finalName = if (name.isNotEmpty()) name else "Kamera $ip"
                    runOnUiThread {
                        CameraStore.add(this@QrPairActivity, finalName, url)
                        Toast.makeText(this@QrPairActivity, getString(R.string.qr_added, finalName), Toast.LENGTH_LONG).show()
                    }
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", successPage(finalName))
                } else {
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", formPage(null))
                }
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.message)
            }
        }
    }

    private fun formPage(error: String?): String {
        val err = if (error != null) "<p class='err'>" + esc(error) + "</p>" else ""
        return """
<!doctype html><html lang="bs"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dodaj kameru</title>
<style>
  body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0E0F13;color:#fff;margin:0;padding:22px}
  .card{max-width:420px;margin:0 auto}
  h1{font-size:20px;margin:0 0 4px}
  p.sub{color:#9AA0AA;margin:0 0 18px;font-size:14px}
  label{display:block;margin:14px 0 6px;font-size:14px;color:#cfd4dc}
  input{width:100%;box-sizing:border-box;padding:13px;border-radius:10px;border:1px solid #2A2E38;background:#1C1F27;color:#fff;font-size:16px}
  button{width:100%;margin-top:22px;padding:15px;border:0;border-radius:10px;background:#3B82F6;color:#fff;font-size:17px;font-weight:700}
  .err{background:#3a1414;border:1px solid #7f1d1d;color:#fecaca;padding:10px;border-radius:8px;font-size:14px}
  .row{display:flex;gap:10px}.row>div{flex:1}
  small{color:#9AA0AA}
</style></head><body><div class="card">
<h1>Dodaj kameru</h1>
<p class="sub">Unesi podatke kamere - spremit ce se na TV.</p>
$err
<form action="/add" method="get">
  <label>Ime kamere</label>
  <input name="name" placeholder="npr. Dvoriste" autocomplete="off">
  <label>IP adresa kamere</label>
  <input name="ip" placeholder="192.168.0.103" inputmode="decimal" autocomplete="off" required>
  <label>Korisnik</label>
  <input name="user" placeholder="npr. admin" autocomplete="off">
  <label>Lozinka</label>
  <input name="pass" type="text" placeholder="lozinka kamere" autocomplete="off">
  <div class="row">
    <div><label>Port</label><input name="port" value="554" inputmode="numeric"></div>
    <div><label>Putanja</label><input name="path" value="/stream1"></div>
  </div>
  <button type="submit">Dodaj kameru na TV</button>
  <p><small>Format: rtsp://korisnik:lozinka@IP:port/putanja</small></p>
</form></div></body></html>
""".trimIndent()
    }

    private fun successPage(name: String): String {
        return """
<!doctype html><html lang="bs"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dodano</title>
<style>body{font-family:system-ui,sans-serif;background:#0E0F13;color:#fff;margin:0;padding:30px;text-align:center}
.ok{font-size:54px}h1{font-size:22px}a{display:inline-block;margin-top:20px;color:#3B82F6}</style>
</head><body>
<div class="ok">&#10003;</div>
<h1>Kamera "${esc(name)}" je dodana na TV!</h1>
<p>Mozes dodati jos jednu ili zatvoriti.</p>
<a href="/">+ Dodaj jos jednu kameru</a>
</body></html>
""".trimIndent()
    }
}

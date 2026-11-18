package com.coral.printagent.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the local print bridge after the device boots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> runCatching { BridgeService.start(context) }
                .onFailure { Log.e("BootReceiver", "Could not start bridge: ${it.message}", it) }
        }
    }
}

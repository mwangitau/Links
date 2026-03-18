package com.githow.links.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.githow.links.service.SmsForegroundService

/**
 * BootReceiver
 *
 * Listens for BOOT_COMPLETED and QUICKBOOT_POWERON (HTC/some Chinese ROMs).
 * When the phone restarts, this fires and starts the foreground service
 * so LINKS is active again without the user needing to open the app.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LINKS_BOOT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Boot event received: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "✅ Phone booted — starting LINKS foreground service")
            SmsForegroundService.start(context)
        }
    }
}
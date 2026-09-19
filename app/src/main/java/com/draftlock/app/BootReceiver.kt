package com.draftlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot WorkManager re-enqueues persisted periodic work on its own,
 * but blocking state + widget + day rollover need an IMMEDIATE truth-sync —
 * otherwise the vault stays in its pre-reboot state until the next 15-min
 * window (or until the user opens the app). This receiver triggers that.
 *
 * Note: GoogleDocsMonitorScheduler.start is idempotent and the worker itself
 * early-outs when the monitor is disabled, so unconditionally rescheduling
 * here is safe and avoids a blocking DataStore read in a receiver.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        try { VaultRefreshScheduler.ensure(context) } catch (_: Exception) {}
        try { GoogleDocsMonitorScheduler.start(context) } catch (_: Exception) {}
        // One-shot immediate sync (periodic minimum is 15 min, so without
        // this the first post-boot refresh would lag).
        try {
            androidx.work.WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<VaultRefreshWorker>().build()
            )
        } catch (_: Exception) {}
    }
}

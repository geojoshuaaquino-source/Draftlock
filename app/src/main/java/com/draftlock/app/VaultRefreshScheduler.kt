package com.draftlock.app

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules [VaultRefreshWorker]: local 15-min truth-sync so usage,
 * blocking, stats and widget stay fresh with the app closed.
 * Minimum interval is 15 min (Android restriction) — same as the Docs monitor.
 * Call [ensure] from app start (idempotent, UPDATE policy).
 */
object VaultRefreshScheduler {
    private const val WORK_NAME = "draftlock_vault_refresh"

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<VaultRefreshWorker>(
            15, TimeUnit.MINUTES
        ).build()
        try {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // don't reset the 15-min clock on every launch
                request
            )
        } catch (_: Exception) { /* WorkManager not ready yet — next launch retries */ }
    }

    fun stop(context: Context) {
        try { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) } catch (_: Exception) {}
    }
}

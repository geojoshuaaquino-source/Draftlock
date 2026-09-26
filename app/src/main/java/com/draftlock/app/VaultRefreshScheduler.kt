package com.draftlock.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the headless blocking truth-sync.
 *
 * WorkManager is deliberately retained as a persistent background safety net.
 * The AccessibilityService also reconciles state, so a delayed WorkManager run
 * cannot by itself leave blocking disabled indefinitely.
 */
object VaultRefreshScheduler {
    private const val WORK_NAME = "draftlock_vault_refresh"

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<VaultRefreshWorker>(
            15, TimeUnit.MINUTES
        )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .addTag(WORK_NAME)
            .build()

        try {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            android.util.Log.e("DraftLock", "Could not schedule vault refresh", e)
        }
    }

    fun stop(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            android.util.Log.e("DraftLock", "Could not stop vault refresh", e)
        }
    }
}

package com.draftlock.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Headless truth-sync for DraftLock.
 *
 * WorkManager is one enforcement path, not the only one: the AccessibilityService
 * independently rechecks the same BlockingCoordinator. This worker therefore focuses
 * on persistent day rollover, strong DevicePolicyManager suspension, and widgets.
 */
class VaultRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val decision = BlockingCoordinator(ctx).reconcile()

            // Widget updates are non-critical. A widget failure must not invalidate
            // the successful blocking reconciliation.
            try {
                DraftLockWidget.updateAll(ctx)
            } catch (e: Exception) {
                android.util.Log.w("DraftLock", "Widget refresh failed", e)
            }

            android.util.Log.d(
                "DraftLock",
                "Vault refresh: dayKey=${decision.dayKey}, " +
                    "unlock=${decision.shouldUnlock}, usageAccess=${decision.usageAccess}"
            )
            Result.success()
        } catch (e: Exception) {
            // Critical local failures must be retried instead of being reported as
            // success.
            android.util.Log.e("DraftLock", "Vault refresh failed; WorkManager will retry", e)
            Result.retry()
        }
    }
}

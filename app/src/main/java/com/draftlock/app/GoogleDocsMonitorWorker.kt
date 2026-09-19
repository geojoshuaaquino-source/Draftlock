package com.draftlock.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reconciles Google Docs writing progress independently of the DraftLock UI.
 *
 * Runs ~every 15 minutes (Android controls exact timing). Uses
 * [GoogleDocsMonitorSync] so each run is 1 Drive-list + only changed docs,
 * avoiding the old N-Docs-gets-per-run slowness and 429 rate limits.
 */
class GoogleDocsMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val store = SettingsStore(appContext)
    private val docsRepo = GoogleDocsRepository()

    override suspend fun doWork(): Result {
        if (!store.monitorEnabled.first()) return Result.success()

        val prefix = store.monitorPrefix.first().trim()
        val countsStr = store.monitorCounts.first()
        val metaStr = store.monitorMeta.first()
        val manager = GoogleOAuthManager(applicationContext)

        val token = getFreshToken(manager)
            ?: return Result.success() // auth dead -> don't tight-retry; UI explains reconnect

        return try {
            val result = GoogleDocsMonitorSync.sync(
                token = token,
                prefix = prefix,
                countsJsonStr = countsStr,
                metaJsonStr = metaStr,
                docsRepo = docsRepo
            )

            val resetMinutes = store.resetMinutes.first()
            val dayKey = UsageTracker.periodStartMillis(resetMinutes).toString()
            val storedKey = store.monitorDayKey.first()
            val currentWords = if (storedKey == dayKey) store.monitorWords.first() else 0

            store.setMonitorWords(currentWords + result.added, dayKey)
            store.setMonitorCounts(result.countsJson)
            store.setMonitorMeta(result.metaJson)
            // Keep stats history in sync (typed + same-day monitor words).
            try { store.snapshotDay() } catch (_: Exception) {}
            // New words may have completed the quota while the app is closed —
            // re-apply suspension immediately instead of waiting for next open.
            try { reapplyBlocking(dayKey) } catch (_: Exception) {}
            DraftLockWidget.updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            // Auth/config errors never heal by retrying -> stop retry loop.
            // Network/throttle/5xx -> retry with WorkManager backoff.
            if (GoogleDocsMonitorSync.isRetryable(e)) Result.retry() else Result.success()
        }
    }

    private suspend fun getFreshToken(manager: GoogleOAuthManager): String? =
        suspendCancellableCoroutine { continuation ->
            manager.withFreshToken(
                onToken = { token ->
                    if (continuation.isActive) continuation.resume(token)
                },
                onError = {
                    if (continuation.isActive) continuation.resume(null)
                }
            )
        }

    /**
     * Headless mirror of ViewModel.applyBlocking: new monitored words may
     * have completed the quota while the app is closed.
     */
    private suspend fun reapplyBlocking(dayKey: String) {
        val ctx = applicationContext
        val db = com.draftlock.app.data.DraftLockDatabase.get(ctx)
        val blocker = AppBlocker(ctx)
        val todayWords = try { store.todayWords.first() } catch (_: Exception) { 0 }
        val quota = try { store.quota.first() } catch (_: Exception) { 1000 }
        val monitorKey = try { store.monitorDayKey.first() } catch (_: Exception) { "" }
        val monitorWords = if (monitorKey == dayKey) {
            try { store.monitorWords.first() } catch (_: Exception) { 0 }
        } else 0
        val logic = try { store.logic.first() } catch (_: Exception) { "AND" }
        val overrideUntil = try { store.overrideUntil.first() } catch (_: Exception) { 0L }
        val writingDone = (todayWords + monitorWords) >= quota
        val reqs = try { db.dao().observeRequirements().first() } catch (_: Exception) { emptyList() }
        val enabled = reqs.filter { it.enabled }
        // Usage minutes can't be recomputed cheaply without UsageStats permission
        // checks here — VaultRefreshWorker owns that; writing-only fast path here.
        val shouldUnlock = if (enabled.isEmpty()) {
            writingDone || overrideUntil > System.currentTimeMillis()
        } else if (logic == "OR" && writingDone) {
            true
        } else {
            // AND path or unmet writing: leave state to VaultRefreshWorker's
            // full check (it has usage minutes); only UNSUSPEND on certain win.
            (writingDone && logic == "OR") || overrideUntil > System.currentTimeMillis()
        }
        if (!shouldUnlock) return
        val locked = try { db.dao().observeLockedApps().first() } catch (_: Exception) { emptyList() }
        blocker.unsuspend(locked.filter { it.enabled }.map { it.packageName })
    }
}

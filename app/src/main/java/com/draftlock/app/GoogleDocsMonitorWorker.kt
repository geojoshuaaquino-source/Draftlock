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
}

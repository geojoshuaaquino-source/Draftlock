package com.draftlock.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Reconciles Google Docs writing progress independently of the DraftLock UI.
 *
 * WorkManager may run this approximately every 15 minutes or later because Android
 * controls background execution. The important part is that the worker compares the
 * current saved document against the last persisted snapshot, so words added while
 * DraftLock's process was dead are recovered on the next successful check.
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
        val counts = runCatching { JSONObject(store.monitorCounts.first()) }.getOrElse { JSONObject() }
        val manager = GoogleOAuthManager(applicationContext)

        val token = getFreshToken(manager) ?: return Result.retry()

        return try {
            val files = docsRepo.findFiles(token, "")
                .filter { prefix.isBlank() || it.name.startsWith(prefix, ignoreCase = true) }

            var added = 0
            for (file in files) {
                val wordsNow = countWords(docsRepo.getDocumentText(token, file.id))
                val previous = counts.optInt(file.id, -1)

                if (previous < 0) {
                    // First observation: establish a baseline, never count existing text.
                    counts.put(file.id, wordsNow)
                } else {
                    if (wordsNow > previous) added += wordsNow - previous
                    // Store the latest snapshot even when words were deleted.
                    counts.put(file.id, wordsNow)
                }
            }

            val resetMinutes = store.resetMinutes.first()
            val dayKey = UsageTracker.periodStartMillis(resetMinutes).toString()
            val storedKey = store.monitorDayKey.first()
            val currentWords = if (storedKey == dayKey) store.monitorWords.first() else 0

            store.setMonitorWords(currentWords + added, dayKey)
            store.setMonitorCounts(counts.toString())

            Result.success()
        } catch (_: Exception) {
            Result.retry()
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

    private fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}

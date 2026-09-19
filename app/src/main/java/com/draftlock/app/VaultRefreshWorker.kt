package com.draftlock.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Background truth-sync: runs every 15 min AND after reboot so DraftLock
 * stays fresh WITHOUT opening the app.
 *
 * Fixes "only refreshing when I open the app": foreground loops in
 * MainActivity/ViewModel never run when the app is closed, so usage
 * minutes, suspend/unsuspend, day rollover, stats snapshot and the widget
 * all went stale. This worker does all of that headlessly:
 *
 * 1. Day rollover (period-based writing day, honors resetMinutes).
 * 2. Usage minutes recompute for requirements (UsageStats).
 * 3. Re-apply suspend/unsuspend via AppBlocker.
 * 4. Snapshot DailyRecord (typed + same-day monitor words).
 * 5. Push widget.
 *
 * No network required (pure local) so it runs in Doze maintenance windows.
 */
class VaultRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val store = SettingsStore(ctx)
            val db = DraftLockDatabase.get(ctx)
            val dao = db.dao()
            val usage = UsageTracker(ctx)
            val blocker = AppBlocker(ctx)

            val resetMinutes = try { store.resetMinutes.first() } catch (_: Exception) { 0 }
            val dayKey = UsageTracker.periodStartMillis(resetMinutes).toString()

            // 1. Day rollover: new writing day -> reset counters, clear override flag.
            try {
                val todayKey = store.todayKey.first()
                if (todayKey != dayKey) {
                    store.setTodayWords(0, dayKey)
                    store.setMonitorWords(0, dayKey)
                    store.clearOverride()
                } else {
                    // Same day: still snapshot so stats/widget include monitor words.
                    store.snapshotDay()
                }
            } catch (_: Exception) {}

            // 2+3. Recompute usage + re-apply blocking (headless mirror of
            // ViewModel.refreshUsage/applyBlocking, which only run in foreground).
            try {
                if (usage.hasUsageAccess()) {
                    val reqs = try { dao.observeRequirements().first() } catch (_: Exception) { emptyList() }
                    val start = UsageTracker.periodStartMillis(resetMinutes)
                    val minutesByPkg = reqs.associate { it.packageName to usage.minutesForPackage(it.packageName, start) }

                    val quota = try { store.quota.first() } catch (_: Exception) { 1000 }
                    val todayWords = try { store.todayWords.first() } catch (_: Exception) { 0 }
                    val monitorKey = try { store.monitorDayKey.first() } catch (_: Exception) { "" }
                    val monitorWords = if (monitorKey == dayKey) {
                        try { store.monitorWords.first() } catch (_: Exception) { 0 }
                    } else 0
                    val logic = try { store.logic.first() } catch (_: Exception) { "AND" }
                    val overrideUntil = try { store.overrideUntil.first() } catch (_: Exception) { 0L }

                    val writingDone = (todayWords + monitorWords) >= quota
                    val enabled = reqs.filter { it.enabled }
                    val appDone = enabled.map { (minutesByPkg[it.packageName] ?: 0) >= it.requiredMinutes }
                    val reqsDone = if (enabled.isEmpty()) true
                        else if (logic == "OR") writingDone || appDone.any { it }
                        else writingDone && appDone.all { it }
                    val allDone = if (enabled.isEmpty()) writingDone else reqsDone
                    val shouldUnlock = allDone || overrideUntil > System.currentTimeMillis()

                    val locked = try { dao.observeLockedApps().first() } catch (_: Exception) { emptyList() }
                    val packages = locked.filter { it.enabled }.map { it.packageName }
                    if (shouldUnlock) blocker.unsuspend(packages) else blocker.suspend(packages)
                }
            } catch (_: Exception) {}

            // 5. Widget push (throttle respected inside updateAll).
            try { DraftLockWidget.updateAll(ctx) } catch (_: Exception) {}

            Result.success()
        } catch (_: Exception) {
            Result.success() // never wedge WorkManager with retry loops for local work
        }
    }
}

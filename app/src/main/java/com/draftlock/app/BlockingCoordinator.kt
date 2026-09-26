package com.draftlock.app

import android.content.Context
import com.draftlock.app.data.DraftLockDatabase
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Single source of truth for deciding whether locked apps should be accessible.
 *
 * This is deliberately usable from both WorkManager and the AccessibilityService,
 * because either mechanism can be delayed or killed independently on some devices.
 */
data class BlockingDecision(
    val dayKey: String,
    val shouldUnlock: Boolean,
    val usageAccess: Boolean
)

class BlockingCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val store = SettingsStore(appContext)
    private val dao = DraftLockDatabase.get(appContext).dao()
    private val usage = UsageTracker(appContext)
    private val blocker = AppBlocker(appContext)

    suspend fun evaluate(): BlockingDecision {
        val resetMinutes = store.resetMinutes.first()
        val dayKey = UsageTracker.periodStartMillis(resetMinutes).toString()

        // Rollover must happen before reading today's values, so a new day
        // cannot inherit yesterday's quota, monitor words, or override.
        store.ensureCurrentDay(dayKey)

        val requirements = dao.observeRequirements().first()
        val enabledRequirements = requirements.filter { it.enabled }
        val usageAccess = usage.hasUsageAccess()

        val minutesByPackage = if (usageAccess) {
            enabledRequirements.associate { req ->
                req.packageName to usage.minutesForPackage(
                    req.packageName,
                    UsageTracker.periodStartMillis(resetMinutes)
                )
            }
        } else {
            // Missing Usage Access means app-time requirements cannot be proven.
            // Keep the requirement unsatisfied rather than accidentally unlocking.
            enabledRequirements.associate { it.packageName to 0 }
        }

        val quota = store.quota.first()
        val todayWords = store.todayWords.first()
        val monitorWords = if (store.monitorDayKey.first() == dayKey) {
            store.monitorWords.first()
        } else {
            0
        }

        val writingDone = (todayWords + monitorWords) >= quota
        val logic = store.logic.first()
        val appDone = enabledRequirements.map { req ->
            (minutesByPackage[req.packageName] ?: 0) >= req.requiredMinutes
        }

        val conditionsDone = when {
            enabledRequirements.isEmpty() -> writingDone
            logic == "OR" -> writingDone || appDone.any { it }
            else -> writingDone && appDone.all { it }
        }

        val overrideUntil = store.overrideUntil.first()
        val shouldUnlock = conditionsDone || overrideUntil > System.currentTimeMillis()

        return BlockingDecision(
            dayKey = dayKey,
            shouldUnlock = shouldUnlock,
            usageAccess = usageAccess
        )
    }

    suspend fun reconcile(): BlockingDecision {
        val decision = evaluate()
        val packages = dao.observeLockedApps()
            .first()
            .filter { it.enabled }
            .map { it.packageName }

        if (decision.shouldUnlock) {
            val result = blocker.unsuspendChecked(packages)
            if (result.error != null || result.failedPackages.isNotEmpty()) {
                throw IllegalStateException(
                    "Failed to unsuspend packages: " +
                        (result.error?.message ?: result.failedPackages.joinToString())
                )
            }
        } else {
            val result = blocker.suspendChecked(packages)
            if (result.error != null || result.failedPackages.isNotEmpty()) {
                throw IllegalStateException(
                    "Failed to suspend packages: " +
                        (result.error?.message ?: result.failedPackages.joinToString())
                )
            }
        }

        return decision
    }
}

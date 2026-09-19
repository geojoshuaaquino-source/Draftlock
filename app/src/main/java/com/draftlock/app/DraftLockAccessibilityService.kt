package com.draftlock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.draftlock.app.data.DraftLockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DraftLockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPkg: String? = null
    private var lastTriggerMs: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        // throttle 2s per pkg
        if (pkg == lastPkg && System.currentTimeMillis() - lastTriggerMs < 2000) return
        lastPkg = pkg
        checkAndBlock(pkg)
    }

    private fun checkAndBlock(foregroundPkg: String) {
        scope.launch {
            try {
                val app = application
                val db = DraftLockDatabase.get(app)
                val store = com.draftlock.app.data.SettingsStore(app)
                val locked = db.dao().getLockedAppSync(foregroundPkg) ?: return@launch
                if (!locked.enabled) return@launch
                if (System.currentTimeMillis() < store.overrideUntil.first()) return@launch
                // Full unlock mirror of DraftLockViewModel.allConditionsComplete():
                // writing (typed + same-day monitored) combined with per-app
                // UsageStats requirements via AND/OR logic. The old code only
                // checked the writing quota, so OR-logic users were blocked
                // even after meeting an app-time requirement, and AND-logic
                // users could slip through on writing alone.
                val resetMinutes = store.resetMinutes.first()
                val dayKey = UsageTracker.periodStartMillis(resetMinutes).toString()
                val words = store.todayWords.first()
                val quota = store.quota.first()
                val monitored = if (store.monitorDayKey.first() == dayKey) store.monitorWords.first() else 0
                val writingDone = (words + monitored) >= quota
                val logic = try { store.logic.first() } catch (_: Exception) { "AND" }
                val requirements = try { db.dao().observeRequirements().first() } catch (_: Exception) { emptyList() }
                val enabled = requirements.filter { it.enabled }
                val unlocked = if (enabled.isEmpty()) {
                    writingDone
                } else {
                    val usage = UsageTracker(app)
                    val start = UsageTracker.periodStartMillis(resetMinutes)
                    val appDone = if (!usage.hasUsageAccess()) {
                        // Without Usage Access we can't verify app-time; fail
                        // closed on the writing goal only for AND, open for OR
                        // only when writing is done.
                        enabled.map { false }
                    } else {
                        enabled.map { req ->
                            usage.minutesForPackage(req.packageName, start) >= req.requiredMinutes
                        }
                    }
                    if (logic == "OR") writingDone || appDone.any { it }
                    else writingDone && appDone.all { it }
                }
                if (unlocked) return@launch
                // need to ensure app is indeed blocked – show overlay
                lastTriggerMs = System.currentTimeMillis()
                val i = Intent(app, BlockingOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("blocked_pkg", foregroundPkg)
                    putExtra("blocked_label", locked.displayName)
                }
                app.startActivity(i)
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {}
}

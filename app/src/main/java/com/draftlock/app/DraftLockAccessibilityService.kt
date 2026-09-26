package com.draftlock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.draftlock.app.data.DraftLockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DraftLockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reconcileMutex = Mutex()
    private val coordinator by lazy { BlockingCoordinator(applicationContext) }
    private var periodicJob: Job? = null
    private var lastPkg: String? = null
    private var lastTriggerMs: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Accessibility is the event-driven enforcement path and is much less
        // affected by WorkManager/Doze delays. Periodically reconcile too so a
        // midnight rollover or a changed quota is enforced even without a new
        // window event.
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                try {
                    reconcileMutex.withLock {
                        coordinator.reconcile()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DraftLock", "Accessibility reconcile failed", e)
                }
                delay(30_000L)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Avoid stacking multiple blocking activities for the same app.
        if (pkg == lastPkg && System.currentTimeMillis() - lastTriggerMs < 2000) return
        lastPkg = pkg
        checkAndBlock(pkg)
    }

    private fun checkAndBlock(foregroundPkg: String) {
        scope.launch {
            try {
                val db = DraftLockDatabase.get(applicationContext)
                val locked = db.dao().getLockedAppSync(foregroundPkg) ?: return@launch
                if (!locked.enabled) return@launch

                val decision = reconcileMutex.withLock {
                    // Evaluate (including day rollover) before showing the overlay.
                    coordinator.evaluate()
                }
                if (decision.shouldUnlock) return@launch

                lastTriggerMs = System.currentTimeMillis()
                val i = Intent(applicationContext, BlockingOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("blocked_pkg", foregroundPkg)
                    putExtra("blocked_label", locked.displayName)
                }
                applicationContext.startActivity(i)
            } catch (e: Exception) {
                android.util.Log.e(
                    "DraftLock",
                    "Accessibility blocking check failed for $foregroundPkg",
                    e
                )
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        periodicJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

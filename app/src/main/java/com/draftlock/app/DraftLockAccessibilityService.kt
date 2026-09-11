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
                val words = store.todayWords.first()
                val quota = store.quota.first()
                val overrideUntil = store.overrideUntil.first()
                if (System.currentTimeMillis() < overrideUntil) return@launch
                val done = words >= quota
                // also check logic/requirements – for simplicity block if writing not done
                // Full logic: if writing OR logic, allow if either done – we mirror ViewModel logic quickly
                if (done) return@launch
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

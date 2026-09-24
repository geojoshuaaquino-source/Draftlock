package com.draftlock.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

class DraftLockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, force = true)
    }

    override fun onEnabled(context: Context) {
        updateAll(context, force = true)
    }

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var lastPushMillis = 0L
        private const val THROTTLE_MILLIS = 30_000L

        fun updateAll(context: Context, force: Boolean = false) {
            if (!force && System.currentTimeMillis() - lastPushMillis < THROTTLE_MILLIS) return
            lastPushMillis = System.currentTimeMillis()
            val appContext = context.applicationContext
            widgetScope.launch {
                val store = SettingsStore(appContext)
                val endAt = store.sprintEndAt.first()
                // Show EFFECTIVE words (typed + same-day monitored) with quota,
                // not just monitor words — the old text understated progress.
                val resetMinutes = try { store.resetMinutes.first() } catch (_: Exception) { 0 }
                val dayKey = UsageTracker.periodStartMillis(resetMinutes).toString()
                val todayKey = try { store.todayKey.first() } catch (_: Exception) { "" }
                val todayWords = if (todayKey == dayKey) {
                    try { store.todayWords.first() } catch (_: Exception) { 0 }
                } else 0
                val monitorKey = try { store.monitorDayKey.first() } catch (_: Exception) { "" }
                val monitorWords = if (monitorKey == dayKey) {
                    try { store.monitorWords.first() } catch (_: Exception) { 0 }
                } else 0
                val quota = try { store.quota.first() } catch (_: Exception) { 1000 }
                val monitorEnabled = try { store.monitorEnabled.first() } catch (_: Exception) { false }
                val effective = (todayWords + monitorWords).coerceAtLeast(0)

                val remaining = if (endAt > 0L) {
                    max(0L, ((endAt - System.currentTimeMillis()) / 1000L))
                } else 0L

                val timer = if (remaining > 0L) {
                    String.format("%02d:%02d", remaining / 60, remaining % 60)
                } else {
                    "NO SPRINT"
                }

                val status = if (monitorEnabled) "Docs monitor active" else "Monitor off"
                val remainingWords = (quota - effective).coerceAtLeast(0)
                val views = RemoteViews(appContext.packageName, R.layout.widget_draftlock).apply {
                    setTextViewText(R.id.widget_timer, timer)
                    setTextViewText(R.id.widget_words, "$effective / $quota words • $remainingWords to go")
                    setTextViewText(R.id.widget_status, status)

                    // MainActivity is the single real entry point (MockupActivity
                    // is a design prototype, not the launcher).
                    val launch = Intent(appContext, MockupActivity::class.java)
                    val pending = PendingIntent.getActivity(
                        appContext,
                        8101,
                        launch,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    setOnClickPendingIntent(R.id.widget_root, pending)
                    setOnClickPendingIntent(R.id.widget_title, pending)
                    setOnClickPendingIntent(R.id.widget_timer, pending)
                    setOnClickPendingIntent(R.id.widget_words, pending)
                    setOnClickPendingIntent(R.id.widget_status, pending)
                }

                AppWidgetManager.getInstance(appContext).updateAppWidget(
                    ComponentName(appContext, DraftLockWidget::class.java),
                    views
                )
            }
        }
    }
}

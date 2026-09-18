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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

class DraftLockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    override fun onEnabled(context: Context) {
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val store = SettingsStore(appContext)
                val started = store.sprintStartedAt.first()
                val minutes = store.sprintMinutes.first()
                val monitorWords = store.monitorWords.first()
                val monitorEnabled = store.monitorEnabled.first()

                val remaining = if (started > 0L) {
                    max(0L, ((started + minutes * 60_000L - System.currentTimeMillis()) / 1000L))
                } else 0L

                val timer = if (remaining > 0L) {
                    String.format("%02d:%02d", remaining / 60, remaining % 60)
                } else {
                    "NO SPRINT"
                }

                val status = if (monitorEnabled) "Google Docs monitor active" else "Monitor off"
                val views = RemoteViews(appContext.packageName, R.layout.widget_draftlock).apply {
                    setTextViewText(R.id.widget_timer, timer)
                    setTextViewText(R.id.widget_words, "$monitorWords new words detected")
                    setTextViewText(R.id.widget_status, status)

                    val launch = Intent(appContext, MockupActivity::class.java)
                    val pending = PendingIntent.getActivity(
                        appContext,
                        8101,
                        launch,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
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

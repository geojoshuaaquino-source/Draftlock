package com.draftlock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.draftlock.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Messenger-style floating monitor.
 *
 * The overlay reads the same DataStore used by DraftLock's real monitoring
 * pipeline, so its displayed count changes as soon as the stored count changes.
 * The Google Docs monitor still determines how frequently Google is queried.
 */
class FloatingMonitorService : Service() {
    companion object {
        private const val CHANNEL_ID = "draftlock_floating_monitor"
        private const val NOTIFICATION_ID = 8102

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingMonitorService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingMonitorService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null
    private var timerJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var bubble: TextView? = null
    private var wordsText: TextView? = null
    private var timerText: TextView? = null
    private var expanded = false

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.draftlock.app.R.drawable.ic_launcher)
            .setContentTitle("DraftLock monitor")
            .setContentText("Floating word count and sprint timer are active")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else 0
        )

        showOverlay()
        observeStore()
    }

    private fun showOverlay() {
        val displayContext = if (Build.VERSION.SDK_INT >= 30) {
            createDisplayContext(display).createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null
            )
        } else {
            this
        }

        windowManager = displayContext.getSystemService(WindowManager::class.java)

        val root = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.argb(235, 9, 18, 35), dp(18))
        }

        bubble = TextView(displayContext).apply {
            text = "0w"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = rounded(Color.rgb(77, 141, 255), dp(32))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        wordsText = TextView(displayContext).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        timerText = TextView(displayContext).apply {
            setTextColor(Color.rgb(120, 223, 255))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val close = TextView(displayContext).apply {
            text = "Hide"
            setTextColor(Color.rgb(155, 175, 205))
            textSize = 11f
            setPadding(0, dp(8), 0, 0)
            setOnClickListener { collapse() }
        }

        root.addView(bubble, LinearLayout.LayoutParams(dp(58), dp(58)))

        val detail = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(wordsText)
            addView(timerText)
            addView(close)
        }
        root.addView(detail)

        val lp = WindowManager.LayoutParams(
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(220)
        }

        overlayView = root
        windowManager?.addView(root, lp)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        bubble?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    lp.x = startX + dx
                    lp.y = startY + dy
                    windowManager?.updateViewLayout(root, lp)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        expanded = !expanded
                        detail.visibility = if (expanded) View.VISIBLE else View.GONE
                    }
                    true
                }

                else -> true
            }
        }
    }

    private fun collapse() {
        expanded = false
        overlayView?.getChildAt(1)?.visibility = View.GONE
    }

    private fun observeStore() {
        val store = SettingsStore(applicationContext)

        updateJob = scope.launch {
            while (true) {
                val typed = store.todayWords.first()
                val monitored = store.monitorWords.first()
                val todayKey = store.todayKey.first()
                val monitorKey = store.monitorDayKey.first()
                val quota = store.quota.first()
                val reset = store.resetMinutes.first()
                val dayKey = UsageTracker.periodStartMillis(reset).toString()
                val effective = if (todayKey == dayKey) {
                    typed + if (monitorKey == dayKey) monitored else 0
                } else {
                    0
                }
                val words = effective.coerceAtLeast(0)
                bubble?.text = words.toString() + "w"
                wordsText?.text = words.toString() + " / " + quota + " words"
                delay(500)
            }
        }
        timerJob = scope.launch {
            while (true) {
                val endAt = store.sprintEndAt.first()
                val remaining = if (endAt > 0L) {
                    max(0L, (endAt - System.currentTimeMillis()) / 1000L)
                } else {
                    0L
                }

                timerText?.text = if (remaining > 0L) {
                    "Sprint  " + "%02d:%02d".format(remaining / 60, remaining % 60)
                } else {
                    "No active sprint"
                }
                delay(1000)
            }
        }
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "DraftLock floating monitor",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        updateJob?.cancel()
        timerJob?.cancel()
        scope.cancel()
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

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
import android.hardware.display.DisplayManager
import android.view.Display
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
            if (!Settings.canDrawOverlays(context)) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    SettingsStore(context.applicationContext).setFloatingOverlay(false)
                }
                android.widget.Toast.makeText(context, "Allow DraftLock to display over other apps, then enable the floating monitor again.", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, FloatingMonitorService::class.java)
                )
            } catch (e: RuntimeException) {
                android.util.Log.e("DraftLockOverlay", "Could not start floating monitor service", e)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    SettingsStore(context.applicationContext).setFloatingOverlay(false)
                }
                android.widget.Toast.makeText(context, "Floating monitor could not start: " + (e.message ?: e.javaClass.simpleName), android.widget.Toast.LENGTH_LONG).show()
            }
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

        try {
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

            if (!showOverlay()) {
                persistOverlayDisabled()
                stopSelf()
                return
            }
            observeStore()
        } catch (e: RuntimeException) {
            android.util.Log.e("DraftLockOverlay", "Floating monitor initialization failed", e)
            android.widget.Toast.makeText(this, "Floating monitor failed: " + (e.message ?: e.javaClass.simpleName), android.widget.Toast.LENGTH_LONG).show()
            persistOverlayDisabled()
            stopSelf()
        }
    }

    private fun showOverlay(): Boolean {
        val displayContext = if (Build.VERSION.SDK_INT >= 30) {
            // Service contexts may not be associated with a display. Resolve it explicitly.
            val displayManager = getSystemService(DisplayManager::class.java)
            val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            if (defaultDisplay != null) {
                applicationContext.createDisplayContext(defaultDisplay).createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    null
                )
            } else {
                applicationContext
            }
        } else {
            this
        }

        windowManager = displayContext.getSystemService(WindowManager::class.java)

        val root = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.argb(245, 9, 18, 35), dp(18))
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
            text = "×"
            contentDescription = "Close floating monitor"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(48, 59, 78), dp(18))
            setOnClickListener {
                persistOverlayDisabled()
                stopSelf()
            }
        }

        val header = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bubble, LinearLayout.LayoutParams(dp(58), dp(58)))
            val spacer = View(displayContext)
            addView(spacer, LinearLayout.LayoutParams(dp(8), 1))
            addView(close, LinearLayout.LayoutParams(dp(36), dp(36)))
        }
        root.addView(header)

        val sprintControls = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val durationRow = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(15, 25, 45, 60).forEach { minutes ->
            val choice = TextView(displayContext).apply {
                text = "${minutes}m"
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(48, 59, 78), dp(10))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    scope.launch {
                        SettingsStore(applicationContext).setSprintMinutes(minutes)
                        android.widget.Toast.makeText(this@FloatingMonitorService, "Sprint set to ${minutes} minutes", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val params = LinearLayout.LayoutParams(0, dp(36), 1f)
            if (minutes != 15) params.marginStart = dp(4)
            durationRow.addView(choice, params)
        }
        sprintControls.addView(durationRow)

        val sprintAction = TextView(displayContext).apply {
            text = "Start sprint"
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = rounded(Color.rgb(120, 223, 255), dp(10))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                scope.launch {
                    val store = SettingsStore(applicationContext)
                    val endAt = store.sprintEndAt.first()
                    if (endAt > System.currentTimeMillis()) {
                        store.setSprintStartedAt(0L)
                        store.setSprintEndAt(0L)
                    } else {
                        val now = System.currentTimeMillis()
                        val minutes = store.sprintMinutes.first().coerceIn(5, 120)
                        store.setSprintStartedAt(now)
                        store.setSprintEndAt(now + minutes * 60_000L)
                    }
                }
            }
        }
        val openDoc = TextView(displayContext).apply {
            text = "Open selected Google Doc"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(48, 59, 78), dp(10))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                scope.launch {
                    val id = SettingsStore(applicationContext).googleDocumentId.first()
                    if (id.isBlank()) {
                        android.widget.Toast.makeText(this@FloatingMonitorService, "Select a Google Doc in DraftLock first", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://docs.google.com/document/d/$id/edit"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this@FloatingMonitorService, "Could not open Google Doc: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        sprintControls.addView(sprintAction, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        sprintControls.addView(openDoc, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(6) })

        val detail = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(4), dp(8), dp(4), dp(4))
            addView(wordsText)
            addView(timerText)
            addView(sprintControls)
        }
        root.addView(detail)

        val lp = WindowManager.LayoutParams(
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Keep the overlay bounded to its actual content; never let it cover
            // the screen with an unconstrained/no-limits window.
            width = dp(180)
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(220)
        }

        overlayView = root
        try {
            windowManager?.addView(root, lp)
        } catch (e: RuntimeException) {
            android.util.Log.e("DraftLockOverlay", "WindowManager.addView failed", e)
            overlayView = null
            windowManager = null
            android.widget.Toast.makeText(this, "Could not display floating bubble: " + (e.message ?: e.javaClass.simpleName), android.widget.Toast.LENGTH_LONG).show()
            return false
        }

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
                    val metrics = resources.displayMetrics
                    val maxX = (metrics.widthPixels - root.width).coerceAtLeast(0)
                    val maxY = (metrics.heightPixels - root.height).coerceAtLeast(0)
                    lp.x = (startX + dx).coerceIn(0, maxX)
                    lp.y = (startY + dy).coerceIn(0, maxY)
                    try {
                        windowManager?.updateViewLayout(root, lp)
                    } catch (e: RuntimeException) {
                        android.util.Log.e("DraftLockOverlay", "Could not move floating monitor", e)
                        android.widget.Toast.makeText(
                            this,
                            "Floating monitor stopped while moving: " + (e.message ?: e.javaClass.simpleName),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        stopSelf()
                    }
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
        return true
    }

    private fun persistOverlayDisabled() {
        // Do not use the service scope here: onDestroy cancels it immediately
        // after the close action stops the service.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            SettingsStore(applicationContext).setFloatingOverlay(false)
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
                val minutes = store.sprintMinutes.first()
                val remaining = if (endAt > 0L) {
                    max(0L, (endAt - System.currentTimeMillis()) / 1000L)
                } else {
                    0L
                }

                timerText?.text = if (remaining > 0L) {
                    "Sprint  " + "%02d:%02d".format(remaining / 60, remaining % 60)
                } else {
                    "No active sprint • ${minutes}m selected"
                }
                sprintAction.text = if (remaining > 0L) "Stop sprint" else "Start ${minutes}m sprint"
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

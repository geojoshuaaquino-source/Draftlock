package com.draftlock.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

class UsageTracker(private val context: Context) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    fun hasUsageAccess(): Boolean = try {
        val now = System.currentTimeMillis()
        usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
            .isNotEmpty()
    } catch (_: Exception) {
        false
    }

    fun openUsageAccessSettings() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun minutesForPackage(packageName: String, dayStartMillis: Long): Int {
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            dayStartMillis,
            now
        )
        val millis = stats.filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
        return (millis / 60_000L).toInt()
    }

    companion object {
        fun periodStartMillis(resetMinutes: Int, nowMillis: Long = System.currentTimeMillis()): Long {
            val zone = ZoneId.systemDefault()
            val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
            var date = LocalDate.of(now.year, now.month, now.dayOfMonth)
            val reset = date.atStartOfDay(zone).plusMinutes(resetMinutes.toLong())
            if (now.isBefore(reset)) date = date.minusDays(1)
            return date.atStartOfDay(zone).plusMinutes(resetMinutes.toLong()).toInstant().toEpochMilli()
        }
    }
}

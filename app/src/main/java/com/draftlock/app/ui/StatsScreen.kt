package com.draftlock.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.draftlock.app.DraftLockViewModel
import com.draftlock.app.data.DailyRecord
import com.draftlock.app.ui.theme.DraftLockColors
import com.draftlock.app.ui.theme.GlassTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure helpers — unit-testable, no Android deps. */
object StatsUtils {
    fun last7Keys(todayKey: String): List<String> = try {
        val dayMs = 86_400_000L
        val base = todayKey.toLongOrNull() ?: System.currentTimeMillis()
        (6 downTo 0).map { (base - it * dayMs).toString() }
    } catch (_: Exception) {
        (6 downTo 0).map { (System.currentTimeMillis() - it * 86_400_000L).toString() }
    }

    fun streak(records: List<DailyRecord>, todayKey: String, todayWords: Int, quota: Int): Int {
        val byKey = records.associateBy { it.dayKey }.toMutableMap()
        byKey[todayKey] = DailyRecord(todayKey, todayWords, quota)
        var s = 0
        var cursor = try { todayKey.toLongOrNull() ?: return 0 } catch (_: Exception) { return 0 }
        // If today not yet met, streak counts from yesterday.
        val today = byKey[todayKey]
        if (today == null || today.words < (today.quota.takeIf { it > 0 } ?: quota)) {
            cursor -= 86_400_000L
        }
        while (true) {
            val r = byKey[cursor.toString()] ?: break
            if (r.words >= r.quota.coerceAtLeast(1)) s++ else break
            cursor -= 86_400_000L
            if (s > 365) break
        }
        return s
    }

    fun completionRate(records: List<DailyRecord>): Float {
        if (records.isEmpty()) return 0f
        return records.count { it.words >= it.quota.coerceAtLeast(1) } / records.size.toFloat()
    }
}

@Composable
fun StatsScreen(vm: DraftLockViewModel, todayWordsLive: Int, quota: Int) {
    // Collect so the chart recomposes standalone (value-only reads go stale).
    val recent by vm.recentDays.collectAsStateWithLifecycle()
    val resetMinutes by vm.resetMinutes.collectAsStateWithLifecycle()
    val dayKey = com.draftlock.app.UsageTracker.periodStartMillis(resetMinutes).toString()
    val merged = remember(recent, todayWordsLive, quota, dayKey) {
        val map = recent.associateBy { it.dayKey }.toMutableMap()
        val live = (map[dayKey]?.words ?: 0).coerceAtLeast(0)
        // Live today overrides stored snapshot so the chart moves while typing.
        map[dayKey] = DailyRecord(dayKey, maxOf(live, todayWordsLive), quota)
        StatsUtils.last7Keys(dayKey).map { k -> map[k] ?: DailyRecord(k, 0, quota) }
    }
    val total7 = merged.sumOf { it.words }
    val avg = if (merged.isNotEmpty()) total7 / merged.size else 0
    val best = merged.maxOfOrNull { it.words } ?: 0
    val streak = remember(recent, todayWordsLive, quota, dayKey) {
        StatsUtils.streak(recent, dayKey, todayWordsLive, quota)
    }
    val rate = remember(recent) { StatsUtils.completionRate(recent) }

    LazyColumn(
        Modifier.fillMaxSize().background(DraftLockColors.bg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header — Deep Blue Glass bento, 18dp, no lime
            Card(
                shape = RoundedCornerShape(GlassTokens.bentoRadius.dp),
                colors = CardDefaults.cardColors(containerColor = GlassTokens.glassStrong),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STATISTICS", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, letterSpacing = 1.2.sp, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$todayWordsLive", style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 40.sp), fontWeight = FontWeight.Black, color = Color.White)
                        Text("/ $quota today", style = MaterialTheme.typography.titleMedium, color = DraftLockColors.muted, modifier = Modifier.padding(bottom = 5.dp))
                        Spacer(Modifier.weight(1f))
                        StreakBadge(streak)
                    }
                    LinearProgressIndicator(
                        progress = { (todayWordsLive.toFloat() / quota.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                        color = DraftLockColors.accent,
                        trackColor = Color(0xFF16283F)
                    )
                    Text(
                        "${(rate * 100).toInt()}% days hit quota • streak $streak",
                        style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard("7-DAY", "$total7", "words", Modifier.weight(1f))
                KpiCard("AVG/DAY", "$avg", "words", Modifier.weight(1f))
                KpiCard("BEST", "$best", "words", Modifier.weight(1f))
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(GlassTokens.bentoRadius.dp),
                colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("LAST 7 DAYS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = DraftLockColors.muted, letterSpacing = 1.0.sp)
                        Text("quota $quota", style = MaterialTheme.typography.labelSmall, color = DraftLockColors.accent)
                    }
                    WeeklyBars(days = merged, quota = quota)
                    // Accessible table fallback — not color-only (ui-ux-pro-max chart rule).
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        merged.forEach { d ->
                            DayRow(d, quota, d.dayKey == dayKey)
                        }
                    }
                }
            }
        }
        if (recent.isEmpty() && todayWordsLive == 0) {
            item {
                Card(
                    shape = RoundedCornerShape(GlassTokens.bentoRadius.dp),
                    colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel)
                ) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No history yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Write today and your 7-day chart, streak and averages appear here.", style = MaterialTheme.typography.bodySmall, color = DraftLockColors.muted)
                    }
                }
            }
        } else {
            items(recent.take(14)) { d ->
                HistoryRow(d)
            }
        }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(
            if (streak >= 3) DraftLockColors.accent else Color(0xFF16283F)
        ).padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (streak > 0) "🔥 $streak DAY${if (streak == 1) "" else "S"}" else "START STREAK",
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black,
            color = if (streak >= 3) Color.White else DraftLockColors.muted, fontSize = 10.sp
        )
    }
}

@Composable
private fun KpiCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(GlassTokens.bentoRadius.dp),
        colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, letterSpacing = 0.8.sp, fontSize = 9.sp)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun WeeklyBars(days: List<DailyRecord>, quota: Int) {
    val max = maxOf(quota, days.maxOfOrNull { it.words } ?: quota, 1)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = days.size
        if (n == 0) return@Canvas
        val gap = size.width / n
        val barW = (gap * 0.52f).coerceAtLeast(8f)
        days.forEachIndexed { i, d ->
            val h = (d.words.toFloat() / max.toFloat()).coerceIn(0f, 1f) * size.height * 0.86f
            val x = gap * i + (gap - barW) / 2f
            val met = d.words >= quota.coerceAtLeast(1)
            drawRoundRect(
                color = if (met) Color(0xFF3D9BFF) else Color(0xFF274264),
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h - 18f),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val label = try {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(d.dayKey.toLongOrNull() ?: 0L))
            } catch (_: Exception) { "–" }
            Text(label.take(2).uppercase(), style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted, fontSize = 9.sp, modifier = Modifier.width(28.dp))
        }
    }
}

@Composable
private fun DayRow(d: DailyRecord, quota: Int, isToday: Boolean) {
    val met = d.words >= quota.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (met) DraftLockColors.accent else Color(0xFF274264))
        )
        Text(
            (try { SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(d.dayKey.toLong())) } catch (_: Exception) { d.dayKey.take(8) }) + if (isToday) " • today" else "",
            style = MaterialTheme.typography.labelSmall, color = if (isToday) Color.White else DraftLockColors.muted,
            modifier = Modifier.weight(1f)
        )
        Text("${d.words}w", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (met) DraftLockColors.accent else Color.White)
        Text(if (met) "✓" else "○", color = if (met) DraftLockColors.accent else DraftLockColors.muted, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun HistoryRow(d: DailyRecord) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DraftLockColors.panel),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    try { SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(Date(d.dayKey.toLong())) } catch (_: Exception) { "Day ${d.dayKey.take(8)}" },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    "${d.words} / ${d.quota} words" + if (d.overrideUsed) " • override used" else "",
                    style = MaterialTheme.typography.labelSmall, color = DraftLockColors.muted
                )
            }
            if (d.words >= d.quota.coerceAtLeast(1)) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(DraftLockColors.accent.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("MET ✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = DraftLockColors.accent, fontSize = 10.sp)
                }
            } else {
                CircularProgressIndicator(
                    progress = { (d.words.toFloat() / d.quota.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.size(28.dp), strokeWidth = 3.dp,
                    color = DraftLockColors.accent, trackColor = Color(0xFF16283F)
                )
            }
        }
    }
}

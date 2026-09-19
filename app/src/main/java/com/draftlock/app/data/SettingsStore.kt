package com.draftlock.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.draftLockDataStore by preferencesDataStore("draftlock_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val quota = intPreferencesKey("daily_quota")
        val resetMinutes = intPreferencesKey("reset_minutes")
        val logic = stringPreferencesKey("logic")
        val documentText = stringPreferencesKey("document_text")
        val documentName = stringPreferencesKey("document_name")
        val overrideUntil = longPreferencesKey("override_until")
        val overrideUsed = booleanPreferencesKey("override_used")
        val googleFolderId = stringPreferencesKey("google_folder_id")
        val googleDocumentId = stringPreferencesKey("google_document_id")
        val googleAutoSave = booleanPreferencesKey("google_auto_save")
        val todayWords = intPreferencesKey("today_words")
        val todayKey = stringPreferencesKey("today_key")
        val sprintMinutes = intPreferencesKey("sprint_minutes")
        val sprintStartedAt = longPreferencesKey("sprint_started_at")
        val sprintEndAt = longPreferencesKey("sprint_end_at")
        val monitorEnabled = booleanPreferencesKey("monitor_enabled")
        val monitorPrefix = stringPreferencesKey("monitor_prefix")
        val monitorWords = intPreferencesKey("monitor_words")
        val monitorDayKey = stringPreferencesKey("monitor_day_key")
        val monitorCounts = stringPreferencesKey("monitor_counts")
        val monitorMeta = stringPreferencesKey("monitor_meta")
    }

    val quota: Flow<Int> = context.draftLockDataStore.data.map { it[Keys.quota] ?: 1000 }
    val resetMinutes: Flow<Int> = context.draftLockDataStore.data.map { it[Keys.resetMinutes] ?: 0 }
    val logic: Flow<String> = context.draftLockDataStore.data.map { it[Keys.logic] ?: "AND" }
    val documentText: Flow<String> = context.draftLockDataStore.data.map { it[Keys.documentText] ?: "" }
    val documentName: Flow<String> = context.draftLockDataStore.data.map { it[Keys.documentName] ?: "Today's Draft" }
    val overrideUntil: Flow<Long> = context.draftLockDataStore.data.map { it[Keys.overrideUntil] ?: 0L }
    val overrideUsed: Flow<Boolean> = context.draftLockDataStore.data.map { it[Keys.overrideUsed] ?: false }
    val googleFolderId: Flow<String> = context.draftLockDataStore.data.map { it[Keys.googleFolderId] ?: "" }
    val googleDocumentId: Flow<String> = context.draftLockDataStore.data.map { it[Keys.googleDocumentId] ?: "" }
    val googleAutoSave: Flow<Boolean> = context.draftLockDataStore.data.map { it[Keys.googleAutoSave] ?: true }
    val todayWords: Flow<Int> = context.draftLockDataStore.data.map { it[Keys.todayWords] ?: 0 }
    val todayKey: Flow<String> = context.draftLockDataStore.data.map { it[Keys.todayKey] ?: "" }
    val sprintMinutes: Flow<Int> = context.draftLockDataStore.data.map { it[Keys.sprintMinutes] ?: 25 }
    val sprintStartedAt: Flow<Long> = context.draftLockDataStore.data.map { it[Keys.sprintStartedAt] ?: 0L }
    val sprintEndAt: Flow<Long> = context.draftLockDataStore.data.map { it[Keys.sprintEndAt] ?: 0L }
    val monitorEnabled: Flow<Boolean> = context.draftLockDataStore.data.map { it[Keys.monitorEnabled] ?: false }
    val monitorPrefix: Flow<String> = context.draftLockDataStore.data.map { it[Keys.monitorPrefix] ?: "" }
    val monitorWords: Flow<Int> = context.draftLockDataStore.data.map { it[Keys.monitorWords] ?: 0 }
    val monitorDayKey: Flow<String> = context.draftLockDataStore.data.map { it[Keys.monitorDayKey] ?: "" }
    val monitorCounts: Flow<String> = context.draftLockDataStore.data.map { it[Keys.monitorCounts] ?: "{}" }
    val monitorMeta: Flow<String> = context.draftLockDataStore.data.map { it[Keys.monitorMeta] ?: "{}" }

    suspend fun setQuota(value: Int) = context.draftLockDataStore.edit { it[Keys.quota] = value.coerceAtLeast(1) }
    suspend fun setResetMinutes(value: Int) = context.draftLockDataStore.edit { it[Keys.resetMinutes] = value.coerceIn(0, 1439) }
    suspend fun setLogic(value: String) = context.draftLockDataStore.edit { it[Keys.logic] = value }
    suspend fun setDocumentText(value: String) = context.draftLockDataStore.edit { it[Keys.documentText] = value }
    suspend fun setDocumentName(value: String) = context.draftLockDataStore.edit { it[Keys.documentName] = value }

    suspend fun setOverride(until: Long) {
        context.draftLockDataStore.edit {
            it[Keys.overrideUntil] = until
            it[Keys.overrideUsed] = true
        }
        recordCurrentDay()
    }

    suspend fun clearOverride() = context.draftLockDataStore.edit { it[Keys.overrideUntil] = 0L }
    suspend fun setGoogleFolderId(value: String) = context.draftLockDataStore.edit { it[Keys.googleFolderId] = value }
    suspend fun setGoogleDocumentId(value: String) = context.draftLockDataStore.edit { it[Keys.googleDocumentId] = value }
    suspend fun setGoogleAutoSave(value: Boolean) = context.draftLockDataStore.edit { it[Keys.googleAutoSave] = value }
    suspend fun setSprintMinutes(value: Int) = context.draftLockDataStore.edit { it[Keys.sprintMinutes] = value.coerceIn(5, 120) }
    suspend fun setSprintStartedAt(value: Long) = context.draftLockDataStore.edit { it[Keys.sprintStartedAt] = value }
    suspend fun setSprintEndAt(value: Long) = context.draftLockDataStore.edit { it[Keys.sprintEndAt] = value }
    suspend fun setMonitorEnabled(value: Boolean) = context.draftLockDataStore.edit { it[Keys.monitorEnabled] = value }
    suspend fun setMonitorPrefix(value: String) = context.draftLockDataStore.edit { it[Keys.monitorPrefix] = value }
    suspend fun setMonitorWords(value: Int, key: String) = context.draftLockDataStore.edit { it[Keys.monitorWords] = value.coerceAtLeast(0); it[Keys.monitorDayKey] = key }
    suspend fun setMonitorCounts(value: String) = context.draftLockDataStore.edit { it[Keys.monitorCounts] = value }
    suspend fun setMonitorMeta(value: String) = context.draftLockDataStore.edit { it[Keys.monitorMeta] = value }

    suspend fun setTodayWords(value: Int, key: String) {
        context.draftLockDataStore.edit {
            val previousKey = it[Keys.todayKey] ?: ""
            if (previousKey != key) {
                it[Keys.overrideUsed] = false
                it[Keys.overrideUntil] = 0L
            }
            it[Keys.todayWords] = value.coerceAtLeast(0)
            it[Keys.todayKey] = key
        }
        recordCurrentDay()
    }

    private suspend fun recordCurrentDay() {
        val prefs = context.draftLockDataStore.data.first()
        val dayKey = prefs[Keys.todayKey] ?: return
        if (dayKey.isBlank()) return
        DraftLockDatabase.get(context).dao().upsertDay(
            DailyRecord(
                dayKey = dayKey,
                words = prefs[Keys.todayWords] ?: 0,
                quota = prefs[Keys.quota] ?: 1000,
                overrideUsed = prefs[Keys.overrideUsed] ?: false
            )
        )
    }
}

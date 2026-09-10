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

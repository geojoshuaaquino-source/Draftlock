package com.draftlock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_records")
data class DailyRecord(
    @PrimaryKey val dayKey: String,
    val words: Int,
    val quota: Int,
    val overrideUsed: Boolean = false
)

package com.draftlock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sprint_sessions")
data class SprintSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long = 0L,
    val plannedMinutes: Int,
    val wordsAtStart: Int,
    val wordsWritten: Int = 0,
    val completed: Boolean = false
)

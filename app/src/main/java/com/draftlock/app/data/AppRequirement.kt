package com.draftlock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_requirements")
data class AppRequirement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val displayName: String,
    val requiredMinutes: Int,
    val enabled: Boolean = true
)

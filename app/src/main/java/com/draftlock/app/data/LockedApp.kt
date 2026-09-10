package com.draftlock.app.data

import androidx.room.Entity

@Entity(tableName = "locked_apps")
data class LockedApp(
    @androidx.room.PrimaryKey val packageName: String,
    val displayName: String,
    val enabled: Boolean = true
)

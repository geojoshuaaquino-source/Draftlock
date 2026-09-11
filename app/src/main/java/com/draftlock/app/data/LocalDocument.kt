package com.draftlock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_documents")
data class LocalDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val wordCount: Int = 0
)

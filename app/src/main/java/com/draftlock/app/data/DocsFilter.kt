package com.draftlock.app.data

import com.draftlock.app.RemoteFile

object DocsFilter {
    fun filter(files: List<RemoteFile>, query: String): List<RemoteFile> {
        val q = query.trim()
        if (q.isBlank() || q.equals("DND", ignoreCase = true)) return files.sortedByDescending { it.modifiedTime }
        val lower = q.lowercase()
        return files.filter { it.name.lowercase().contains(lower) || it.id.lowercase().contains(lower) }
            .sortedByDescending { it.modifiedTime }
    }
    fun matches(file: RemoteFile, query: String): Boolean {
        if (query.isBlank()) return true
        val l = query.lowercase()
        return file.name.lowercase().contains(l) || file.id.lowercase().contains(l)
    }
}

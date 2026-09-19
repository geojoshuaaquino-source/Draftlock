package com.draftlock.app

import org.json.JSONObject

/**
 * Shared Google Docs monitor sync.
 *
 * The old code fetched the FULL text of EVERY matching doc on EVERY check
 * (every 10s foreground + every 15min background). With N docs that is
 * 1 Drive-list + N Docs-gets per check -> slow + 429 rate-limit errors.
 *
 * This version:
 * - prefilters server-side by prefix (Drive "name contains"),
 * - caps to the 25 most recently modified docs,
 * - skips Docs-get entirely when Drive modifiedTime is unchanged.
 */
object GoogleDocsMonitorSync {

    const val MAX_DOCS = 25

    data class SyncResult(
        val added: Int,
        val matched: Int,
        val fetched: Int,
        val skipped: Int,
        val truncated: Boolean,
        val countsJson: String,
        val metaJson: String
    )

    fun sync(
        token: String,
        prefix: String,
        countsJsonStr: String,
        metaJsonStr: String,
        docsRepo: GoogleDocsRepository,
        resetBaseline: Boolean = false
    ): SyncResult {
        val counts = runCatching { JSONObject(countsJsonStr) }.getOrElse { JSONObject() }
        val meta = runCatching { JSONObject(metaJsonStr) }.getOrElse { JSONObject() }
        val cleanPrefix = prefix.trim()

        val candidates = docsRepo.findFiles(token, "", cleanPrefix)
            .filter { cleanPrefix.isBlank() || it.name.startsWith(cleanPrefix, ignoreCase = true) }

        val truncated = candidates.size > MAX_DOCS
        val files = candidates.take(MAX_DOCS)

        var added = 0
        var fetched = 0
        var skipped = 0

        for (file in files) {
            val previous = counts.optInt(file.id, -1)
            val knownModified = meta.optString(file.id, "")

            // Skip the expensive Docs-get when Drive says the file is untouched
            // and we already have a baseline for it.
            if (!resetBaseline && previous >= 0 && knownModified.isNotBlank() && knownModified == file.modifiedTime) {
                skipped++
                continue
            }

            val wordsNow = countWords(docsRepo.getDocumentText(token, file.id))
            fetched++

            if (previous < 0 || resetBaseline) {
                // First observation: establish a baseline, never count existing text.
                counts.put(file.id, wordsNow)
            } else {
                if (wordsNow > previous) added += wordsNow - previous
                // Store the latest snapshot even when words were deleted.
                counts.put(file.id, wordsNow)
            }
            meta.put(file.id, file.modifiedTime)
        }

        return SyncResult(
            added = added,
            matched = files.size,
            fetched = fetched,
            skipped = skipped,
            truncated = truncated,
            countsJson = counts.toString(),
            metaJson = meta.toString()
        )
    }

    fun friendlyError(e: Throwable): String {
        if (e is GoogleAuthException) return "Google authorization required — reconnect Google and retry."
        if (e is kotlinx.coroutines.TimeoutCancellationException) return "Check timed out — weak connection. Retrying automatically."
        if (e is GoogleApiException) {
            return when {
                e.statusCode == 401 -> "Google authorization expired — reconnect Google and retry."
                e.statusCode == 403 -> e.message ?: "Google denied access (403)."
                e.statusCode == 429 || e.reason == "rateLimitExceeded" || e.reason == "userRateLimitExceeded" ->
                    "Google is throttling (rate limit) — wait a minute, then Check now."
                e.statusCode in 500..599 -> "Google server hiccup (${e.statusCode}) — retry in a bit."
                else -> e.message ?: "Google API request failed (${e.statusCode})."
            }
        }
        val msg = e.message.orEmpty()
        val cls = e.javaClass.simpleName
        return when {
            e is java.net.UnknownHostException -> "No connection — check internet, then Check now."
            e is java.net.SocketTimeoutException -> "Google timed out — weak connection. Retry."
            e is javax.net.ssl.SSLException -> "Secure connection failed — retry."
            cls.contains("ConnectException") -> "No connection — check internet, then Check now."
            msg.contains("Unable to resolve host", ignoreCase = true) -> "No connection — check internet, then Check now."
            msg.contains("timeout", ignoreCase = true) -> "Google timed out — retry."
            else -> "Monitor error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun isRetryable(e: Throwable): Boolean {
        if (e is GoogleAuthException) return false
        if (e is GoogleApiException) {
            // Auth / permission / disabled-API errors will not heal by retrying fast.
            if (e.statusCode == 401 || e.statusCode == 403) return false
            if (e.reason == "accessNotConfigured") return false
            return true
        }
        return true
    }

    private fun countWords(text: String): Int =
        if (text.isBlank()) 0
        else text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}

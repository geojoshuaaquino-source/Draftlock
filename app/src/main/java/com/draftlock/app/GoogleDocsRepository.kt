package com.draftlock.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Google Drive + Docs integration using the official REST APIs directly.
 * The access token is supplied by the OAuth layer; DraftLock never stores a password.
 */
class GoogleDocsRepository {
    private companion object {
        const val DRIVE_BASE = "https://www.googleapis.com/drive/v3"
        const val DOCS_BASE = "https://docs.googleapis.com/v1"
        const val DOC_MIME = "application/vnd.google-apps.document"
    }

    fun createDocument(accessToken: String, name: String, folderId: String? = null): String {
        val body = JSONObject()
            .put("name", name)
            .put("mimeType", DOC_MIME)

        if (!folderId.isNullOrBlank()) {
            body.put("parents", JSONArray().put(folderId))
        }

        val json = request(
            accessToken = accessToken,
            method = "POST",
            url = "$DRIVE_BASE/files?fields=id,name,parents",
            body = body.toString()
        )
        return JSONObject(json).getString("id")
    }

    fun replaceDocument(accessToken: String, documentId: String, text: String) {
        val document = JSONObject(
            request(
                accessToken = accessToken,
                method = "GET",
                url = "$DOCS_BASE/documents/$documentId"
            )
        )
        val content = document.optJSONObject("body")?.optJSONArray("content") ?: JSONArray()
        var endIndex = 2
        for (i in 0 until content.length()) {
            endIndex = maxOf(endIndex, content.optJSONObject(i)?.optInt("endIndex", 2) ?: 2)
        }

        val requests = JSONArray()
        if (endIndex > 2) {
            requests.put(
                JSONObject().put("deleteContentRange", JSONObject().put("range", JSONObject()
                    .put("startIndex", 1)
                    .put("endIndex", endIndex - 1)))
            )
        }
        if (text.isNotEmpty()) {
            requests.put(
                JSONObject().put("insertText", JSONObject()
                    .put("endOfSegmentLocation", JSONObject())
                    .put("text", text))
            )
        }

        if (requests.length() > 0) {
            request(
                accessToken = accessToken,
                method = "POST",
                url = "$DOCS_BASE/documents/$documentId:batchUpdate",
                body = JSONObject().put("requests", requests).toString()
            )
        }
    }

    fun getDocumentText(accessToken: String, documentId: String): String {
        val json = JSONObject(request(accessToken, "GET", "$DOCS_BASE/documents/$documentId"))
        val body = json.optJSONObject("body") ?: return ""
        val content = body.optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val paragraph = content.optJSONObject(i)?.optJSONObject("paragraph") ?: continue
            val elements = paragraph.optJSONArray("elements") ?: continue
            for (j in 0 until elements.length()) {
                val textRun = elements.optJSONObject(j)?.optJSONObject("textRun") ?: continue
                sb.append(textRun.optString("content", ""))
            }
        }
        // Docs adds trailing \n
        return sb.toString().trimEnd('\n')
    }

    fun findFiles(accessToken: String, namePrefix: String): List<RemoteFile> {
        val escapedName = namePrefix.replace("'", "\\'")
        val query = "trashed = false and mimeType = '$DOC_MIME' and name contains '$escapedName'"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val fields = URLEncoder.encode("files(id,name,modifiedTime,size,parents)", StandardCharsets.UTF_8.toString())
        val json = JSONObject(request(
            accessToken = accessToken,
            method = "GET",
            url = "$DRIVE_BASE/files?q=$encodedQuery&spaces=drive&fields=$fields&pageSize=100"
        ))
        val files = json.optJSONArray("files") ?: JSONArray()
        return buildList {
            for (i in 0 until files.length()) {
                val item = files.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (name.startsWith(namePrefix, ignoreCase = true)) {
                    val parents = item.optJSONArray("parents") ?: JSONArray()
                    add(RemoteFile(
                        id = item.optString("id"),
                        name = name,
                        modifiedTime = item.optString("modifiedTime"),
                        size = item.optLong("size", 0L),
                        parents = buildList {
                            for (p in 0 until parents.length()) add(parents.optString(p))
                        }
                    ))
                }
            }
        }
    }

    private fun request(
        accessToken: String,
        method: String,
        url: String,
        body: String? = null
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = input?.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Google API request failed ($status): $response")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}

data class RemoteFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long,
    val parents: List<String>
)

package com.draftlock.app

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpCredentialsAdapter
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest
import com.google.api.services.docs.v1.model.DeleteContentRangeRequest
import com.google.api.services.docs.v1.model.EndOfSegmentLocation
import com.google.api.services.docs.v1.model.InsertTextRequest
import com.google.api.services.docs.v1.model.Location
import com.google.api.services.docs.v1.model.Request
import com.google.api.services.docs.v1.model.Range
import com.google.api.services.docs.v1.model.Document
import com.google.api.services.docs.v1.model.StructuralElement
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.client.auth.oauth2.Credential

class GoogleDocsRepository {
    private val transport = NetHttpTransport()
    private val json = GsonFactory.getDefaultInstance()

    private fun credential(accessToken: String): Credential = com.google.api.client.auth.oauth2.Credential()
        .setAccessToken(accessToken)

    private fun docs(accessToken: String): Docs = Docs.Builder(transport, json, HttpCredentialsAdapter(credential(accessToken)))
        .setApplicationName("DraftLock")
        .build()

    private fun drive(accessToken: String): Drive = Drive.Builder(transport, json, HttpCredentialsAdapter(credential(accessToken)))
        .setApplicationName("DraftLock")
        .build()

    fun createDocument(accessToken: String, name: String, folderId: String? = null): String {
        val doc = docs(accessToken).documents().create(Document().setTitle(name)).execute()
        if (!folderId.isNullOrBlank()) {
            drive(accessToken).files().update(doc.documentId, null)
                .setAddParents(folderId)
                .setFields("id,parents")
                .execute()
        }
        return doc.documentId
    }

    fun replaceDocument(accessToken: String, documentId: String, text: String) {
        val service = docs(accessToken)
        val document = service.documents().get(documentId).execute()
        val endIndex = findBodyEndIndex(document)
        val requests = mutableListOf<Request>()
        if (endIndex > 2) {
            requests += Request().setDeleteContentRange(
                DeleteContentRangeRequest().setRange(Range().setStartIndex(1).setEndIndex(endIndex - 1))
            )
        }
        if (text.isNotEmpty()) {
            requests += Request().setInsertText(
                InsertTextRequest().setEndOfSegmentLocation(EndOfSegmentLocation()).setText(text)
            )
        }
        if (requests.isNotEmpty()) service.documents().batchUpdate(documentId, BatchUpdateDocumentRequest().setRequests(requests)).execute()
    }

    fun findFiles(accessToken: String, namePrefix: String): List<File> =
        drive(accessToken).files().list()
            .setQ("trashed = false and mimeType = 'application/vnd.google-apps.document'")
            .setSpaces("drive")
            .setFields("files(id,name,modifiedTime,size,parents)")
            .execute().files.filter { it.name?.startsWith(namePrefix, ignoreCase = true) == true }

    private fun findBodyEndIndex(document: Document): Int =
        document.body?.content?.mapNotNull(StructuralElement::getEndIndex)?.maxOrNull() ?: 2
}

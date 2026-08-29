package dev.stan.yotsuba.feature.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.stan.yotsuba.domain.model.ImportSource

/**
 * Turns what a document picker hands back into [ImportSource]s.
 *
 * Enumeration goes through [DocumentsContract] rather than `documentfile`, which the app
 * does not depend on and would not earn its place for two queries.
 */
object ImportPicker {

    /** Individually picked files, in the order the user picked them. */
    fun sourcesFrom(context: Context, uris: List<Uri>): List<ImportSource> =
        uris.mapNotNull { uri ->
            displayNameOf(context, uri)?.let { ImportSource(uri.toString(), it) }
        }

    /**
     * Every readable file directly inside a picked folder, by name. Subfolders are skipped:
     * a thread is a flat list of files, and recursing would silently import a whole tree.
     */
    fun sourcesFromTree(context: Context, tree: Uri): List<ImportSource> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val found = mutableListOf<ImportSource>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mime = cursor.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                found += ImportSource(documentUri.toString(), cursor.getString(1))
            }
        }
        return found.sortedBy { it.displayName }
    }

    /** The folder's own name, for the thread title. */
    fun treeName(context: Context, tree: Uri): String? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        return displayNameOf(context, documentUri)
    }

    private fun displayNameOf(context: Context, uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
}

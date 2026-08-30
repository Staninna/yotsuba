package dev.stan.yotsuba.core.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.domain.model.VaultPaths
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a file into the device gallery, under `Pictures/Yotsuba` or `Movies/Yotsuba`.
 * The vault itself is `.nomedia`, so this is how a saved file gets to other apps.
 */
@Singleton
class GalleryExporter @Inject constructor(@ApplicationContext private val context: Context) {

    /** Throws on failure; a MediaStore row that could not be filled is deleted again. */
    fun export(file: File, mime: String) {
        val video = mime.startsWith("video/")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (video) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) +
                        "/" + VaultPaths.ROOT_DIR_NAME,
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused ${file.name}")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("cannot open $uri")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                ),
                VaultPaths.ROOT_DIR_NAME,
            ).apply { mkdirs() }
            val target = generateSequence(0) { it + 1 }
                .map { File(dir, VaultPaths.dedupedFileName(file.name, it)) }
                .first { !it.exists() }
            file.copyTo(target)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        }
    }
}

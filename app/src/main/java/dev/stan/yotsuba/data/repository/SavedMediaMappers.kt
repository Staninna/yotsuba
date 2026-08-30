package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import java.io.File

/** A fresh save (or migration match) filed under its thread. */
fun savedMediaEntity(
    item: MediaItem,
    board: String,
    threadNo: Long,
    subject: String?,
    target: File,
    savedAt: Long,
    thumbnailPath: String? = null,
    durationMs: Long? = null,
): SavedMediaEntity = SavedMediaEntity(
    url = item.fullUrl,
    board = board,
    threadNo = threadNo,
    postNo = item.postNo,
    subject = subject,
    displayName = target.name,
    absolutePath = target.absolutePath,
    ext = item.ext,
    sizeBytes = item.sizeBytes,
    width = item.width,
    height = item.height,
    thumbnailUrl = item.thumbnailUrl,
    savedAt = savedAt,
    thumbnailPath = thumbnailPath,
    durationMs = durationMs,
)

/** A row rebuilt from a meta.json sidecar during rescan. */
fun savedMediaEntity(meta: VaultThreadMeta, f: VaultFileMeta, file: File): SavedMediaEntity =
    SavedMediaEntity(
        // Unsorted migration leftovers have no CDN URL; key them by path.
        url = f.url ?: "file://${file.absolutePath}",
        board = meta.board,
        threadNo = meta.threadNo,
        postNo = f.postNo,
        subject = meta.subject,
        displayName = f.fileName,
        absolutePath = file.absolutePath,
        ext = f.ext,
        sizeBytes = f.sizeBytes ?: file.length(),
        width = f.width,
        height = f.height,
        thumbnailUrl = f.thumbnailUrl,
        savedAt = f.savedAtMillis ?: file.lastModified(),
        thumbnailPath = VideoStills.stillFor(file).takeIf { it.isFile }?.absolutePath,
        durationMs = f.durationMs,
    )

/**
 * Rebuilt rows with the md5/phash/pixelSize their [previous] rows had. A sidecar carries no
 * hash, so a rescan would otherwise wipe every one and dedup would start from scratch. Path
 * first; URL for a file whose directory moved under it (merge, rename), whose path is new.
 */
fun List<SavedMediaEntity>.withHashesFrom(previous: List<SavedMediaEntity>): List<SavedMediaEntity> {
    if (previous.isEmpty()) return this
    val byPath = previous.filter { it.absolutePath.isNotEmpty() }.associateBy { it.absolutePath }
    val byUrl = previous.associateBy { it.url }
    return map { row ->
        val old = byPath[row.absolutePath] ?: byUrl[row.url] ?: return@map row
        row.copy(md5 = old.md5, phash = old.phash, pixelSize = old.pixelSize)
    }
}

/** A migrated legacy file no thread could be matched for, filed under `_unsorted/`. */
fun unsortedSavedMediaEntity(target: File, savedAt: Long): SavedMediaEntity = SavedMediaEntity(
    url = "file://${target.absolutePath}",
    board = null,
    threadNo = null,
    postNo = null,
    subject = null,
    displayName = target.name,
    absolutePath = target.absolutePath,
    ext = ".${target.extension}",
    sizeBytes = target.length(),
    width = null,
    height = null,
    thumbnailUrl = null,
    savedAt = savedAt,
)

/** A legacy URL whose file was never located; still counts as "already saved". */
fun urlOnlySavedMediaEntity(url: String, downloadedAt: Long): SavedMediaEntity = SavedMediaEntity(
    url = url,
    board = VaultPaths.parseMediaUrl(url)?.board,
    threadNo = null,
    postNo = null,
    subject = null,
    displayName = url.substringAfterLast('/'),
    absolutePath = "",
    ext = VaultPaths.parseMediaUrl(url)?.ext,
    sizeBytes = null,
    width = null,
    height = null,
    thumbnailUrl = null,
    savedAt = downloadedAt,
)

fun SavedMediaEntity.toVaultEntry(): VaultEntry = VaultEntry(
    url = url,
    location = location(),
    subject = subject,
    postNo = postNo,
    displayName = displayName,
    absolutePath = absolutePath,
    ext = ext,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    thumbnailUrl = thumbnailUrl,
    savedAt = savedAt,
    localThumbnailPath = thumbnailPath,
    durationMs = durationMs,
)

/** Legacy rows keep the unsorted marker in their columns; the domain has one value for it. */
private fun SavedMediaEntity.location(): VaultLocation {
    val b = board ?: return VaultLocation.Unsorted
    val t = threadNo ?: return VaultLocation.Unsorted
    return VaultLocation(b, t).takeUnless { it.isUnsorted } ?: VaultLocation.Unsorted
}

package dev.stan.yotsuba.core.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Sidecar `meta.json` written into every thread directory (and `_unsorted/`), so the vault
 * stays self-describing without the app: board, thread, and per-file post metadata.
 */
@Serializable
data class VaultThreadMeta(
    val board: String,
    val threadNo: Long? = null,
    val subject: String? = null,
    val threadUrl: String? = null,
    val files: List<VaultFileMeta> = emptyList(),
    /** When posts.json was compacted to the saved conversations, unix millis. Set once, never merged into again. */
    val prunedAt: Long? = null,
    /** How many posts the compaction dropped. */
    val prunedPostCount: Int? = null,
) {
    /** A pruned thread is final: sync never widens it again. */
    val isPruned: Boolean get() = prunedAt != null

    /** Replaces any entry with the same [VaultFileMeta.fileName], keeps order stable. */
    fun upsert(entry: VaultFileMeta): VaultThreadMeta {
        val kept = files.filterNot { it.fileName == entry.fileName }
        return copy(files = kept + entry)
    }

    fun remove(fileName: String): VaultThreadMeta = copy(files = files.filterNot { it.fileName == fileName })
}

@Serializable
data class VaultFileMeta(
    /** Name of the media file next to this meta.json. */
    val fileName: String,
    val postNo: Long? = null,
    val tim: Long? = null,
    val originalFilename: String? = null,
    val ext: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val spoiler: Boolean = false,
    val posterName: String? = null,
    val tripcode: String? = null,
    /** Post timestamp, unix seconds. */
    val postedAtSeconds: Long? = null,
    /** Plain-text post body (markup already stripped by the post parser). */
    val postText: String? = null,
    /** When the user saved the file, unix millis. */
    val savedAtMillis: Long? = null,
    /** Video length, millis; read off the file when its still was captured. */
    val durationMs: Long? = null,
)

object VaultMetaCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = false
    }

    fun encode(meta: VaultThreadMeta): String = json.encodeToString(meta)

    fun decode(text: String): VaultThreadMeta? = runCatching {
        json.decodeFromString<VaultThreadMeta>(text)
    }.getOrNull()
}

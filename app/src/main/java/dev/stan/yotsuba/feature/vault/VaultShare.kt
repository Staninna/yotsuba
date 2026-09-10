package dev.stan.yotsuba.feature.vault

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.stan.yotsuba.core.media.mimeOf
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.feature.media.shareMediaFile
import java.io.File

/**
 * Shares one or many saved files through the system sheet; one file keeps the single-item flow.
 * Files a rescan found gone are dropped: this is the only route from the vault to FileProvider.
 */
fun shareVaultEntries(context: Context, entries: List<VaultEntry>) {
    // A missing row has no path, and FileProvider throws on one while building the intent.
    val present = entries.filterNot { it.missing }
    val single = present.singleOrNull()
    if (single != null) {
        shareMediaFile(context, File(single.absolutePath), single.ext.orEmpty())
        return
    }
    if (present.isEmpty()) return
    val uris = present.map {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(it.absolutePath))
    }
    val mimes = present.map { mimeOf(it.ext.orEmpty()) }.toSet()
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = when {
            mimes.size == 1 -> mimes.first()
            mimes.all { it.startsWith("image/") } -> "image/*"
            mimes.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

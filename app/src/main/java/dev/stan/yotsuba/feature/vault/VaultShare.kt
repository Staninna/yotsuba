package dev.stan.yotsuba.feature.vault

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.feature.media.mimeOf
import dev.stan.yotsuba.feature.media.shareMediaFile
import java.io.File

/** Shares one or many saved files through the system sheet; one file keeps the single-item flow. */
fun shareVaultEntries(context: Context, entries: List<VaultEntry>) {
    val single = entries.singleOrNull()
    if (single != null) {
        shareMediaFile(context, File(single.absolutePath), single.ext.orEmpty())
        return
    }
    if (entries.isEmpty()) return
    val uris = entries.map {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(it.absolutePath))
    }
    val mimes = entries.map { mimeOf(it.ext.orEmpty()) }.toSet()
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

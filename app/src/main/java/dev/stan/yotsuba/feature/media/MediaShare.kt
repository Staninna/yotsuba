package dev.stan.yotsuba.feature.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import dev.stan.yotsuba.core.media.mimeOf
import java.io.File

/** Opens the system "All files access" toggle for this app so the vault becomes writable. */
fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT < 30) return
    val intent = Intent(
        AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Fires a share chooser over plain [text]; a device with nothing to share to is not a crash. */
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/**
 * Fires a share chooser over [file] through the app's FileProvider. False when the chooser
 * could not be started, so the caller can say so instead of leaving a dead tap.
 */
fun shareMediaFile(context: Context, file: File, ext: String): Boolean {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(ext)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { context.startActivity(Intent.createChooser(intent, null)) }.isSuccess
}

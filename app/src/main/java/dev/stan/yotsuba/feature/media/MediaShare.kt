package dev.stan.yotsuba.feature.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import dev.stan.yotsuba.core.media.mimeOf
import java.io.File

/**
 * Opens the system "All files access" toggle for this app so the vault becomes writable:
 * the per-app page first, the global list when a skin lacks it. False when neither exists,
 * or below API 30 where there is nothing to grant.
 */
fun requestAllFilesAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 30) return false
    val perApp = Intent(
        AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val global = Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return context.startExternal(perApp) || context.startExternal(global)
}

/**
 * Opens [url] in whatever handles it, usually the browser. The user picked this from an
 * explicit menu naming the site, so there is no "leave the app?" confirmation. False when
 * nothing on the device could take it.
 */
fun openInBrowser(context: Context, url: String): Boolean =
    context.startExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

/** Fires a share chooser over plain [text]. False when no app on the device could take it. */
fun shareText(context: Context, text: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return context.startExternal(Intent.createChooser(intent, null))
}

/**
 * Fires a share chooser over [file] through the app's FileProvider. False when the chooser
 * could not be started, so the caller can say so instead of leaving a dead tap.
 */
fun shareMediaFile(context: Context, file: File, ext: String): Boolean =
    context.startExternal(Intent.createChooser(sendIntent(context, file, ext), null))

/**
 * Starts [intent] in another app. Only "nothing handles this" is an expected answer and
 * becomes false; any other failure is a bug and is left to surface as one.
 */
private fun Context.startExternal(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

/** Apps that take a shared image straight into Google Lens, the standalone one first. */
private val LENS_PACKAGES = listOf("com.google.ar.lens", "com.google.android.googlequicksearchbox")

/**
 * Hands [file] to Google Lens as a shared image, the one engine that accepts a picture
 * without a URL. With no Lens-capable app installed it falls back to the share chooser,
 * so the tap still leads somewhere. False when not even that could start.
 */
fun searchFileWithLens(context: Context, file: File, ext: String): Boolean {
    for (pkg in LENS_PACKAGES) {
        val intent = sendIntent(context, file, ext).setPackage(pkg)
        if (context.startExternal(intent)) return true
    }
    return shareMediaFile(context, file, ext)
}

private fun sendIntent(context: Context, file: File, ext: String): Intent {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(ext)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * The one cache directory for files handed to other apps: downloads made for the share
 * button and frames cut out of a video. Everything in it is disposable, and [trim] keeps
 * it to the newest [LIMIT] files so a long session does not fill the phone.
 */
object ShareCache {
    const val DIR_NAME = "shared_media"
    const val LIMIT = 20

    fun dir(context: Context): File = File(context.cacheDir, DIR_NAME).apply { mkdirs() }

    /** Keeps the newest [LIMIT] files; [keep] always survives, whatever its timestamp. */
    fun trim(dir: File, keep: File) {
        dir.listFiles { f -> f.isFile }
            ?.sortedByDescending { if (it == keep) Long.MAX_VALUE else it.lastModified() }
            ?.drop(LIMIT)
            ?.forEach { it.delete() }
    }

    /** Writes [bitmap] as a JPEG named [name] into the cache and trims around it. Null on failure. */
    fun writeJpeg(context: Context, bitmap: Bitmap, name: String): File? = runCatching {
        val dir = dir(context)
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        trim(dir, keep = file)
        file
    }.getOrNull()

    /** The cache name for the frame of [video] at [timeMs]: `clip.webm` at 1.5 s is `clip-1500ms.jpg`. */
    fun frameFileName(video: File, timeMs: Long): String =
        "${video.nameWithoutExtension}-${timeMs.coerceAtLeast(0)}ms.jpg"
}

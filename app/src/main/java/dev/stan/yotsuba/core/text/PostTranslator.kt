package dev.stan.yotsuba.core.text

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation into the device language, the only place that sees ML Kit. The
 * source language is detected per text; a model missing on this device downloads first,
 * announced through [onDownloading] so the caller can say so. Throws on anything it
 * cannot do: an unknown source language, a device language ML Kit lacks, a failed download.
 */
class PostTranslator @Inject constructor() {

    suspend fun translate(text: String, onDownloading: () -> Unit): String {
        val target = TranslateLanguage.fromLanguageTag(Locale.getDefault().language)
            ?: throw IllegalStateException("device language is not translatable")
        val source = LanguageIdentification.getClient().use { it.identifyLanguage(text).await() }
            .let(TranslateLanguage::fromLanguageTag)
            ?: throw IllegalStateException("source language unknown")
        if (source == target) return text
        val manager = RemoteModelManager.getInstance()
        val missing = listOf(source, target).filter { lang ->
            !manager.isModelDownloaded(TranslateRemoteModel.Builder(lang).build()).await()
        }
        if (missing.isNotEmpty()) onDownloading()
        val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        return Translation.getClient(options).use { translator ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            translator.translate(text).await()
        }
    }
}

/** ponytail: the one Task we await; the play-services coroutine artifact is not worth a dependency for it. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

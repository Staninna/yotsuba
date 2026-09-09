package dev.stan.yotsuba.core.media

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device text recognition over an image file, the one place ML Kit is imported. Latin
 * script only, which covers what the boards this app reads write in.
 */
object ImageText {
    /**
     * Every line ML Kit found in [file], blocks separated by a blank line. Null when the
     * file will not decode, the model fails, or the image holds no text at all: to the
     * user those are one and the same "nothing here".
     */
    suspend fun recognise(context: Context, file: File): String? {
        val image = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }.getOrNull() ?: return null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.textBlocks.joinToString("\n\n") { block -> block.text }) }
                    .addOnFailureListener { cont.resume(null) }
            }?.takeIf { it.isNotBlank() }
        } finally {
            recognizer.close()
        }
    }
}

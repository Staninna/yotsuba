package dev.stan.yotsuba.feature.media

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.BuildConfig
import dev.stan.yotsuba.core.media.mimeOf
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response

/**
 * Where the uploads go. Overridable so tests can point every host at a MockWebServer;
 * production uses the defaults.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReverseSearchUploadModule {
    /** Production upload targets; tests construct the uploader with their own. */
    @Provides
    fun uploadEndpoints(): UploadEndpoints = UploadEndpoints()
}

data class UploadEndpoints(
    val tineye: String = "https://tineye.com/api/v1/result_json/",
    val tineyeResults: String = "https://tineye.com/search/",
    val yandexUpload: String = "https://yandex.com/images-apphost/image-download" +
        "?images_avatars_size=preview&images_avatars_namespace=images-cbir",
    val yandexResults: String = "https://yandex.com/images/search?rpt=imageview&cbir_id=",
    val litterbox: String = "https://litterbox.catbox.moe/resources/internals/api.php",
    val zeroXZero: String = "https://0x0.st",
)

/**
 * Whether the engine's own upload form answers with something that yields a shareable
 * results URL. The others give back an HTML results page with nothing to hand the
 * browser, so the temporary host is their only route.
 */
val ReverseSearchEngine.hasDirectUpload: Boolean
    get() = this == ReverseSearchEngine.TINEYE || this == ReverseSearchEngine.YANDEX

/** Where one local search stands; the sheet renders it. */
sealed interface LocalSearchState {
    data object Idle : LocalSearchState
    data class Uploading(val engine: ReverseSearchEngine) : LocalSearchState
    /** [url] is the results page; the screen opens it and resets to [Idle]. */
    data class Opened(val engine: ReverseSearchEngine, val url: String) : LocalSearchState
    /** [canFallback] when the temporary host is still worth offering. */
    data class Failed(val engine: ReverseSearchEngine, val canFallback: Boolean) : LocalSearchState
}

/**
 * Puts a local-only file where a reverse search engine can see it. Two routes: a multipart
 * POST to the engine's own upload form ([directSearchUrl]), or an upload to a temporary
 * host whose URL then goes through the ordinary [ReverseSearchEngine.searchUrl] path
 * ([hostTemporarily]). Both return the URL to open, never a body to render.
 */
class ReverseSearchUploader @Inject constructor(
    client: OkHttpClient,
    private val endpoints: UploadEndpoints,
) {
    // The DI client's rate limiter only fires for a.4cdn.org, so sharing its pool is safe.
    private val client = client.newBuilder()
        .callTimeout(30.seconds.toJavaDuration())
        .build()

    /**
     * Uploads [file] to [engine]'s own form and returns the results page URL. Only engines
     * with [hasDirectUpload]; anything else fails fast so the caller falls back to the host.
     */
    suspend fun directSearchUrl(engine: ReverseSearchEngine, file: File, ext: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (engine) {
                    ReverseSearchEngine.TINEYE -> tineyeSearch(file, ext)
                    ReverseSearchEngine.YANDEX -> yandexSearch(file, ext)
                    else -> throw IOException("${engine.label} has no direct upload")
                }
            }
        }

    /**
     * Uploads [file] to litterbox (kept one hour), falling back to 0x0.st, and returns the
     * hosted image URL for the ordinary by-URL search path.
     */
    suspend fun hostTemporarily(file: File, ext: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { litterboxUpload(file, ext) }.recoverCatching { zeroXZeroUpload(file, ext) }
    }

    private suspend fun tineyeSearch(file: File, ext: String): String {
        // The site's own form: multipart to result_json, results page keyed by query_hash.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody(mimeOf(ext).toMediaType()))
            .build()
        client.newCall(Request.Builder().url(endpoints.tineye).post(body).build()).await().use { r ->
            if (!r.isSuccessful) throw IOException("TinEye answered ${r.code}")
            val root = Json.parseToJsonElement(r.body.string()).jsonObject
            val hash = root["query_hash"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: throw IOException("TinEye reply carried no query_hash")
            return endpoints.tineyeResults + URLEncoder.encode(hash, "UTF-8")
        }
    }

    private suspend fun yandexSearch(file: File, ext: String): String {
        // The endpoint rejects multipart with "Incorrect avatar size"; it wants the bytes raw.
        val body = file.asRequestBody(mimeOf(ext).toMediaType())
        client.newCall(Request.Builder().url(endpoints.yandexUpload).post(body).build()).await().use { r ->
            if (!r.isSuccessful) throw IOException("Yandex answered ${r.code}")
            val root = Json.parseToJsonElement(r.body.string()).jsonObject
            val cbirId = root["cbir_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: throw IOException("Yandex reply carried no cbir_id")
            return endpoints.yandexResults + URLEncoder.encode(cbirId, "UTF-8")
        }
    }

    private suspend fun litterboxUpload(file: File, ext: String): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("time", "1h")
            .addFormDataPart("fileToUpload", file.name, file.asRequestBody(mimeOf(ext).toMediaType()))
            .build()
        client.newCall(Request.Builder().url(endpoints.litterbox).post(body).build()).await().use { r ->
            val reply = r.body.string().trim()
            if (!r.isSuccessful || !reply.startsWith("https://")) {
                throw IOException("litterbox answered ${r.code}: ${reply.take(120)}")
            }
            return reply
        }
    }

    private suspend fun zeroXZeroUpload(file: File, ext: String): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mimeOf(ext).toMediaType()))
            .build()
        val request = Request.Builder().url(endpoints.zeroXZero).post(body)
            // 0x0.st blocks the default OkHttp agent; hours until the file expires.
            .header("User-Agent", "Yotsuba/" + BuildConfig.VERSION_NAME)
            .header("X-Expires", "24")
            .build()
        client.newCall(request).await().use { r ->
            val reply = r.body.string().trim()
            if (!r.isSuccessful || !reply.startsWith("https://")) {
                throw IOException("0x0.st answered ${r.code}: ${reply.take(120)}")
            }
            return reply
        }
    }
}

/** Executes [Call], cancelling it if the coroutine dies first. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resumeWith(Result.success(response))
        override fun onFailure(call: Call, e: IOException) = cont.resumeWith(Result.failure(e))
    })
    cont.invokeOnCancellation { cancel() }
}

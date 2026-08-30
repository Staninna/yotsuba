package dev.stan.yotsuba.data.repository

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.BuildConfig
import dev.stan.yotsuba.di.IoDispatcher
import dev.stan.yotsuba.core.log.Log
import dev.stan.yotsuba.core.media.mimeOf
import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.repository.DirectUploadEngine
import dev.stan.yotsuba.domain.repository.HostedFile
import dev.stan.yotsuba.domain.repository.ReverseSearchRepository
import dev.stan.yotsuba.domain.repository.TemporaryHost
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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

/** Where the uploads go. Tests point every host at a MockWebServer; production uses the defaults. */
@Module
@InstallIn(SingletonComponent::class)
object ReverseSearchEndpointsModule {
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
    val uguu: String = "https://uguu.se/upload",
    val zeroXZero: String = "https://0x0.st",
)

/**
 * Multipart uploads to TinEye, Yandex, litterbox and 0x0.st. None of them is 4chan, so per
 * ADR-0003 this is its own client, like the updater's: the shared one's cookie jar, JSON
 * cache and offline interceptor have no business on these hosts.
 */
@Singleton
class ReverseSearchRepositoryImpl internal constructor(
    private val io: CoroutineDispatcher,
    private val endpoints: UploadEndpoints,
    private val client: OkHttpClient,
) : ReverseSearchRepository {

    @Inject constructor(@IoDispatcher io: CoroutineDispatcher, endpoints: UploadEndpoints) : this(io, endpoints, ownClient())

    override suspend fun directSearchUrl(engine: DirectUploadEngine, file: File, ext: String): DataResult<String> =
        withContext(io) {
            apiResult {
                when (engine) {
                    DirectUploadEngine.TINEYE -> tineyeSearch(file, ext)
                    DirectUploadEngine.YANDEX -> yandexSearch(file, ext)
                }
            }
        }

    /**
     * Hosts come and go: litterbox started answering 412 to every upload and 0x0.st switched
     * uploads off on the same day (2026-08-31). Each one is tried in turn and its refusal
     * logged, so the next outage names itself in logcat instead of hiding behind the last host's.
     */
    override suspend fun hostTemporarily(file: File, ext: String): DataResult<HostedFile> = withContext(io) {
        apiResult {
            var last: IOException? = null
            for (host in TemporaryHost.entries) {
                try {
                    return@apiResult HostedFile(uploadTo(host, file, ext), host)
                } catch (e: IOException) {
                    Log.w(TAG, "temporary host $host refused the file", e)
                    last = e
                }
            }
            throw last ?: IOException("no temporary host configured")
        }
    }

    private suspend fun uploadTo(host: TemporaryHost, file: File, ext: String): String = when (host) {
        TemporaryHost.LITTERBOX -> litterboxUpload(file, ext)
        TemporaryHost.UGUU -> uguuUpload(file, ext)
        TemporaryHost.ZERO_X_ZERO -> zeroXZeroUpload(file, ext)
    }

    private suspend fun tineyeSearch(file: File, ext: String): String {
        // The site's own form: multipart to result_json, results page keyed by query_hash.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody(mimeOf(ext).toMediaType()))
            .build()
        client.newCall(Request.Builder().url(endpoints.tineye).post(body).build()).await().use { r ->
            if (!r.isSuccessful) throw IOException("TinEye answered ${r.code}")
            val hash = jsonField(r.body.string(), "query_hash")
                ?: throw IOException("TinEye reply carried no query_hash")
            return endpoints.tineyeResults + URLEncoder.encode(hash, "UTF-8")
        }
    }

    private suspend fun yandexSearch(file: File, ext: String): String {
        // The endpoint rejects multipart with "Incorrect avatar size"; it wants the bytes raw.
        val body = file.asRequestBody(mimeOf(ext).toMediaType())
        client.newCall(Request.Builder().url(endpoints.yandexUpload).post(body).build()).await().use { r ->
            if (!r.isSuccessful) throw IOException("Yandex answered ${r.code}")
            val cbirId = jsonField(r.body.string(), "cbir_id")
                ?: throw IOException("Yandex reply carried no cbir_id")
            return endpoints.yandexResults + URLEncoder.encode(cbirId, "UTF-8")
        }
    }

    /** A top-level string field, or null when the body is not JSON or lacks it. */
    private fun jsonField(body: String, name: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

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

    private suspend fun uguuUpload(file: File, ext: String): String {
        // Files are kept three hours. The reply is JSON: {"success":true,"files":[{"url":...}]}.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("files[]", file.name, file.asRequestBody(mimeOf(ext).toMediaType()))
            .build()
        client.newCall(Request.Builder().url(endpoints.uguu).post(body).build()).await().use { r ->
            val reply = r.body.string()
            val url = runCatching {
                Json.parseToJsonElement(reply).jsonObject["files"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("url")?.jsonPrimitive?.content
            }.getOrNull()
            if (!r.isSuccessful || url == null || !url.startsWith("https://")) {
                throw IOException("uguu answered ${r.code}: ${reply.take(120)}")
            }
            return url
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

private const val TAG = "ReverseSearch"

private fun ownClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

/** Executes [Call], cancelling it if the coroutine dies first. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resumeWith(Result.success(response))
        override fun onFailure(call: Call, e: IOException) = cont.resumeWith(Result.failure(e))
    })
    cont.invokeOnCancellation { cancel() }
}

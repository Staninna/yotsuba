package dev.stan.yotsuba.core.update

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** The published release, reduced to what an updater needs. */
data class Release(
    val tag: String,
    val notes: String,
    /** Where to GET the APK. For a private repo this is the asset API URL. */
    val apkUrl: String,
    val sizeBytes: Long,
    /** True when [apkUrl] is the API endpoint, which needs the octet-stream Accept header. */
    val viaApi: Boolean,
)

class ReleaseException(message: String) : Exception(message)

@Serializable
private data class ReleaseJson(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    val assets: List<AssetJson> = emptyList(),
)

@Serializable
private data class AssetJson(
    val name: String = "",
    val size: Long = 0,
    val url: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

/**
 * Reads the newest release of Yotsuba's own repo.
 *
 * The repo is private today and may be public later, so the token is optional
 * everywhere: without one this works against a public repo, with one it works
 * against either. The token is something the user pastes into Settings — it is
 * never compiled into the app, because anything shipped in an APK is readable
 * by anyone holding the APK.
 */
@Singleton
class GithubReleases @Inject constructor() {

    // Deliberately not the app-wide client: that one carries a 4chan rate
    // limiter and a JSON cache, neither of which should touch api.github.com.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun latest(token: String): Release = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .authorize(token)
            .build()
        val body = client.newCall(req).execute().use { it.readOrThrow() }
        parse(body, private = token.isNotBlank())
    }

    /** Opens the APK stream; the caller owns closing the response. */
    fun openApk(release: Release, token: String): Response {
        val req = Request.Builder()
            .url(release.apkUrl)
            .apply { if (release.viaApi) header("Accept", "application/octet-stream") }
            .authorize(token)
            .build()
        return client.newCall(req).execute()
    }

    private fun Request.Builder.authorize(token: String) = apply {
        if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}")
    }

    private fun Response.readOrThrow(): String = when {
        code == 401 || code == 403 ->
            throw ReleaseException("GitHub refused the request (${code}). Check the update token.")
        code == 404 ->
            throw ReleaseException("No release found. A private repo needs a token with read access.")
        !isSuccessful -> throw ReleaseException("GitHub said $code.")
        else -> body!!.string()
    }

    companion object {
        const val REPO = "Staninna/yotsuba"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * [private] picks the download URL: browser_download_url is public and
         * unauthenticated, while a private repo's asset only comes back from
         * the API endpoint with a token attached.
         */
        fun parse(body: String, private: Boolean): Release {
            val release = try {
                json.decodeFromString<ReleaseJson>(body)
            } catch (e: Exception) {
                throw ReleaseException("Couldn't read GitHub's answer.")
            }
            if (release.tagName.isBlank()) throw ReleaseException("That release has no tag.")
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw ReleaseException("Release ${release.tagName} has no APK attached.")
            val viaApi = private && apk.url.isNotBlank()
            return Release(
                tag = release.tagName,
                notes = release.body.trim(),
                apkUrl = if (viaApi) apk.url else apk.browserDownloadUrl,
                sizeBytes = apk.size,
                viaApi = viaApi,
            )
        }
    }
}

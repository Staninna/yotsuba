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
    val apkUrl: String,
    val sizeBytes: Long,
)

/** One published version as the changelog list shows it; an APK is not required here. */
data class ReleaseEntry(
    val tag: String,
    val notes: String,
    val publishedAt: String,
)

class ReleaseException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class ReleaseJson(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<AssetJson> = emptyList(),
)

@Serializable
private data class AssetJson(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

/**
 * Reads the newest release of Yotsuba's own repo. The repo is public, so this
 * is unauthenticated: no credential to ship, none to leak, and GitHub's
 * per-IP rate limit is far above what a button pressed by hand can reach.
 */
@Singleton
class GithubReleases @Inject constructor() {

    // Deliberately not the app-wide client: that one carries a 4chan rate
    // limiter and a JSON cache, neither of which should touch api.github.com.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        parse(client.newCall(req).execute().use { it.readOrThrow() })
    }

    /** Every published release, newest first, for the version history. */
    suspend fun all(): List<ReleaseEntry> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=100")
            .header("Accept", "application/vnd.github+json")
            .build()
        parseAll(client.newCall(req).execute().use { it.readOrThrow() })
    }

    /** Opens the APK stream; the caller owns closing the response. */
    fun openApk(release: Release): Response =
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute()

    private fun Response.readOrThrow(): String = when {
        code == 404 -> throw ReleaseException("No release published yet.")
        code == 403 -> throw ReleaseException("GitHub is rate-limiting us. Try again later.")
        !isSuccessful -> throw ReleaseException("GitHub said $code.")
        else -> body.string()
    }

    companion object {
        const val REPO = "Staninna/yotsuba"

        private val json = Json { ignoreUnknownKeys = true }

        /** Drafts are skipped; the order is GitHub's, which is newest first. */
        fun parseAll(body: String): List<ReleaseEntry> {
            val releases = try {
                json.decodeFromString<List<ReleaseJson>>(body)
            } catch (e: Exception) {
                throw ReleaseException("Couldn't read GitHub's answer.", e)
            }
            return releases
                .filter { !it.draft && it.tagName.isNotBlank() }
                .map { ReleaseEntry(tag = it.tagName, notes = it.body.trim(), publishedAt = it.publishedAt.take(10)) }
        }

        /** Split out from the fetch so the parsing rules are unit-testable. */
        fun parse(body: String): Release {
            val release = try {
                json.decodeFromString<ReleaseJson>(body)
            } catch (e: Exception) {
                throw ReleaseException("Couldn't read GitHub's answer.", e)
            }
            if (release.tagName.isBlank()) throw ReleaseException("That release has no tag.")
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw ReleaseException("Release ${release.tagName} has no APK attached.")
            return Release(
                tag = release.tagName,
                notes = release.body.trim(),
                apkUrl = apk.browserDownloadUrl,
                sizeBytes = apk.size,
            )
        }
    }
}

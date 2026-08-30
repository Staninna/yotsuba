package dev.stan.yotsuba.core.vault

import kotlinx.serialization.json.Json

/**
 * The one JSON policy for the vault's sidecars. meta.json and posts.json sit in the same
 * directory and are read by the same rescan, so they must agree on it: unknown keys are
 * tolerated (older apps read newer files), defaults stay out of the file, and a file that
 * fails to parse reads as absent rather than failing the scan.
 */
internal object SidecarJson {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = false
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> decode(text: String): T? = runCatching { json.decodeFromString<T>(text) }.getOrNull()
}

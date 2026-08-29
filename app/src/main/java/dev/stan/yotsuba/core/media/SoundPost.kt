package dev.stan.yotsuba.core.media

import java.net.URI

/**
 * A "sound post": a 4chan filename of the form `name[sound=host%2Fpath.mp3].webm` whose
 * bracketed tag names an external audio track to play over a silent webm or a still.
 *
 * The convention is loose in the wild -- the URL usually has no scheme, is sometimes
 * percent-encoded twice, and occasionally is not a URL at all. [parse] keeps the name
 * either way and only yields a [SoundPost.url] it would be safe to hand to a player:
 * an https URL with a real host and nothing odd in it.
 */
data class SoundPost(
    /** The filename with the `[sound=...]` tag removed. */
    val name: String,
    /** The external audio URL, or null when the tag is missing or unusable. */
    val url: String?,
) {
    companion object {
        private val TAG = Regex("""\[sound=([^\]]*)]""", RegexOption.IGNORE_CASE)
        private val SCHEME = Regex("""^[a-z][a-z0-9+.-]*://""", RegexOption.IGNORE_CASE)
        private val HOST = Regex("""^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$""", RegexOption.IGNORE_CASE)
        private val PERCENT = Regex("""%[0-9a-fA-F]{2}""")
        private const val MAX_DECODE_PASSES = 3

        fun parse(filename: String): SoundPost {
            val match = TAG.find(filename) ?: return SoundPost(filename, null)
            val name = filename.removeRange(match.range).trim().ifEmpty { filename }
            return SoundPost(name, toUrl(match.groupValues[1]))
        }

        /** Null unless [raw] decodes to a plain https URL. */
        fun toUrl(raw: String): String? {
            var decoded = raw.trim()
            // Double-encoded tags exist; decode until the string stops changing.
            repeat(MAX_DECODE_PASSES) {
                val next = percentDecode(decoded)
                if (next == decoded) return@repeat
                decoded = next
            }
            if (decoded.isEmpty() || decoded.any { it.isWhitespace() || it in "\"'<>\\" }) return null
            val withScheme = when {
                decoded.startsWith("https://", ignoreCase = true) -> decoded
                decoded.startsWith("//") -> "https:" + decoded
                SCHEME.containsMatchIn(decoded) -> return null
                else -> "https://$decoded"
            }
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            if (!HOST.matches(host) || uri.userInfo != null) return null
            return withScheme
        }

        private fun percentDecode(s: String): String =
            PERCENT.replace(s) { m -> m.value.substring(1).toInt(16).toChar().toString() }
                .let { partial ->
                    // Multi-byte UTF-8 sequences come back as one char per byte above;
                    // re-assemble them so a non-ASCII path survives the decode.
                    if (partial.any { it.code in 0x80..0xFF }) {
                        runCatching {
                            String(partial.map { it.code.toByte() }.toByteArray(), Charsets.UTF_8)
                        }.getOrDefault(partial)
                    } else {
                        partial
                    }
                }
    }
}

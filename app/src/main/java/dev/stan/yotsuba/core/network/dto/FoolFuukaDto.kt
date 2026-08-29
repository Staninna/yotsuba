package dev.stan.yotsuba.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * One thread from FoolFuuka's `/_/api/chan/thread/` reply. FoolFuuka quotes most numbers
 * as strings, so decoding runs lenient and the DTO asks for the type it wants.
 */
data class FoolFuukaThreadDto(
    val op: FoolFuukaPostDto,
    val replies: List<FoolFuukaPostDto>,
) {
    val posts: List<FoolFuukaPostDto> get() = listOf(op) + replies
}

@Serializable
data class FoolFuukaPostDto(
    val num: Long,
    val timestamp: Long = 0,
    val op: Int = 0,
    val name: String? = null,
    val trip: String? = null,
    val capcode: String? = null,
    val poster_hash: String? = null,
    val poster_country: String? = null,
    val poster_country_name: String? = null,
    val title: String? = null,
    /** Plain text with `>>123` and `>greentext` still as typed. */
    val comment: String? = null,
    /** FoolFuuka's own HTML rendering; different markup from 4chan's, so [comment] is used. */
    val comment_processed: String? = null,
    val deleted: Int = 0,
    val media: FoolFuukaMediaDto? = null,
)

@Serializable
data class FoolFuukaMediaDto(
    val media_filename: String? = null,
    val media_w: Int = 0,
    val media_h: Int = 0,
    val media_size: Long = 0,
    val media_link: String? = null,
    val remote_media_link: String? = null,
    val thumb_link: String? = null,
    val spoiler: Int = 0,
    val banned: Int = 0,
)

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * The thread inside a FoolFuuka reply, or null when the reply is an error object or carries
 * no thread. The reply is keyed by thread number: `{"123": {"op": {...}, "posts": {...}}}`.
 */
fun parseFoolFuukaThread(reply: JsonObject): FoolFuukaThreadDto? {
    if ((reply["error"] as? JsonPrimitive)?.contentOrNull != null) return null
    val thread = reply.values.firstOrNull { it is JsonObject && "op" in it.jsonObject }?.jsonObject ?: return null
    val op = lenientJson.decodeFromJsonElement(FoolFuukaPostDto.serializer(), thread.getValue("op"))
    val replies = (thread["posts"] as? JsonObject)?.values.orEmpty()
        .map { lenientJson.decodeFromJsonElement(FoolFuukaPostDto.serializer(), it) }
        .sortedBy { it.num }
    return FoolFuukaThreadDto(op, replies)
}

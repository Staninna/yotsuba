package dev.stan.yotsuba.core.network

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * A FoolFuuka archive's thread endpoint. The host varies per board, so the caller passes
 * the full URL from [ArchiveHosts.apiUrl]. Comes back raw: an error reply is
 * `{"error": "..."}` with a 200, which only [dev.stan.yotsuba.core.network.dto.parseFoolFuukaThread]
 * can tell apart from a thread.
 */
interface ArchiveApi {
    @GET
    suspend fun thread(@Url url: String): JsonObject
}

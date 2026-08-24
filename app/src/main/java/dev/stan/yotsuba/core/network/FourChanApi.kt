package dev.stan.yotsuba.core.network

import dev.stan.yotsuba.core.network.dto.BoardsDto
import dev.stan.yotsuba.core.network.dto.CatalogPageDto
import dev.stan.yotsuba.core.network.dto.ThreadDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface FourChanApi {
    @GET("boards.json")
    suspend fun boards(@Header("Cache-Control") cacheControl: String? = null): BoardsDto

    @GET("{board}/catalog.json")
    suspend fun catalog(
        @Path("board") board: String,
        @Header("Cache-Control") cacheControl: String? = null,
    ): List<CatalogPageDto>

    @GET("{board}/thread/{no}.json")
    suspend fun thread(
        @Path("board") board: String,
        @Path("no") no: Long,
        @Header("Cache-Control") cacheControl: String? = null,
    ): ThreadDto
}

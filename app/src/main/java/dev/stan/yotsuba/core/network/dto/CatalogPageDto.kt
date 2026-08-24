package dev.stan.yotsuba.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CatalogPageDto(
    val page: Int,
    val threads: List<PostDto> = emptyList(),
)

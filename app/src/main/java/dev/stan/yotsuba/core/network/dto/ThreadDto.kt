package dev.stan.yotsuba.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ThreadDto(
    val posts: List<PostDto> = emptyList(),
)

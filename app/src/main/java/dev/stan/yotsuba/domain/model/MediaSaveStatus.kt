package dev.stan.yotsuba.domain.model

/** Where a post's media stands with respect to the vault, for thumbnail badges. */
enum class MediaSaveStatus { SAVED, QUEUED, DOWNLOADING, FAILED }

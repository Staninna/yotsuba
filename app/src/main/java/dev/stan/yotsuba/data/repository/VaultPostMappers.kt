package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.vault.VaultPostFile
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.ThreadPost

fun ThreadPost.toVaultMeta(): VaultPostMeta = VaultPostMeta(
    no = no,
    isOp = isOp,
    name = name,
    tripcode = tripcode,
    capcode = capcode,
    posterId = posterId,
    countryCode = countryCode,
    countryName = countryName,
    timeSeconds = timeSeconds,
    subject = subject,
    body = body,
    quotedPostNos = quotedPostNos,
    file = presentMedia?.let {
        VaultPostFile(
            filename = it.filename,
            ext = it.ext,
            url = it.fullUrl,
            thumbnailUrl = it.thumbnailUrl,
            width = it.width,
            height = it.height,
            sizeBytes = it.sizeBytes,
            spoiler = it.spoiler,
        )
    },
)

fun VaultPostMeta.toThreadPost(board: String): ThreadPost = ThreadPost(
    board = board,
    no = no,
    isOp = isOp,
    name = name.orEmpty(),
    tripcode = tripcode,
    capcode = capcode,
    posterId = posterId,
    countryCode = countryCode,
    countryName = countryName,
    timeSeconds = timeSeconds,
    subject = subject,
    body = body,
    media = file?.let {
        PostMedia.Present(
            MediaItem(
                postNo = no,
                filename = it.filename,
                ext = it.ext,
                sizeBytes = it.sizeBytes,
                width = it.width,
                height = it.height,
                thumbnailUrl = it.thumbnailUrl,
                fullUrl = it.url,
                spoiler = it.spoiler,
            ),
        )
    },
    quotedPostNos = quotedPostNos,
)

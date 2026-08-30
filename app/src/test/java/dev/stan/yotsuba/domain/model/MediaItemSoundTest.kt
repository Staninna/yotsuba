package dev.stan.yotsuba.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemSoundTest {

    private fun item(ext: String, soundUrl: String? = null) = MediaItem(
        postNo = 1,
        filename = "clip",
        ext = ext,
        sizeBytes = 1,
        width = 1,
        height = 1,
        thumbnailUrl = "t",
        fullUrl = "f",
        spoiler = false,
        soundUrl = soundUrl,
    )

    @Test
    fun `video with a sound post has sound regardless of board`() {
        assertTrue(item(".webm", soundUrl = "https://a.catbox.moe/x.mp3").mayHaveSound(boardAllowsAudio = false))
    }

    @Test
    fun `video on an audio board may have sound`() {
        assertTrue(item(".webm").mayHaveSound(boardAllowsAudio = true))
    }

    @Test
    fun `video on a silent board without a sound post is silent`() {
        assertFalse(item(".mp4").mayHaveSound(boardAllowsAudio = false))
    }

    @Test
    fun `image never has sound even with a sound url`() {
        assertFalse(item(".jpg", soundUrl = "https://a.catbox.moe/x.mp3").mayHaveSound(boardAllowsAudio = true))
    }
}

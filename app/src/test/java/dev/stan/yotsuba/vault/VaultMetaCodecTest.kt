package dev.stan.yotsuba.vault

import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultMetaCodecTest {

    private val meta = VaultThreadMeta(
        board = "g",
        threadNo = 42,
        subject = "desktop thread",
        threadUrl = "https://boards.4chan.org/g/thread/42",
        files = listOf(
            VaultFileMeta(
                fileName = "cat.jpg", postNo = 43, tim = 1700000000000L,
                originalFilename = "cat", ext = ".jpg",
                url = "https://i.4cdn.org/g/1700000000000.jpg",
                width = 640, height = 480, sizeBytes = 1234,
                spoiler = true, postedAtSeconds = 99, savedAtMillis = 100,
            ),
            VaultFileMeta(fileName = "minimal.png"),
        ),
    )

    @Test fun `encode-decode round-trips`() {
        assertEquals(meta, VaultMetaCodec.decode(VaultMetaCodec.encode(meta)))
    }

    @Test fun `sound fields round-trip and old sidecars without them decode as unprobed`() {
        val probed = VaultFileMeta(fileName = "a.webm", durationMs = 1500, hasAudio = true, soundUrl = "https://x.y/z.mp3")
        val text = VaultMetaCodec.encode(VaultThreadMeta(board = "g", files = listOf(probed)))
        assertEquals(probed, VaultMetaCodec.decode(text)!!.files.single())

        val old = VaultMetaCodec.decode("""{"board":"g","files":[{"fileName":"a.webm","durationMs":1500}]}""")!!
        assertNull(old.files.single().hasAudio)
        assertNull(old.files.single().soundUrl)
    }

    @Test fun `decode ignores unknown keys`() {
        val text = """{"board":"g","future_field":123,"files":[{"fileName":"a.jpg","new_thing":true}]}"""
        assertEquals(
            VaultThreadMeta(board = "g", files = listOf(VaultFileMeta(fileName = "a.jpg"))),
            VaultMetaCodec.decode(text),
        )
    }

    @Test fun `decode of garbage returns null`() {
        assertNull(VaultMetaCodec.decode("not json"))
        assertNull(VaultMetaCodec.decode(""))
        assertNull(VaultMetaCodec.decode("""{"files":[]}""")) // missing required board
    }

    @Test fun `upsert replaces by fileName and moves the entry to the end`() {
        val updated = meta.upsert(VaultFileMeta(fileName = "cat.jpg", postNo = 999))
        assertEquals(listOf("minimal.png", "cat.jpg"), updated.files.map { it.fileName })
        assertEquals(999L, updated.files.last().postNo)

        val added = meta.upsert(VaultFileMeta(fileName = "new.webm"))
        assertEquals(3, added.files.size)
    }

    @Test fun `remove drops by fileName`() {
        assertEquals(listOf("minimal.png"), meta.remove("cat.jpg").files.map { it.fileName })
        assertEquals(meta, meta.remove("absent.gif"))
    }
}

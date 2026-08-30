package dev.stan.yotsuba.feature.vault

import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * VaultStatsSheet.kt:69 passes a [VaultLocation] as a LazyColumn item key. Compose hands every
 * item key to SaveableStateHolderImpl.SaveableStateProvider, which does
 * `require(parentSaveableStateRegistry?.canBeSaved(key) ?: true)`. On Android the parent registry
 * is DisposableSaveableStateRegistry, whose predicate is `canBeSavedToBundle`. This test calls
 * that exact predicate.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class VaultStatsKeyTest {

    private val canBeSavedToBundle = Class
        .forName("androidx.compose.ui.platform.DisposableSaveableStateRegistry_androidKt")
        .getDeclaredMethod("canBeSavedToBundle", Any::class.java)
        .apply { isAccessible = true }

    private fun canBeSaved(value: Any): Boolean = canBeSavedToBundle.invoke(null, value) as Boolean

    private fun entry(url: String, location: VaultLocation) = VaultEntry(
        url = url,
        location = location,
        subject = "subject",
        postNo = 1L,
        displayName = "1.jpg",
        absolutePath = "/sdcard/Yotsuba/g/1.jpg",
        ext = ".jpg",
        sizeBytes = 100L,
        width = 10,
        height = 10,
        thumbnailUrl = null,
        savedAt = 0L,
    )

    private val stats = VaultStats.of(
        listOf(entry("https://i.4cdn.org/g/1.jpg", VaultLocation("g", 12345L))),
        now = 0L,
    )

    @Test
    fun boardKeyIsFine() {
        assertEquals("g", stats.perBoard.first().board)
        assertTrue(canBeSaved(stats.perBoard.first().board))
    }

    @Test
    fun threadKeyCannotBeSaved() {
        assertFalse(canBeSaved(stats.biggestThreads.first().location))
    }

    @Test
    fun composeRequireOnTheThreadKeyThrows() {
        val key: Any = stats.biggestThreads.first().location
        val thrown = runCatching {
            // Verbatim from SaveableStateHolderImpl.SaveableStateProvider.
            require(canBeSaved(key)) {
                "Type of the key $key is not supported. On Android you can only use types " +
                    "which can be stored inside the Bundle."
            }
        }.exceptionOrNull()
        thrown!!.printStackTrace()
        assertEquals(IllegalArgumentException::class.java, thrown.javaClass)
    }
}

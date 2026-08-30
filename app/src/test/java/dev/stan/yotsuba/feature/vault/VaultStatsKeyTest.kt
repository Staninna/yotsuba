package dev.stan.yotsuba.feature.vault

import dev.stan.yotsuba.domain.model.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose hands every LazyColumn item key to SaveableStateHolderImpl.SaveableStateProvider,
 * which does `require(parentSaveableStateRegistry?.canBeSaved(key) ?: true)`. On Android that
 * predicate is DisposableSaveableStateRegistry's `canBeSavedToBundle`. The stats sheet once
 * keyed its thread rows on a raw [VaultLocation], which that predicate rejects, so the sheet
 * crashed as soon as a populated vault's "Biggest threads" section composed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultStatsKeyTest {

    private val canBeSavedToBundle = Class
        .forName("androidx.compose.ui.platform.DisposableSaveableStateRegistry_androidKt")
        .getDeclaredMethod("canBeSavedToBundle", Any::class.java)
        .apply { isAccessible = true }

    private fun canBeSaved(value: Any): Boolean = canBeSavedToBundle.invoke(null, value) as Boolean

    private val location = VaultLocation("g", 12345L)

    @Test
    fun `a raw location is not a legal item key`() {
        assertFalse(canBeSaved(location))
    }

    @Test
    fun `the sheet's thread key is Bundle-safe and unique per location`() {
        assertTrue(canBeSaved(lazyKey(location)))
        assertEquals("g/12345", lazyKey(location))
        assertTrue(lazyKey(location) != lazyKey(VaultLocation("g", 12346L)))
        assertTrue(lazyKey(location) != lazyKey(VaultLocation("a", 12345L)))
    }
}

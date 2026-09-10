package dev.stan.yotsuba.media

import androidx.compose.ui.test.assertIsNotEnabled
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.LocalSearchMethod
import dev.stan.yotsuba.domain.repository.DirectUploadEngine
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class ReverseSearchFlowTest : FlowTest() {

    private val engines = listOf("Google Lens", "SauceNAO", "IQDB", "TinEye", "Yandex")
    private val uploadNote = "only exists on this phone"
    private val fileName = TestSeed.mediaItem.displayName

    /** A saved file whose only copy is on this phone, so every engine needs an upload. */
    private fun seedVaultOnlyFile() {
        fakes.vault.seedLocalCopy(TestSeed.mediaItem, url = "file:///vault/$fileName")
    }

    /** Opens the sheet over the vault-only file, from the Saved tab's own viewer. */
    private fun openVaultSearchSheet() {
        composeRule.openVaultViewer(fileName)
        composeRule.openViewerMenu("Search image")
        composeRule.waitForText("Search with")
    }

    private fun confirmUpload(engine: String = "TinEye") {
        openVaultSearchSheet()
        composeRule.tap(engine, substring = false)
        composeRule.waitForText("Don't ask again")
    }

    @Test
    fun threadImage_listsEveryEngine_withoutTheUploadNote() {
        composeRule.openSeededImage()
        composeRule.openViewerMenu("Search image")
        composeRule.waitForText("Search with")
        engines.forEach { assertTrue(it, composeRule.hasText(it, substring = false)) }
        assertFalse(composeRule.hasText(uploadNote))
        // Nothing on disk yet, so there is no file to hand another app.
        composeRule.nodeWithText("Share to another app").assertIsNotEnabled()
    }

    @Test
    fun vaultOnlyFile_explainsThatEnginesNeedAnUpload() {
        seedVaultOnlyFile()
        openVaultSearchSheet()
        composeRule.waitForText(uploadNote)
        engines.forEach { assertTrue(it, composeRule.hasText(it, substring = false)) }
    }

    @Test
    fun engineTap_asksFirst_andCancelUploadsNothing() {
        seedVaultOnlyFile()
        confirmUpload()
        composeRule.waitForText("Upload to TinEye?")
        composeRule.tap("Cancel", substring = false)
        composeRule.waitForText("Search with")
        assertTrue(fakes.reverseSearch.directCalls.isEmpty())
    }

    @Test
    fun upload_withDirectUploadSetting_goesToTheEnginesOwnForm() {
        seedVaultOnlyFile()
        confirmUpload()
        composeRule.tap("Upload", substring = false)
        composeRule.waitUntilTrue { fakes.reverseSearch.directCalls == listOf(DirectUploadEngine.TINEYE) }
        assertTrue(fakes.reverseSearch.hostCalls == 0)
    }

    @Test
    fun upload_withTemporaryHostSetting_goesToTheHost() {
        seedVaultOnlyFile()
        fakes.settings.set { it.copy(localSearchMethod = LocalSearchMethod.TEMP_HOST) }
        confirmUpload()
        composeRule.waitForText("Upload to a temporary host?")
        composeRule.tap("Upload", substring = false)
        composeRule.waitUntilTrue { fakes.reverseSearch.hostCalls == 1 }
        assertTrue(fakes.reverseSearch.directCalls.isEmpty())
    }

    @Test
    fun failedUpload_saysSo_andOffersTheTemporaryHost() {
        seedVaultOnlyFile()
        fakes.reverseSearch.failAll()
        confirmUpload()
        composeRule.tap("Upload", substring = false)
        composeRule.waitForText("Could not upload to TinEye")
        composeRule.tap("Try the temporary host")
        composeRule.waitForText("Upload to a temporary host?")
    }

    @Test
    fun dontAskAgain_turnsTheConfirmationOff() {
        seedVaultOnlyFile()
        confirmUpload()
        composeRule.tap("Don't ask again")
        composeRule.tap("Upload", substring = false)
        composeRule.waitUntilTrue { !fakes.settings.state.value.confirmTemporaryHost }
    }
}

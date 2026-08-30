package dev.stan.yotsuba.feature.media

import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.LocalSearchMethod
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.DirectUploadEngine
import dev.stan.yotsuba.domain.repository.HostedFile
import dev.stan.yotsuba.domain.repository.ReverseSearchRepository
import dev.stan.yotsuba.domain.repository.TemporaryHost
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.fake.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReverseSearchViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val file = File("frame.jpg")

    /** Records every call; answers with a fixed URL so the state machine can be followed. */
    private class RecordingUploads : ReverseSearchRepository {
        val directCalls = mutableListOf<DirectUploadEngine>()
        var hostCalls = 0
        var fail = false

        override suspend fun directSearchUrl(engine: DirectUploadEngine, file: File, ext: String): DataResult<String> {
            directCalls += engine
            return if (fail) DataResult.Failure(NetworkError.Unknown()) else DataResult.Success("https://results/$engine")
        }

        override suspend fun hostTemporarily(file: File, ext: String): DataResult<HostedFile> {
            hostCalls++
            return DataResult.Success(HostedFile("https://litter.catbox.moe/a.jpg", TemporaryHost.LITTERBOX))
        }
    }

    private fun viewModel(settings: Settings, uploads: RecordingUploads = RecordingUploads()) =
        ReverseSearchViewModel(uploads, FakeSettings(settings))

    @Test fun `the direct route stops at the prompt while confirmation is on`() = runTest {
        val uploads = RecordingUploads()
        val vm = viewModel(Settings(localSearchMethod = LocalSearchMethod.DIRECT_UPLOAD, confirmTemporaryHost = true), uploads)
        vm.search(ReverseSearchEngine.TINEYE, file, ".jpg")
        advanceUntilIdle()
        assertEquals(LocalSearchState.ConfirmUpload(ReverseSearchEngine.TINEYE, direct = true), vm.state.value)
        assertEquals(emptyList<DirectUploadEngine>(), uploads.directCalls)
        assertEquals(0, uploads.hostCalls)
    }

    @Test fun `the host route names the host in its prompt`() = runTest {
        val vm = viewModel(Settings(confirmTemporaryHost = true))
        vm.search(ReverseSearchEngine.SAUCENAO, file, ".jpg")
        advanceUntilIdle()
        assertEquals(LocalSearchState.ConfirmUpload(ReverseSearchEngine.SAUCENAO, direct = false), vm.state.value)
    }

    @Test fun `confirming a direct prompt uploads to the engine's form`() = runTest {
        val uploads = RecordingUploads()
        val vm = viewModel(Settings(confirmTemporaryHost = true), uploads)
        vm.search(ReverseSearchEngine.TINEYE, file, ".jpg", confirmed = true)
        advanceUntilIdle()
        assertEquals(listOf(DirectUploadEngine.TINEYE), uploads.directCalls)
        assertEquals(0, uploads.hostCalls)
        assertEquals(LocalSearchState.Opened(ReverseSearchEngine.TINEYE, "https://results/TINEYE"), vm.state.value)
    }

    @Test fun `with the prompt off a direct engine uploads on the tap`() = runTest {
        val uploads = RecordingUploads()
        val vm = viewModel(Settings(confirmTemporaryHost = false), uploads)
        vm.search(ReverseSearchEngine.YANDEX, file, ".jpg")
        advanceUntilIdle()
        assertEquals(listOf(DirectUploadEngine.YANDEX), uploads.directCalls)
    }

    @Test fun `the host retry after a failed direct upload still asks first`() = runTest {
        val uploads = RecordingUploads().apply { fail = true }
        val vm = viewModel(Settings(confirmTemporaryHost = true), uploads)
        vm.search(ReverseSearchEngine.TINEYE, file, ".jpg", confirmed = true)
        advanceUntilIdle()
        assertEquals(LocalSearchState.Failed(ReverseSearchEngine.TINEYE, canFallback = true), vm.state.value)

        vm.search(ReverseSearchEngine.TINEYE, file, ".jpg", forceHost = true)
        advanceUntilIdle()
        assertEquals(LocalSearchState.ConfirmUpload(ReverseSearchEngine.TINEYE, direct = false), vm.state.value)
        assertEquals(0, uploads.hostCalls)

        vm.search(ReverseSearchEngine.TINEYE, file, ".jpg", confirmed = true, forceHost = true)
        advanceUntilIdle()
        assertEquals(1, uploads.hostCalls)
        assertEquals(
            LocalSearchState.Opened(ReverseSearchEngine.TINEYE, ReverseSearchEngine.TINEYE.searchUrl("https://litter.catbox.moe/a.jpg")),
            vm.state.value,
        )
    }

    @Test fun `temp host setting routes a direct engine through the host`() = runTest {
        val uploads = RecordingUploads()
        val vm = viewModel(Settings(localSearchMethod = LocalSearchMethod.TEMP_HOST, confirmTemporaryHost = false), uploads)
        vm.search(ReverseSearchEngine.TINEYE, file, ".jpg")
        advanceUntilIdle()
        assertEquals(0, uploads.directCalls.size)
        assertEquals(1, uploads.hostCalls)
    }
}

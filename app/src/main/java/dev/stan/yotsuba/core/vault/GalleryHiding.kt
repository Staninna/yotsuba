package dev.stan.yotsuba.core.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.di.IoDispatcher
import dev.stan.yotsuba.domain.model.VaultPaths
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Whether the vault is kept out of the phone's gallery. The state is the `.nomedia` marker
 * at the vault root and nothing else: it is read from disk every time it is asked for, so
 * it stays right after a reinstall or a change made from a file manager.
 */
interface GalleryHiding {
    /** True when the marker exists; null when the app has no storage access. */
    suspend fun isHidden(): Boolean?

    /** Creates or removes the marker and nudges the media scanner; returns the new state. */
    suspend fun setHidden(hidden: Boolean): Result<Boolean>
}

@Singleton
class NoMediaMarker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: MediaVaultRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : GalleryHiding {

    // Same root VaultStore uses; this class cannot import the data layer to ask it.
    private val root: File
        get() = File(Environment.getExternalStorageDirectory(), VaultPaths.ROOT_DIR_NAME)

    private val marker: File get() = File(root, VaultPaths.NOMEDIA_FILE_NAME)

    override suspend fun isHidden(): Boolean? = withContext(io) {
        if (!vault.hasStorageAccess()) null else marker.exists()
    }

    override suspend fun setHidden(hidden: Boolean): Result<Boolean> = withContext(io) {
        if (!vault.hasStorageAccess()) return@withContext Result.failure(IOException("no storage access"))
        runCatching {
            if (hidden) {
                root.mkdirs()
                if (!marker.exists() && !marker.createNewFile()) throw IOException("could not create ${marker.name}")
            } else if (marker.exists() && !marker.delete()) {
                throw IOException("could not delete ${marker.name}")
            }
            // The gallery only re-reads a folder it is told about.
            MediaScannerConnection.scanFile(context, arrayOf(root.absolutePath), null, null)
            marker.exists()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GalleryHidingModule {
    @Binds abstract fun galleryHiding(impl: NoMediaMarker): GalleryHiding
}

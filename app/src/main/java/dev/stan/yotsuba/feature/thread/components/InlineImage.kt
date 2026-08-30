package dev.stan.yotsuba.feature.thread.components

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.feature.media.LoadPill
import dev.stan.yotsuba.feature.media.defersHeavyMedia
import dev.stan.yotsuba.feature.thread.InlineImage
import java.io.File

/**
 * The full image shown inside a post card in place of its thumbnail. The box takes the
 * image's own aspect ratio up front, so the list does not jump when the bytes arrive; the
 * cached thumbnail sits underneath until then. With data saver on a metered connection the
 * thumbnail stays and a "Load (N MB)" pill fetches on request.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineImage(
    media: MediaItem,
    source: InlineImage,
    contentDescription: String,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Once tapped the image stays loaded for this card's lifetime; the bytes are spent.
    var loadRequested by remember(media.fullUrl) { mutableStateOf(false) }
    val deferred = source.dataSaver && !loadRequested && defersHeavyMedia(true, rememberNetworkStatus())
    val ratio = if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 1f
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = MAX_HEIGHT)
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = media.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (deferred) {
            LoadPill(sizeBytes = media.sizeBytes, onClick = { loadRequested = true })
        } else {
            AsyncImage(
                model = source.localPath?.let { File(it) } ?: media.fullUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A very tall image is capped rather than filling ten screens; Fit keeps it whole inside. */
private val MAX_HEIGHT = 600.dp

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface InlineImageEntryPoint {
    fun networkMonitor(): NetworkMonitor
}

/** The live connection, only asked for when data saver could defer the load. */
@Composable
private fun rememberNetworkStatus(): NetworkStatus {
    val context: Context = LocalContext.current.applicationContext
    val monitor = remember(context) {
        EntryPointAccessors.fromApplication(context, InlineImageEntryPoint::class.java).networkMonitor()
    }
    val status by monitor.status.collectAsState(initial = monitor.current())
    return status
}

package dev.stan.yotsuba.feature.thread

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.feature.media.saveToVault
import kotlinx.coroutines.launch

/** Runs a vault save, or asks for storage access first and says so; see [rememberSaveToVault]. */
fun interface SaveToVault {
    operator fun invoke(save: () -> Unit)
}

/**
 * The thread screen's one way to save into the vault: every save site gates on storage
 * access the same way and answers a missing grant with the same snackbar, so that answer
 * is captured once here. Remembered, so it can key the action objects that hold it.
 */
@Composable
fun rememberSaveToVault(snackbar: SnackbarHostState, hasAccess: () -> Boolean): SaveToVault {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val grantAccessMessage = stringResource(R.string.media_grant_storage)
    // Read at save time, not captured: a fresh lambda per recomposition must not remake the action.
    val currentHasAccess by rememberUpdatedState(hasAccess)
    return remember(context, scope, snackbar, grantAccessMessage) {
        SaveToVault { save ->
            saveToVault(
                context = context,
                hasAccess = currentHasAccess(),
                onAccessNeeded = { scope.launch { snackbar.showSnackbar(grantAccessMessage) } },
                save = save,
            )
        }
    }
}

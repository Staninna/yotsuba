package dev.stan.yotsuba.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import kotlinx.coroutines.launch

/**
 * The Recent segment's share of a host app bar: the search toggle, the clear-all button
 * and the confirmation dialog behind it. The host owns the [androidx.compose.material3.TopAppBar]
 * and decides when to show these; the open/closed state survives rotation here.
 */
@Stable
class HistoryTopBar internal constructor(
    private val viewModel: HistoryViewModel,
    private val snackbar: SnackbarHostState,
    searching: MutableState<Boolean>,
    confirmClear: MutableState<Boolean>,
) {
    /** True while the search field should replace the host's title. */
    var searching by searching
        private set
    private var confirmClear by confirmClear

    /** Title slot: the search field while [searching], otherwise the host's [fallback]. */
    @Composable
    fun Title(state: HistoryUiState, fallback: @Composable () -> Unit) {
        if (searching) {
            HistorySearchField(query = state.query, onQueryChange = viewModel::onQueryChange)
        } else {
            fallback()
        }
    }

    /** Search toggle and clear-all, both disabled while there is nothing to search or clear. */
    @Composable
    fun Actions(state: HistoryUiState) {
        IconButton(
            onClick = {
                if (searching) viewModel.onQueryChange("")
                searching = !searching
            },
            enabled = searching || state.totalCount > 0,
        ) {
            Icon(
                if (searching) Icons.Filled.Close else Icons.Filled.Search,
                stringResource(if (searching) R.string.history_search_close else R.string.history_search),
            )
        }
        IconButton(onClick = { confirmClear = true }, enabled = state.totalCount > 0) {
            Icon(Icons.Filled.DeleteSweep, stringResource(R.string.action_clear_all))
        }
    }

    /** The clear-all confirmation; call once from the host, outside the app bar. */
    @Composable
    fun Dialogs() {
        if (!confirmClear) return
        val scope = rememberCoroutineScope()
        val clearedMessage = stringResource(R.string.history_cleared)
        HistoryClearDialog(
            onConfirm = {
                confirmClear = false
                viewModel.onClearAll()
                scope.launch { snackbar.showSnackbar(clearedMessage) }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun rememberHistoryTopBar(viewModel: HistoryViewModel, snackbar: SnackbarHostState): HistoryTopBar {
    val searching = rememberSaveable { mutableStateOf(false) }
    val confirmClear = rememberSaveable { mutableStateOf(false) }
    return remember(viewModel, snackbar) { HistoryTopBar(viewModel, snackbar, searching, confirmClear) }
}

package dev.stan.yotsuba.feature.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.CatalogLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    board: String,
    initialSearch: String?,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    viewModel: CatalogViewModel = catalogViewModel(board, initialSearch),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val boardInfo by viewModel.boardInfo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(boardInfo?.title ?: board)
                        val filteredCount = (state as? UiState.Success)?.data?.filteredCount ?: 0
                        Text(
                            if (filteredCount == 0) "/$board/"
                            else "/$board/ \u00b7 " + pluralStringResource(R.plurals.filters_filtered_count, filteredCount, filteredCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = { CatalogActions(state, viewModel) },
            )
        },
    ) { padding ->
        CatalogPane(
            board = board,
            viewModel = viewModel,
            onOpenThread = onOpenThread,
            snackbar = snackbar,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

/** Search toggle and layout cycler for a catalog top bar; shared with the Home tab. */
@Composable
fun CatalogActions(state: UiState<CatalogContent>, viewModel: CatalogViewModel) {
    val content = (state as? UiState.Success)?.data
    val searchOpen = content?.searchQuery != null
    IconButton(onClick = { if (searchOpen) viewModel.onCloseSearch() else viewModel.onOpenSearch() }) {
        Icon(
            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
            stringResource(R.string.action_search),
        )
    }
    IconButton(onClick = viewModel::onCycleLayout) {
        Icon(
            when (content?.layout) {
                CatalogLayout.COMPACT -> Icons.Filled.GridView
                CatalogLayout.LIST -> Icons.AutoMirrored.Filled.ViewList
                CatalogLayout.COMFORTABLE, null -> Icons.Filled.ViewAgenda
            },
            stringResource(R.string.catalog_layout_switch),
        )
    }
}

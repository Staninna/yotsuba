package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogThread

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Error(val error: NetworkError) : CatalogUiState
    data class Success(
        val board: Board?,
        val threads: List<CatalogThread>,
        val layout: CatalogLayout,
        val searchQuery: String,
        val refreshing: Boolean,
        val offline: Boolean,
    ) : CatalogUiState
}

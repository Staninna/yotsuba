package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A settings repository backed by a plain state flow. Several ViewModel tests declare
 * their own private copy of this; new tests should use this one.
 */
class FakeSettings(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings) {
        state.value = transform(state.value)
    }
}

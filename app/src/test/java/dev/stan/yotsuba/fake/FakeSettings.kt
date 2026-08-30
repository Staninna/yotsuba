package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A settings repository backed by a plain state flow; [writes] counts every [update]. */
class FakeSettings(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    var writes = 0
    override val settings: Flow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings) {
        writes++
        state.value = transform(state.value)
    }
}

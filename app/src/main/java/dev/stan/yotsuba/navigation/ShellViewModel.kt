package dev.stan.yotsuba.navigation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.Urls.InternalLink
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Activity-level state the shell needs: the link an intent asked us to open, and whether
 * asking for notification permission would be worth the interruption. Lives in a
 * ViewModel so a rotation between the intent arriving and navigation consuming it does
 * not lose the target.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    bookmarkRepository: BookmarkRepository,
) : ViewModel() {
    /** True once there is something a notification could be about and the user wants them. */
    val wantsNotifications: StateFlow<Boolean> = combine(
        settingsRepository.settings,
        bookmarkRepository.bookmarks,
    ) { settings, bookmarks -> settings.bookmarkNotifications && bookmarks.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _pendingLink = MutableStateFlow<InternalLink?>(null)
    val pendingLink: StateFlow<InternalLink?> = _pendingLink

    /** Parks the intent's target, if it has one; a plain launch is a no-op. */
    fun onIntent(intent: Intent?) {
        ExternalLinks.fromIntent(intent)?.let { _pendingLink.value = it }
    }

    fun linkConsumed() {
        _pendingLink.value = null
    }
}

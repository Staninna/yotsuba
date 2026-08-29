package dev.stan.yotsuba.navigation

import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.Urls.InternalLink
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Activity-level state the shell needs: the link an intent asked us to open. Lives in a
 * ViewModel so a rotation between the intent arriving and navigation consuming it does
 * not lose the target.
 */
@HiltViewModel
class ShellViewModel @Inject constructor() : ViewModel() {
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

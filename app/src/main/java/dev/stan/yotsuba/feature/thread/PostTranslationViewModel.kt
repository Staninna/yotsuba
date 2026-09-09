package dev.stan.yotsuba.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.text.PostTranslator
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One post's translation as the card draws it. */
sealed interface PostTranslation {
    data class Working(val downloading: Boolean) : PostTranslation
    data class Done(val text: String) : PostTranslation
    data object Failed : PostTranslation
}

/**
 * Translations shown under posts, kept for as long as the screen that asked lives and no
 * longer: nothing here persists. Scoped to the thread's navigation entry, so the action
 * sheet and every card on the screen read the same map.
 */
@HiltViewModel
class PostTranslationViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val translator: PostTranslator,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settingsRepository.settings.map { it.translatePosts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _translations = MutableStateFlow<Map<Long, PostTranslation>>(emptyMap())
    val translations: StateFlow<Map<Long, PostTranslation>> = _translations

    fun translate(post: ThreadPost) {
        if (_translations.value[post.no] is PostTranslation.Working) return
        set(post.no, PostTranslation.Working(downloading = false))
        viewModelScope.launch {
            val result = try {
                PostTranslation.Done(
                    translator.translate(post.body.plainText) { set(post.no, PostTranslation.Working(downloading = true)) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PostTranslation.Failed
            }
            set(post.no, result)
        }
    }

    fun hide(postNo: Long) = _translations.update { it - postNo }

    private fun set(postNo: Long, state: PostTranslation) = _translations.update { it + (postNo to state) }
}

package dev.stan.yotsuba.core.lock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.stan.yotsuba.di.ApplicationScope
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether the app is behind the lock screen right now.
 *
 * Attached to the process lifecycle by [dev.stan.yotsuba.YotsubaApplication]: leaving the app
 * notes the time, coming back locks when "lock again after" has run out (or right away when
 * it is 0). A fresh process with the lock on starts locked. Nothing here talks to the
 * biometric stack; [unlock] is what the prompt calls on success.
 *
 * [now] is a monotonic millisecond clock so a clock change while backgrounded cannot skip
 * the delay; tests hand in their own.
 */
@Singleton
class AppLock(
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    private val now: () -> Long,
) : DefaultLifecycleObserver {

    @Inject constructor(
        settingsRepository: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(settingsRepository, scope, SystemClock::elapsedRealtime)

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /**
     * False until the settings have been read once. The activity shows nothing until then,
     * so a locked app never flashes its content while the preference loads.
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile private var lockOn = false
    @Volatile private var delayMillis = 0L
    @Volatile private var stoppedAt: Long? = null

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                lockOn = settings.appLock
                delayMillis = settings.appLockDelaySeconds * 1000L
                if (!_ready.value) {
                    // Fresh process: locked exactly when the setting is on.
                    _locked.value = settings.appLock
                    _ready.value = true
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) = onAppStart()
    override fun onStop(owner: LifecycleOwner) = onAppStop()

    /** ON_STOP of the whole process: every activity is off screen. */
    fun onAppStop() {
        stoppedAt = now()
    }

    /** ON_START of the whole process; a no-op before the first stop, which [ready] covers. */
    fun onAppStart() {
        val since = stoppedAt ?: return
        if (!lockOn) return
        if (delayMillis == 0L || now() - since >= delayMillis) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }
}

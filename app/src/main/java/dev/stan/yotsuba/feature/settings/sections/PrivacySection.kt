package dev.stan.yotsuba.feature.settings.sections

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.OnResumeEffect
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.lock.DeviceUnlock
import dev.stan.yotsuba.core.lock.UnlockResult
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.LocalSearchMethod
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.feature.settings.appLockDelayLabel
import dev.stan.yotsuba.feature.settings.labelRes

/** The "lock again after" choices, in seconds; 0 is "right away". */
private val AppLockDelays = listOf(0, 30, 60, 300)

@Composable
fun PrivacySection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    // Re-checked on every resume: the user may have just set a screen lock in system settings.
    var canLock by remember { mutableStateOf(DeviceUnlock.available(context)) }
    OnResumeEffect { canLock = DeviceUnlock.available(context) }
    val confirmTitle = stringResource(R.string.settings_app_lock_confirm_title)
    val notConfirmed = stringResource(R.string.settings_app_lock_not_confirmed)

    SwitchRow(
        title = stringResource(R.string.settings_app_lock),
        summary = stringResource(
            if (canLock) R.string.settings_app_lock_summary else R.string.settings_app_lock_unavailable,
        ),
        checked = settings.appLock,
        enabled = canLock,
        onToggle = { on ->
            if (!on || activity == null) {
                update { it.copy(appLock = on) }
            } else {
                // Turning it on means passing the prompt once, so nobody locks themselves out
                // behind an unlock they cannot pass.
                DeviceUnlock.prompt(activity, confirmTitle) { result ->
                    if (result == UnlockResult.PASSED) update { it.copy(appLock = true) } else showMessage(notConfirmed)
                }
            }
        },
    )
    if (settings.appLock) {
        ChipRow(
            label = stringResource(R.string.settings_app_lock_delay),
            options = (AppLockDelays + settings.appLockDelaySeconds).distinct().sorted(),
            selected = settings.appLockDelaySeconds,
            onSelect = { v -> update { it.copy(appLockDelaySeconds = v) } },
            labelOf = { appLockDelayLabel(it) },
        )
    }

    ChipRow(
        label = stringResource(R.string.settings_local_search),
        options = LocalSearchMethod.entries,
        selected = settings.localSearchMethod,
        onSelect = { v -> update { it.copy(localSearchMethod = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
    Text(
        stringResource(R.string.settings_local_search_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.xs),
    )
}

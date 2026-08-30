package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.BuildConfig
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.OnResumeEffect
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.update.Release
import dev.stan.yotsuba.core.update.ReleaseEntry
import dev.stan.yotsuba.core.update.Updater

/**
 * The Settings "Updates" block: one button that asks GitHub for the newest
 * release, and, only when there is a newer one, a second that fetches that
 * APK and installs it. Nothing here happens without a tap.
 */
@Composable
fun UpdatesSection(
    state: Updater.State,
    history: Updater.History,
    onLoadHistory: () -> Unit,
    onCheck: () -> Unit,
    onInstall: (Release) -> Unit,
    canInstallPackages: () -> Boolean,
    onRequestInstallPermission: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var needsPermission by remember { mutableStateOf(false) }

    // A dev build cannot update itself: the release APK has a different
    // package id, so installing it would add a second app rather than replace
    // this one. Saying "you're up to date" here would be true only by accident
    // -- Version.parse rejects the "-dev" suffix, so the check always returns
    // UpToDate regardless of what has been released.
    LaunchedEffect(Unit) { onLoadHistory() }

    if (BuildConfig.DEBUG) {
        Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs)) {
            Text(
                stringResource(R.string.settings_update_dev_build, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VersionHistory(history, onLoadHistory)
        }
        return
    }

    // Coming back from the system "install unknown apps" screen with the permission granted
    // must clear the hint; nothing else re-checks it until the next tap.
    OnResumeEffect { if (canInstallPackages()) needsPermission = false }

    val busy = state is Updater.State.Checking ||
        state is Updater.State.Downloading ||
        state is Updater.State.Installing

    Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs)) {
        Button(
            onClick = onCheck,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (state is Updater.State.Checking) R.string.settings_update_checking
                    else R.string.settings_update_check,
                ),
            )
        }

        when (state) {
            is Updater.State.UpToDate ->
                Body(stringResource(R.string.settings_update_none, state.version))

            is Updater.State.Failed ->
                Body(state.message, error = true)

            is Updater.State.Downloading -> {
                val total = state.totalBytes.takeIf { it > 0 }
                Body(
                    if (total != null)
                        stringResource(R.string.settings_update_downloading, state.downloadedBytes.mb(), total.mb())
                    else stringResource(R.string.settings_update_downloading_unknown, state.downloadedBytes.mb()),
                )
                if (total != null) {
                    LinearProgressIndicator(
                        progress = { (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Updater.State.Installing -> Body(stringResource(R.string.settings_update_installing))

            is Updater.State.Available -> {
                var notesOpen by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(vertical = spacing.sm)) {
                    Column(Modifier.padding(spacing.lg)) {
                        Text(
                            stringResource(R.string.settings_update_available, state.release.tag),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (state.release.notes.isNotBlank()) {
                            Text(
                                stringResource(
                                    if (notesOpen) R.string.settings_update_notes_close
                                    else R.string.settings_update_notes_open,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = spacing.sm)
                                    .clickable { notesOpen = !notesOpen },
                            )
                            if (notesOpen) ReleaseNotesView(state.release.notes)
                        }
                        Button(
                            onClick = {
                                if (!canInstallPackages()) {
                                    needsPermission = true
                                    onRequestInstallPermission()
                                } else {
                                    needsPermission = false
                                    onInstall(state.release)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.md),
                        ) { Text(stringResource(R.string.settings_update_install)) }
                    }
                }
                if (needsPermission) Body(stringResource(R.string.settings_update_permission))
            }

            Updater.State.Idle, Updater.State.Checking -> Unit
        }

        VersionHistory(history, onLoadHistory)
    }
}

/** Every release's notes, newest first, one collapsible card per version; the newest starts open. */
@Composable
private fun VersionHistory(history: Updater.History, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Text(
        stringResource(R.string.settings_update_history),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = spacing.lg, bottom = spacing.xs),
    )
    when (history) {
        Updater.History.Idle, Updater.History.Loading -> Body(stringResource(R.string.settings_update_history_loading))
        is Updater.History.Failed -> {
            Body(history.message, error = true)
            Text(
                stringResource(R.string.settings_update_history_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = spacing.xs).clickable(onClick = onRetry),
            )
        }
        is Updater.History.Loaded -> {
            if (history.entries.isEmpty()) Body(stringResource(R.string.settings_update_history_empty))
            history.entries.forEachIndexed { index, entry ->
                VersionCard(entry, initiallyOpen = index == 0)
            }
        }
    }
}

@Composable
private fun VersionCard(entry: ReleaseEntry, initiallyOpen: Boolean) {
    val spacing = LocalSpacing.current
    var open by rememberSaveable(entry.tag) { mutableStateOf(initiallyOpen) }
    val installed = entry.tag.removePrefix("v") == BuildConfig.VERSION_NAME.removeSuffix("-dev")
    Card(Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (installed) stringResource(R.string.settings_update_history_installed, entry.tag) else entry.tag,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (entry.publishedAt.isNotBlank()) {
                        Text(
                            entry.publishedAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (open) R.string.settings_update_notes_close else R.string.settings_update_notes_open,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(open) {
                if (entry.notes.isBlank()) Body(stringResource(R.string.settings_update_history_no_notes))
                else ReleaseNotesView(entry.notes)
            }
        }
    }
}

@Composable
private fun Body(text: String, error: Boolean = false) {
    val spacing = LocalSpacing.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = spacing.sm),
    )
}

/** Bytes as a one-decimal megabyte string, without pulling in a formatter. */
private fun Long.mb(): String = "%.1f".format(this / 1_048_576.0)

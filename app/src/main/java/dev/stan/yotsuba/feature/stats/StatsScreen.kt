package dev.stan.yotsuba.feature.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.core.util.TimeFormat
import java.time.format.TextStyle

/** Plain rows of numbers about this install. Everything here is read from the phone's own tables. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val u = s.usage
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.sm),
        ) {
            Text(
                stringResource(R.string.stats_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
            Header(R.string.stats_reading)
            StatRow(R.string.stats_threads_read, u.threadsRead.toString())
            StatRow(R.string.stats_posts_read, u.postsRead.toString())
            StatRow(R.string.stats_in_history, s.historyCount.toString())
            StatRow(R.string.stats_watching, s.bookmarkCount.toString())
            StatRow(R.string.stats_watch_added, u.bookmarksAdded.toString())
            if (u.boardsByVisits.isNotEmpty()) {
                Header(R.string.stats_boards)
                u.boardsByVisits.forEach { (board, visits) ->
                    StatRow(label = "/$board/", value = visits.toString())
                }
            }
            Header(R.string.stats_saving)
            StatRow(R.string.stats_images_saved, u.imagesSaved.toString())
            StatRow(R.string.stats_videos_saved, u.videosSaved.toString())
            StatRow(R.string.stats_bytes_saved, FileSize.format(u.bytesSaved))
            StatRow(R.string.stats_vault_now, stringResource(R.string.stats_vault_detail, s.vaultFiles, FileSize.format(s.vaultBytes)))
            Header(R.string.stats_habits)
            u.busiestHour?.let { StatRow(R.string.stats_busiest_hour, "%02d:00".format(it)) }
            u.busiestDay?.let { StatRow(R.string.stats_busiest_day, it.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)) }
            StatRow(R.string.stats_longest_streak, stringResource(R.string.stats_days, u.longestStreak))
            u.firstUseAt?.let { StatRow(R.string.stats_first_use, TimeFormat.date(it)) }
            Header(R.string.stats_rescues)
            StatRow(R.string.stats_archive_rescues, u.archiveRescues.toString())
            StatRow(R.string.stats_searches, u.searchesRun.toString())
        }
    }
}

@Composable
private fun Header(labelRes: Int) {
    val spacing = LocalSpacing.current
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, top = spacing.lg, bottom = spacing.xs),
    )
}

@Composable
private fun StatRow(labelRes: Int, value: String) = StatRow(stringResource(labelRes), value)

@Composable
private fun StatRow(label: String, value: String) {
    val spacing = LocalSpacing.current
    Row(Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

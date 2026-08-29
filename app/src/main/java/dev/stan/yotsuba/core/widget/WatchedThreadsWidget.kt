package dev.stan.yotsuba.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.MainActivity
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.work.BookmarkRefreshWorker
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.map

/**
 * Home-screen list of watched threads, unread first. Reads straight from
 * [BookmarkRepository.bookmarks] through a Hilt entry point, so any change to the table
 * re-renders the widget while a Glance session is alive.
 */
class WatchedThreadsWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun bookmarkRepository(): BookmarkRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors.fromApplication(context, Deps::class.java).bookmarkRepository()
        val rows = repository.bookmarks.map(::orderForWidget)
        provideContent {
            val list by rows.collectAsState(initial = emptyList())
            GlanceTheme {
                WidgetContent(list)
            }
        }
    }

}

class WatchedThreadsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchedThreadsWidget()
}

/** Header refresh: one immediate run of the same worker the 30 minute schedule uses. */
class RefreshBookmarksAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BookmarkRefreshWorker>().build(),
        )
    }

    companion object {
        const val UNIQUE_NAME = "bookmark-refresh-now"
    }
}

private val boardKey = ActionParameters.Key<String>(WidgetDeepLink.EXTRA_BOARD)
private val threadKey = ActionParameters.Key<Long>(WidgetDeepLink.EXTRA_THREAD_NO)

@Composable
private fun WidgetContent(rows: List<WidgetRow>) {
    val context = LocalContext.current
    val visible = rows.take(6)
    val unreadTotal = rows.sumOf { it.unread }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Header(context, unreadTotal)
        visible.forEach { ThreadRow(context, it) }
    }
}

@Composable
private fun Header(context: Context, unreadTotal: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = context.getString(R.string.widget_unread_total, unreadTotal),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = context.getString(R.string.widget_refresh),
            style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Medium, fontSize = 12.sp),
            modifier = GlanceModifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clickable(actionRunCallback<RefreshBookmarksAction>()),
        )
    }
}

@Composable
private fun ThreadRow(context: Context, row: WidgetRow) {
    val color = if (row.dead) GlanceTheme.colors.outline else GlanceTheme.colors.onSurface
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(boardKey to row.board, threadKey to row.threadNo),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "/${row.board}/",
            style = TextStyle(color = color, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = row.title,
            maxLines = 1,
            style = TextStyle(color = color, fontSize = 12.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (row.unread > 0) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.widget_row_unread, row.unread),
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp),
            )
        }
    }
}


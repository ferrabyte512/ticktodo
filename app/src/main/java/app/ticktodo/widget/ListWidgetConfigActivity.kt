package app.ticktodo.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.ticktodo.R
import app.ticktodo.data.local.TagCodec
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.ui.theme.TickTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lets the user choose what a "Task list" widget shows. Exported because the
 * launcher starts it; it only changes that widget's display setting and reads
 * the signed-in user's own lists and tags.
 */
class ListWidgetConfigActivity : ComponentActivity() {

    private data class Option(val scope: TaskScope, val label: String, val icon: ImageVector)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cancelled unless the user picks something.
        setResult(RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val settingsFlow = widgetEntry(this).settings().settings
        setContent {
            val prefs by settingsFlow.collectAsState(initial = null)
            val p = prefs
            // Follow the user's colours and light/dark choice (without a wallpaper).
            TickTheme(
                themeMode = p?.themeMode ?: app.ticktodo.data.settings.ThemeMode.SYSTEM,
                appearance = (p?.appearance ?: app.ticktodo.data.settings.Appearance()).copy(wallpaper = app.ticktodo.data.settings.Wallpaper.NONE),
                dynamicColor = p?.dynamicColor ?: false,
            ) {
                val fixed = listOf(
                    Option(TaskScope.Today, stringResource(R.string.scope_today), Icons.Outlined.Today),
                    Option(TaskScope.Upcoming, stringResource(R.string.scope_upcoming), Icons.Outlined.CalendarMonth),
                    Option(TaskScope.Inbox, stringResource(R.string.scope_inbox), Icons.Outlined.Inbox),
                    Option(TaskScope.All, stringResource(R.string.scope_all), Icons.Outlined.ViewList),
                )
                val dynamic by produceState(initialValue = emptyList<Option>()) {
                    val entry = widgetEntry(this@ListWidgetConfigActivity)
                    val uid = entry.session().uid.value ?: return@produceState
                    val db = entry.database()
                    val lists = db.taskListDao().all(uid).map { Option(TaskScope.InList(it.id), it.name, Icons.Outlined.ViewList) }
                    val tags = db.taskDao().observeTagStrings(uid).first().flatMap(TagCodec::decode).distinct().sorted().map { Option(TaskScope.WithTag(it), "#$it", Icons.Outlined.Label) }
                    value = lists + tags
                }
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        items(fixed + dynamic) { option ->
                            ListItem(
                                modifier = Modifier.clickable { choose(widgetId, option.scope) },
                                leadingContent = { Icon(option.icon, null) },
                                headlineContent = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun choose(widgetId: Int, scope: TaskScope) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@ListWidgetConfigActivity).getGlanceIdBy(widgetId)
            updateAppWidgetState(this@ListWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[ListWidget.SCOPE_KEY] = scope.key() }
            }
            ListWidget().update(this@ListWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

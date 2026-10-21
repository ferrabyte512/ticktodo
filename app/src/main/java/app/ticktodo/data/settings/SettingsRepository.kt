package app.ticktodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.domain.model.TaskSort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Built-in colour themes. */
enum class AppTheme { JADE, OCEAN, SUNSET, LAVENDER, ROSE, GRAPHITE }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appTheme: AppTheme = AppTheme.JADE,
    val dynamicColor: Boolean = false,
    /** Reminder time for tasks that have a date but no time, in minutes after midnight. */
    val defaultReminderMinute: Int = 9 * 60,
    val sort: TaskSort = TaskSort.DUE_DATE,
    val askedNotificationPermission: Boolean = false,
    val defaultFocusPlan: FocusPlan = FocusPlan.DEFAULT,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Device-level preferences. They contain no task content. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val reminderMinute = intPreferencesKey("default_reminder_minute")
        val sort = stringPreferencesKey("sort")
        val askedNotifications = booleanPreferencesKey("asked_notifications")
        val appTheme = stringPreferencesKey("app_theme")
        val focusPlan = stringPreferencesKey("default_focus_plan")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: false,
            defaultReminderMinute = (p[Keys.reminderMinute] ?: (9 * 60)).coerceIn(0, 1_439),
            sort = p[Keys.sort]?.let { v -> TaskSort.entries.firstOrNull { it.name == v } } ?: TaskSort.DUE_DATE,
            askedNotificationPermission = p[Keys.askedNotifications] ?: false,
            appTheme = p[Keys.appTheme]?.let { v -> AppTheme.entries.firstOrNull { it.name == v } } ?: AppTheme.JADE,
            defaultFocusPlan = FocusPlan.parse(p[Keys.focusPlan]) ?: FocusPlan.DEFAULT,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamic] = enabled }
    suspend fun setDefaultReminderMinute(minute: Int) =
        context.dataStore.edit { it[Keys.reminderMinute] = minute.coerceIn(0, 1_439) }
    suspend fun setSort(sort: TaskSort) = context.dataStore.edit { it[Keys.sort] = sort.name }
    suspend fun setAppTheme(theme: AppTheme) = context.dataStore.edit { it[Keys.appTheme] = theme.name }
    suspend fun setDefaultFocusPlan(plan: FocusPlan) = context.dataStore.edit { it[Keys.focusPlan] = plan.encode() }
    suspend fun setAskedNotificationPermission() = context.dataStore.edit { it[Keys.askedNotifications] = true }
}

package app.ticktodo.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

/**
 * Built-in accent palettes. Stored by name; existing names are kept so saved
 * choices survive updates (JADE is shown as "Sage", SUNSET as "Terracotta").
 */
enum class AppTheme { JADE, OCEAN, SUNSET, LAVENDER, ROSE, GRAPHITE, APRICOT, HONEY }

/** Background behind every screen. Built-ins are drawn in code; PHOTO is the user's own picture. */
enum class Wallpaper { NONE, PEACH_GLOW, MOUNTAINS, DUNES, SEA_MIST, FOREST, WAVES, AURORA, GRADIENT, PHOTO }

/** All visual customisation in one value, so the theme can be built from it in one place. */
data class Appearance(
    val accent: AppTheme = AppTheme.APRICOT,
    /** ARGB; overrides [accent] when set. */
    val customAccent: Int? = null,
    val wallpaper: Wallpaper = Wallpaper.NONE,
    /** ARGB colours (with alpha) for the GRADIENT wallpaper. */
    val gradientStart: Int = 0xFFFFE3D0.toInt(),
    val gradientEnd: Int = 0xFFF6EFE6.toInt(),
    /** Readability scrim over the wallpaper, 0.2 to 0.9. */
    val overlay: Float = DEFAULT_OVERLAY,
    /** Wallpaper blur radius in dp, 0 to 24 (Android 12+). */
    val blurDp: Float = 0f,
    val glassCards: Boolean = true,
    /** Card opacity when glass cards are on, 0.55 to 1. */
    val cardOpacity: Float = DEFAULT_CARD_OPACITY,
    /** Bumped whenever a new photo is imported, so cached images are reloaded. */
    val photoVersion: Long = 0,
) {
    val hasWallpaper: Boolean get() = wallpaper != Wallpaper.NONE

    companion object {
        const val DEFAULT_OVERLAY = 0.55f
        const val DEFAULT_CARD_OPACITY = 0.84f
        val OVERLAY_RANGE = 0.2f..0.9f
        val BLUR_RANGE = 0f..24f
        val CARD_OPACITY_RANGE = 0.55f..1f
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Reminder time for tasks that have a date but no time, in minutes after midnight. */
    val defaultReminderMinute: Int = 9 * 60,
    val sort: TaskSort = TaskSort.DUE_DATE,
    val askedNotificationPermission: Boolean = false,
    val defaultFocusPlan: FocusPlan = FocusPlan.DEFAULT,
    val appearance: Appearance = Appearance(),
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
        val customAccent = intPreferencesKey("custom_accent")
        val wallpaper = stringPreferencesKey("wallpaper")
        val gradientStart = intPreferencesKey("gradient_start")
        val gradientEnd = intPreferencesKey("gradient_end")
        val overlay = floatPreferencesKey("wallpaper_overlay")
        val blur = floatPreferencesKey("wallpaper_blur")
        val glass = booleanPreferencesKey("glass_cards")
        val cardOpacity = floatPreferencesKey("card_opacity")
        val photoVersion = longPreferencesKey("photo_version")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: false,
            defaultReminderMinute = (p[Keys.reminderMinute] ?: (9 * 60)).coerceIn(0, 1_439),
            sort = p[Keys.sort]?.let { v -> TaskSort.entries.firstOrNull { it.name == v } } ?: TaskSort.DUE_DATE,
            askedNotificationPermission = p[Keys.askedNotifications] ?: false,
            defaultFocusPlan = FocusPlan.parse(p[Keys.focusPlan]) ?: FocusPlan.DEFAULT,
            appearance = Appearance(
                accent = p[Keys.appTheme]?.let { v -> AppTheme.entries.firstOrNull { it.name == v } } ?: AppTheme.APRICOT,
                customAccent = p[Keys.customAccent],
                wallpaper = p[Keys.wallpaper]?.let { v -> Wallpaper.entries.firstOrNull { it.name == v } } ?: Wallpaper.NONE,
                gradientStart = p[Keys.gradientStart] ?: Appearance().gradientStart,
                gradientEnd = p[Keys.gradientEnd] ?: Appearance().gradientEnd,
                overlay = (p[Keys.overlay] ?: Appearance.DEFAULT_OVERLAY).coerceIn(Appearance.OVERLAY_RANGE),
                blurDp = (p[Keys.blur] ?: 0f).coerceIn(Appearance.BLUR_RANGE),
                glassCards = p[Keys.glass] ?: true,
                cardOpacity = (p[Keys.cardOpacity] ?: Appearance.DEFAULT_CARD_OPACITY).coerceIn(Appearance.CARD_OPACITY_RANGE),
                photoVersion = p[Keys.photoVersion] ?: 0,
            ),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamic] = enabled }
    suspend fun setDefaultReminderMinute(minute: Int) =
        context.dataStore.edit { it[Keys.reminderMinute] = minute.coerceIn(0, 1_439) }
    suspend fun setSort(sort: TaskSort) = context.dataStore.edit { it[Keys.sort] = sort.name }
    /** Picking a preset clears any custom accent. */
    /** Choosing an accent also turns off wallpaper colours, which would otherwise hide it. */
    suspend fun setAppTheme(theme: AppTheme) = context.dataStore.edit {
        it[Keys.appTheme] = theme.name
        it.remove(Keys.customAccent)
        it[Keys.dynamic] = false
    }

    suspend fun setCustomAccent(argb: Int) = context.dataStore.edit {
        it[Keys.customAccent] = argb or 0xFF000000.toInt()
        it[Keys.dynamic] = false
    }
    suspend fun setWallpaper(wallpaper: Wallpaper) = context.dataStore.edit { it[Keys.wallpaper] = wallpaper.name }
    suspend fun setGradient(start: Int, end: Int) = context.dataStore.edit {
        it[Keys.gradientStart] = start
        it[Keys.gradientEnd] = end
        it[Keys.wallpaper] = Wallpaper.GRADIENT.name
    }
    suspend fun setOverlay(value: Float) = context.dataStore.edit { it[Keys.overlay] = value.coerceIn(Appearance.OVERLAY_RANGE) }
    suspend fun setBlur(value: Float) = context.dataStore.edit { it[Keys.blur] = value.coerceIn(Appearance.BLUR_RANGE) }
    suspend fun setGlassCards(enabled: Boolean) = context.dataStore.edit { it[Keys.glass] = enabled }
    suspend fun setCardOpacity(value: Float) = context.dataStore.edit { it[Keys.cardOpacity] = value.coerceIn(Appearance.CARD_OPACITY_RANGE) }

    /** Records that a new photo was saved and selects it. */
    suspend fun photoImported() = context.dataStore.edit {
        it[Keys.photoVersion] = (it[Keys.photoVersion] ?: 0) + 1
        it[Keys.wallpaper] = Wallpaper.PHOTO.name
    }

    /** Back to the clean default look; theme mode and other settings are kept. */
    suspend fun resetAppearance() = context.dataStore.edit {
        listOf(Keys.appTheme, Keys.customAccent, Keys.wallpaper, Keys.gradientStart, Keys.gradientEnd, Keys.overlay, Keys.blur, Keys.glass, Keys.cardOpacity, Keys.dynamic)
            .forEach { key -> it.remove(key) }
    }
    suspend fun setDefaultFocusPlan(plan: FocusPlan) = context.dataStore.edit { it[Keys.focusPlan] = plan.encode() }
    suspend fun setAskedNotificationPermission() = context.dataStore.edit { it[Keys.askedNotifications] = true }
}

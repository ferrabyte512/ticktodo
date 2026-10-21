package app.ticktodo.ui.appearance

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.data.settings.AppSettings
import app.ticktodo.data.settings.AppTheme
import app.ticktodo.data.settings.SettingsRepository
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.data.settings.Wallpaper
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import app.ticktodo.ui.theme.WallpaperStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val wallpapers: WallpaperStore,
    private val messenger: UiMessenger,
) : ViewModel() {

    val state: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun photoAvailable(): Boolean = wallpapers.exists()

    fun setThemeMode(mode: ThemeMode) = launch { settings.setThemeMode(mode) }
    fun setAccent(theme: AppTheme) = launch { settings.setAppTheme(theme) }
    fun setCustomAccent(argb: Int) = launch { settings.setCustomAccent(argb) }
    fun setDynamicColor(enabled: Boolean) = launch { settings.setDynamicColor(enabled) }
    fun setWallpaper(wallpaper: Wallpaper) = launch { settings.setWallpaper(wallpaper) }
    fun setGradient(start: Int, end: Int) = launch { settings.setGradient(start, end) }
    fun setOverlay(value: Float) = launch { settings.setOverlay(value) }
    fun setBlur(value: Float) = launch { settings.setBlur(value) }
    fun setGlassCards(enabled: Boolean) = launch { settings.setGlassCards(enabled) }
    fun setCardOpacity(value: Float) = launch { settings.setCardOpacity(value) }
    fun reset() = launch { settings.resetAppearance() }

    fun importPhoto(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                if (wallpapers.import(uri)) settings.photoImported()
                else messenger.show(UiMessage(R.string.appearance_photo_failed))
            } finally {
                _importing.value = false
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

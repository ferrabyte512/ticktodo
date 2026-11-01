package app.ticktodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ticktodo.data.auth.AuthRepository
import app.ticktodo.data.auth.AuthState
import app.ticktodo.data.settings.AppSettings
import app.ticktodo.data.settings.SettingsRepository
import app.ticktodo.domain.focus.FocusState
import app.ticktodo.focus.FocusManager
import app.ticktodo.ui.common.Celebration
import app.ticktodo.ui.common.Celebrations
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    auth: AuthRepository,
    settings: SettingsRepository,
    messenger: UiMessenger,
    private val focusManager: FocusManager,
    celebrations: Celebrations,
) : ViewModel() {
    val focusState: StateFlow<FocusState?> = focusManager.state
    val authState: StateFlow<AuthState> = auth.state
    val settings: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val messages: SharedFlow<UiMessage> = messenger.messages

    /** Snackbar actions (Undo) run here so they finish even if the snackbar host leaves composition. */
    fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }

    /** The focus banner's countdown reached zero before the alarm fired. */
    fun focusTimeUp() {
        viewModelScope.launch { focusManager.onPhaseTimeUp() }
    }
    val celebrations: SharedFlow<Celebration> = celebrations.events
}

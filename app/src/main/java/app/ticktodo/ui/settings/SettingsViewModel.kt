package app.ticktodo.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.account.AccountService
import app.ticktodo.account.DeleteResult
import app.ticktodo.data.auth.AuthRepository
import app.ticktodo.data.auth.AuthResult
import app.ticktodo.data.auth.AuthState
import app.ticktodo.data.auth.UserProfile
import app.ticktodo.data.settings.AppSettings
import app.ticktodo.data.settings.AppTheme
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.data.settings.SettingsRepository
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.reminders.ReminderScheduler
import app.ticktodo.sync.SyncScheduler
import app.ticktodo.sync.SyncStatus
import app.ticktodo.sync.SyncStatusRepository
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AccountDialog {
    data class SignOut(val pending: Int) : AccountDialog
    data object Delete : AccountDialog
}

data class SettingsUiState(
    val user: UserProfile? = null,
    val settings: AppSettings = AppSettings(),
    val sync: SyncStatus = SyncStatus(),
    val dialog: AccountDialog? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val accounts: AccountService,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    private val reminders: ReminderScheduler,
    private val messenger: UiMessenger,
    syncStatus: SyncStatusRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(local, auth.state, settingsRepository.settings, syncStatus.status) { l, a, s, sync ->
        l.copy(user = (a as? AuthState.SignedIn)?.user, settings = s, sync = sync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setAppTheme(theme: AppTheme) = viewModelScope.launch { settingsRepository.setAppTheme(theme) }
    fun setDefaultFocusPlan(plan: FocusPlan) = viewModelScope.launch { settingsRepository.setDefaultFocusPlan(plan) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }

    fun syncNow() = syncScheduler.requestSync(delaySeconds = 0)

    /** Re-arms alarms after the user granted a permission. */
    fun onPermissionsMaybeChanged() = viewModelScope.launch { reminders.rescheduleAll() }

    fun askSignOut() {
        val uid = auth.currentUid() ?: return
        viewModelScope.launch {
            syncScheduler.requestSync(delaySeconds = 0)
            local.update { it.copy(dialog = AccountDialog.SignOut(accounts.pendingChanges(uid))) }
        }
    }

    fun askDelete() = local.update { it.copy(dialog = AccountDialog.Delete) }
    fun dismissDialog() = local.update { it.copy(dialog = null) }

    fun signOut() {
        local.update { it.copy(dialog = null, busy = true) }
        viewModelScope.launch {
            accounts.signOut()
            local.update { it.copy(busy = false) }
        }
    }

    fun deleteAccount(activity: Activity) {
        local.update { it.copy(dialog = null, busy = true) }
        viewModelScope.launch {
            val result = accounts.deleteAccount(activity)
            local.update { it.copy(busy = false) }
            when (result) {
                DeleteResult.Deleted -> messenger.show(UiMessage(R.string.msg_account_deleted))
                DeleteResult.Network -> messenger.show(UiMessage(R.string.signin_error_network))
                is DeleteResult.AuthFailed -> if (result.reason != AuthResult.Cancelled) {
                    messenger.show(
                        UiMessage(if (result.reason == AuthResult.WrongAccount) R.string.delete_error_wrong_account else R.string.delete_error_generic),
                    )
                }
                DeleteResult.Failed -> messenger.show(UiMessage(R.string.delete_error_generic))
            }
        }
    }
}

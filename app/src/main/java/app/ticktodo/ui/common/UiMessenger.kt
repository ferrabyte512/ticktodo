package app.ticktodo.ui.common

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UiMessage(
    @StringRes val text: Int,
    val args: List<Any> = emptyList(),
    @StringRes val actionLabel: Int? = null,
    val onAction: (suspend () -> Unit)? = null,
)

/**
 * App-wide snackbar queue, so a screen can show "Task deleted — Undo" even
 * after it navigated back to the previous screen.
 */
@Singleton
class UiMessenger @Inject constructor() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun show(message: UiMessage) {
        _messages.tryEmit(message)
    }
}

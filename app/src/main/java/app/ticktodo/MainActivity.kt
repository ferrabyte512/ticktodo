package app.ticktodo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ticktodo.data.auth.AuthState
import app.ticktodo.ui.MainViewModel
import app.ticktodo.ui.navigation.AppRoot
import app.ticktodo.ui.navigation.PendingAction
import app.ticktodo.ui.theme.AppBackground
import app.ticktodo.ui.theme.TickTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val pending = MutableStateFlow<PendingAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the splash until auth state and theme are known, to avoid a flash of the wrong screen.
        splash.setKeepOnScreenCondition {
            viewModel.authState.value is AuthState.Loading || viewModel.settings.value == null
        }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val action by pending.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            TickTheme(themeMode = s.themeMode, appearance = s.appearance, dynamicColor = s.dynamicColor) {
                AppBackground {
                    AppRoot(viewModel, action) { pending.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Only our own notifications and widget send these actions, but the activity
     * is exported, so the input is validated: the id must look like a task id,
     * and it is only ever looked up within the signed-in user's data.
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_TASK -> intent.getStringExtra(EXTRA_TASK_ID)
                ?.takeIf { TASK_ID.matches(it) }
                ?.let { pending.value = PendingAction.OpenTask(it) }
            ACTION_QUICK_ADD -> pending.value = PendingAction.QuickAdd
            ACTION_OPEN_FOCUS -> pending.value = PendingAction.OpenFocus
        }
    }

    companion object {
        const val ACTION_OPEN_TASK = "app.ticktodo.action.OPEN_TASK"
        const val ACTION_QUICK_ADD = "app.ticktodo.action.QUICK_ADD"
        const val ACTION_OPEN_FOCUS = "app.ticktodo.action.OPEN_FOCUS"
        const val EXTRA_TASK_ID = "task_id"
        private val TASK_ID = Regex("^[0-9a-fA-F-]{36}$")
    }
}

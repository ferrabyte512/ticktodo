package app.ticktodo.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ticktodo.core.AppLog
import app.ticktodo.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Phase-end alarm and notification buttons. Not exported: only our own PendingIntents reach it. */
@AndroidEntryPoint
class FocusReceiver : BroadcastReceiver() {
    @Inject lateinit var manager: FocusManager
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_PHASE_END -> manager.onPhaseTimeUp()
                    ACTION_PAUSE -> manager.pause()
                    ACTION_RESUME -> manager.resume()
                    ACTION_SKIP -> manager.skip()
                    ACTION_STOP -> manager.stop()
                }
            } catch (e: Exception) {
                AppLog.e("focus action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PHASE_END = "app.ticktodo.action.FOCUS_PHASE_END"
        const val ACTION_PAUSE = "app.ticktodo.action.FOCUS_PAUSE"
        const val ACTION_RESUME = "app.ticktodo.action.FOCUS_RESUME"
        const val ACTION_SKIP = "app.ticktodo.action.FOCUS_SKIP"
        const val ACTION_STOP = "app.ticktodo.action.FOCUS_STOP"
    }
}

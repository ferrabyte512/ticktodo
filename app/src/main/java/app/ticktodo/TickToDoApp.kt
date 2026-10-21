package app.ticktodo

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import app.ticktodo.account.AccountService
import app.ticktodo.core.AppLog
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.remote.FirebaseProvider
import app.ticktodo.di.ApplicationScope
import app.ticktodo.focus.FocusManager
import app.ticktodo.focus.FocusNotifications
import app.ticktodo.reminders.Notifications
import app.ticktodo.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TickToDoApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var firebase: FirebaseProvider
    @Inject lateinit var session: UserSession
    @Inject lateinit var accountService: AccountService
    @Inject lateinit var syncScheduler: SyncScheduler
    // Injected so a running focus session is restored and its alarm re-armed at start-up.
    @Inject lateinit var focusManager: FocusManager
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        FocusNotifications.createChannels(this)
        if (firebase.isConfigured) AppCheckInstaller.install()

        // Whenever a user is (or becomes) signed in: drop rows of any other account,
        // make sure periodic sync and reminders are in place.
        scope.launch {
            session.uid.filterNotNull().collect { uid ->
                try {
                    accountService.onSignedIn(uid)
                } catch (e: Exception) {
                    AppLog.e("post sign-in setup failed", e)
                }
            }
        }

        // Pull other devices' changes each time the app comes to the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (session.uid.value != null) syncScheduler.requestSync(delaySeconds = 0)
            }
        })
    }
}

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
import app.ticktodo.reminders.ReminderScheduler
import app.ticktodo.sync.SyncScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TickToDoApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var firebase: FirebaseProvider

    // Lazy so that nothing heavy (Firebase Auth, the database) is created on the
    // main thread during start-up; see onCreate.
    @Inject lateinit var session: Lazy<UserSession>
    @Inject lateinit var accountService: Lazy<AccountService>
    @Inject lateinit var syncScheduler: Lazy<SyncScheduler>
    @Inject lateinit var focusManager: Lazy<FocusManager>
    @Inject lateinit var reminders: Lazy<ReminderScheduler>
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

        // Start-up work runs on a background thread so the first screen appears sooner.
        scope.launch {
            if (firebase.isConfigured) AppCheckInstaller.install()
            // Restores a running focus session and re-arms its alarm.
            focusManager.get()
            // Whenever a user is (or becomes) signed in: drop rows of any other account,
            // make sure periodic sync and reminders are in place.
            session.get().uid.filterNotNull().collect { uid ->
                try {
                    accountService.get().onSignedIn(uid)
                } catch (e: Exception) {
                    AppLog.e("post sign-in setup failed", e)
                }
            }
        }

        // Pull other devices' changes each time the app comes to the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch {
                    if (session.get().uid.value != null) {
                        syncScheduler.get().requestSync(delaySeconds = 0)
                        // A force-stop (or an aggressive battery manager) drops the app's alarms;
                        // opening the app puts them back.
                        reminders.get().rescheduleAll()
                    }
                }
            }
        })
    }
}

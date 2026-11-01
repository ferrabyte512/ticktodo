package app.ticktodo.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ticktodo.core.AppLog
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.local.FocusSessionEntity
import app.ticktodo.di.ApplicationScope
import app.ticktodo.domain.focus.FocusEngine
import app.ticktodo.domain.focus.FocusPhase
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.domain.focus.FocusState
import app.ticktodo.domain.focus.FocusTransition
import app.ticktodo.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusStore: DataStore<Preferences> by preferencesDataStore(
    name = "focus",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Runs focus sessions. The state is persisted after every change and the end
 * of each phase is an alarm, so a session keeps going when the app is closed,
 * killed or the phone restarts. No foreground service is used: the ongoing
 * notification counts down by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FocusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val session: UserSession,
    private val clock: Clock,
    private val widget: WidgetUpdater,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("state")
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private val _state = MutableStateFlow<FocusState?>(null)
    val state: StateFlow<FocusState?> = _state.asStateFlow()

    init {
        scope.launch { restore() }
    }

    fun observeTaskFocusTotal(taskId: String): Flow<Long> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(0L) else db.focusDao().observeTaskTotal(uid, taskId)
    }

    fun observeFocusSince(since: Long): Flow<Long> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(0L) else db.focusDao().observeTotalSince(uid, since)
    }

    /** Reloads the saved session and applies any phase ends that passed while the app was not running. */
    suspend fun restore() = mutex.withLock {
        val saved = context.focusStore.data.first()[key]?.let {
            try { json.decodeFromString(FocusState.serializer(), it) } catch (e: Exception) { null }
        }
        _state.value = saved
        if (saved != null) {
            val transitions = FocusEngine.catchUp(saved, clock.now())
            transitions.forEach { record(saved, it) }
            commit(transitions.lastOrNull()?.state ?: saved, alert = transitions.lastOrNull())
        }
    }

    suspend fun start(taskId: String, taskTitle: String, plan: FocusPlan) = mutex.withLock {
        val now = clock.now()
        _state.value?.let { record(it, FocusTransition(null, FocusEngine.stop(it, now), it.phase, true)) }
        commit(FocusEngine.start(taskId, taskTitle, plan, now))
    }

    suspend fun pause() = mutate { FocusEngine.pause(it, clock.now()) }

    suspend fun resume() = mutate { FocusEngine.resume(it, clock.now()) }

    /** Adds five minutes to the current phase. */
    suspend fun extend() = mutate { FocusEngine.extend(it, FocusEngine.EXTEND_MS) }

    /** Ends the current phase early and moves on. */
    suspend fun skip() = mutex.withLock {
        val current = _state.value ?: return@withLock
        val t = FocusEngine.advance(current, clock.now(), ranToEnd = false)
        record(current, t)
        commit(t.state)
    }

    /** Ends the session, keeping the focus time spent so far. */
    suspend fun stop() = mutex.withLock {
        val current = _state.value ?: return@withLock
        record(current, FocusTransition(null, FocusEngine.stop(current, clock.now()), current.phase, true))
        commit(null)
    }

    /** Ends the session without recording anything; used on sign-out and account deletion. */
    suspend fun clear() = mutex.withLock { commit(null) }

    /** Called by the phase-end alarm and by the focus screen when its countdown reaches zero. */
    suspend fun onPhaseTimeUp() = mutex.withLock {
        val current = _state.value ?: return@withLock
        val transitions = FocusEngine.catchUp(current, clock.now())
        if (transitions.isEmpty()) {
            // Alarm fired early (inexact alarms can drift); make sure it is armed again.
            commit(current)
            return@withLock
        }
        transitions.forEach { record(current, it) }
        commit(transitions.last().state, alert = transitions.last())
    }

    private suspend fun mutate(block: (FocusState) -> FocusState) = mutex.withLock {
        val current = _state.value ?: return@withLock
        commit(block(current))
    }

    private suspend fun record(state: FocusState, transition: FocusTransition) {
        val uid = session.uid.value ?: return
        if (transition.focusMsEarned < MIN_RECORDED_MS) return
        val now = clock.now()
        db.focusDao().insert(
            FocusSessionEntity(
                ownerUid = uid,
                taskId = state.taskId,
                startedAt = now - transition.focusMsEarned,
                endedAt = now,
                focusMs = transition.focusMsEarned,
            ),
        )
    }

    private suspend fun commit(next: FocusState?, alert: FocusTransition? = null) {
        _state.value = next
        context.focusStore.edit { prefs ->
            if (next == null) prefs.remove(key) else prefs[key] = json.encodeToString(FocusState.serializer(), next)
        }
        val endsAt = next?.phaseEndsAt
        if (endsAt != null) scheduleAlarm(endsAt) else cancelAlarm()
        FocusNotifications.showOngoing(context, next, clock.now())
        alert?.let { FocusNotifications.showPhaseAlert(context, it) }
        widget.update()
    }

    private fun alarmIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        Intent(context, FocusReceiver::class.java).setAction(FocusReceiver.ACTION_PHASE_END),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(at: Long) {
        val pi = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            AppLog.w("exact focus alarm refused", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm() {
        alarmIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        private const val ALARM_REQUEST = 7_001
        /** Shorter focus periods (an accidental start) are not counted. */
        const val MIN_RECORDED_MS = 60_000L

        fun isBreak(phase: FocusPhase) = phase != FocusPhase.FOCUS
    }
}

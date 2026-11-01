package app.ticktodo.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager persists requests across process death and reboots, so a change
 * made offline is pushed once the network is back even if the app was closed.
 */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Requests a sync soon. A short delay batches rapid edits into one run; an
     * already queued request is kept because it reads all pending rows when it runs.
     * [throttlePull] is set by routine triggers (app opened, local edit): they
     * push at once but only pull if the last pull is older than ten minutes,
     * which keeps Firestore reads inside the free daily quota. A request
     * without it (sign-in, Sync now, pull-to-refresh) replaces a throttled one.
     */
    fun requestSync(full: Boolean = false, delaySeconds: Long = 3, throttlePull: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_FULL to full, SyncWorker.KEY_THROTTLE_PULL to throttlePull))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(
            ONE_TIME,
            if (throttlePull) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Picks up changes made on other devices while the app is not open. Twelve
     * hours: active users pull when they open the app anyway, and each run
     * costs at least two Firestore reads per device.
     */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        // UPDATE so installs that still carry the old 6-hour interval move to 12 hours.
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    /** True while a sync is running. */
    val isRunning: Flow<Boolean> = workManager.getWorkInfosByTagFlow(TAG)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    companion object {
        private const val TAG = "sync"
        private const val ONE_TIME = "sync-now"
        private const val PERIODIC = "sync-periodic"
    }
}

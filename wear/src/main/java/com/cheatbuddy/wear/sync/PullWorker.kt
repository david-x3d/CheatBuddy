package com.cheatbuddy.wear.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.wear.tiles.TileService
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cheatbuddy.wear.tile.LastSheetTileService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Runs [WatchSyncEngine] in the background. Never blocks or drives the UI. */
@HiltWorker
class PullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: WatchSyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = engine.sync()
        runCatching { TileService.getUpdater(applicationContext).requestUpdate(LastSheetTileService::class.java) }
        return when (outcome) {
            SyncOutcome.Success -> Result.success()
            // Reconnection re-triggers sync via the capability listener; one retry covers short hiccups.
            is SyncOutcome.WaitingForPhone -> if (runAttemptCount < 1) Result.retry() else Result.success()
            SyncOutcome.StorageFull -> Result.failure()
            is SyncOutcome.Failed -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

class SyncScheduler(private val workManager: WorkManager) {

    /** Debounced one-off sync; bursts of Data Layer events collapse into one run. */
    fun syncSoon(delaySeconds: Long = 2) {
        val request = OneTimeWorkRequestBuilder<PullWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.REPLACE, request)
    }

    /** User-triggered sync from settings. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<PullWorker>().build()
        workManager.enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.REPLACE, request)
    }

    /** Safety net in case an event was missed. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<PullWorker>(1, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val ONE_OFF = "cheatbuddy-pull"
        const val PERIODIC = "cheatbuddy-pull-periodic"
    }
}

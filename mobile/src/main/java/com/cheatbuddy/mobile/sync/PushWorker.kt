package com.cheatbuddy.mobile.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

@HiltWorker
class PushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: PhoneSyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = engine.push()) {
        is PushResult.Published -> Result.success()
        is PushResult.Failed -> if (result.retryable && runAttemptCount < 5) Result.retry() else Result.failure()
    }
}

class PhoneSyncScheduler(private val workManager: WorkManager) {

    /** Debounced push: rapid edits (typing) collapse into one publish. */
    fun pushSoon(delayMillis: Long = 800) {
        val request = OneTimeWorkRequestBuilder<PushWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun pushNow() = pushSoon(delayMillis = 0)

    val isRunning: Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        infos.any { it.state == WorkInfo.State.RUNNING }
    }

    private companion object {
        const val WORK_NAME = "cheatbuddy-push"
    }
}

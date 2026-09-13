package eu.kanade.tachiyomi.ui.source

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Refreshes network-backed Discover shelves periodically even when InkShelf is not open. */
internal class InkShelfDiscoverRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        runCatching {
            if (BrowseController.refreshDiscoverFeedsInBackground(applicationContext)) {
                Result.success()
            } else {
                Result.retry()
            }
        }.getOrElse { Result.retry() }

    companion object {
        private const val UNIQUE_WORK = "inkshelf_discover_background_refresh_v1"

        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<InkShelfDiscoverRefreshWorker>(6, TimeUnit.HOURS)
                    // Do not compete with the first few minutes after app launch/install.
                    .setInitialDelay(20, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager
                .getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}

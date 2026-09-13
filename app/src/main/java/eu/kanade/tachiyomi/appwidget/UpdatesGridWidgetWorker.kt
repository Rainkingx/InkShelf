package eu.kanade.tachiyomi.appwidget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class UpdatesGridWidgetWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        with(TachiyomiWidgetManager()) { applicationContext.init() }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "UpdatesGridGlanceWidgetRefresh"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdatesGridWidgetWorker>().build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

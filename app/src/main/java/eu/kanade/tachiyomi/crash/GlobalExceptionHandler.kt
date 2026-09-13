package eu.kanade.tachiyomi.crash

import android.content.Context
import android.content.Intent
import timber.log.Timber

class GlobalExceptionHandler private constructor(
    private val applicationContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler,
    private val activityToBeLaunched: Class<*>,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        exception: Throwable,
    ) {
        Timber.e(exception)
        launchActivity(exception)
        defaultHandler.uncaughtException(thread, exception)
    }

    private fun launchActivity(exception: Throwable) {
        val intent =
            Intent(applicationContext, activityToBeLaunched).apply {
                putExtra(INTENT_EXTRA_STACK_TRACE, exception.stackTraceToString())
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        applicationContext.startActivity(intent)
    }

    companion object {
        private const val INTENT_EXTRA_STACK_TRACE = "stack_trace"

        fun initialize(
            applicationContext: Context,
            activityToBeLaunched: Class<*>,
        ) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler is GlobalExceptionHandler) return
            val handler =
                GlobalExceptionHandler(
                    applicationContext,
                    currentHandler ?: Thread.UncaughtExceptionHandler { _, _ -> },
                    activityToBeLaunched,
                )
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        fun getStackTraceFromIntent(intent: Intent): String? = intent.getStringExtra(INTENT_EXTRA_STACK_TRACE)
    }
}

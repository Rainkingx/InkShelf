package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Offline controls operate only on already verified local J2K chapter records. */
internal object InkShelf2GuideDownloads {
    enum class Status { UNMATCHED, AVAILABLE, QUEUED, DOWNLOADING, OFFLINE, FAILED }

    data class State(val status: Status, val percent: Int = 0) {
        val canQueue: Boolean get() = status == Status.AVAILABLE || status == Status.FAILED
    }

    fun eligible(
        target: InkShelf2GuideProgress.Target,
        sources: SourceManager,
        enabledSourceIds: Set<Long>,
    ): Boolean = target.chapter.id != null && target.manga.source in enabledSourceIds &&
        sources.get(target.manga.source) is HttpSource

    suspend fun snapshot(
        progress: InkShelf2GuideProgress.EventState,
        manager: DownloadManager,
        sources: SourceManager,
        enabledSourceIds: Set<Long>,
    ): Map<Int, State> = withContext(Dispatchers.IO) {
        manager.awaitDownloadCacheReady()
        val jobs = manager.queue.mapNotNull { job -> job.chapter.id?.let { it to job } }.toMap()
        progress.issues.mapValues { (_, issue) ->
            val targets = issue.targets
            val downloadable = targets.filter { eligible(it, sources, enabledSourceIds) }
            val queued = targets.mapNotNull { jobs[it.chapter.id] }
            val running = queued.firstOrNull { it.status == Download.State.DOWNLOADING }
            when {
                targets.any { manager.isChapterDownloaded(it.chapter, it.manga) } -> State(Status.OFFLINE)
                running != null -> State(Status.DOWNLOADING, running.progress.coerceIn(0, 100))
                queued.any { it.status != Download.State.ERROR && it.status != Download.State.NOT_DOWNLOADED } -> State(Status.QUEUED)
                downloadable.any { jobs[it.chapter.id]?.status == Download.State.ERROR } -> State(Status.FAILED)
                downloadable.isNotEmpty() -> State(Status.AVAILABLE)
                else -> State(Status.UNMATCHED)
            }
        }
    }

    suspend fun offlineTarget(
        progress: InkShelf2GuideProgress.EventState,
        issueId: Int,
        manager: DownloadManager,
    ): InkShelf2GuideProgress.Target? = withContext(Dispatchers.IO) {
        manager.awaitDownloadCacheReady()
        progress.issues[issueId]?.targets?.firstOrNull {
            manager.isChapterDownloaded(it.chapter, it.manga)
        }
    }
}

package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class DownloadQueue(
    private val store: DownloadStore,
    private val queue: MutableList<Download> = CopyOnWriteArrayList<Download>(),
) : List<Download> by queue {
    private val _state = MutableStateFlow(emptyList<Download>())

    /** Emits the queue's contents on every change, so the downloader can re-pick what to run. */
    val state: StateFlow<List<Download>> = _state.asStateFlow()

    private val downloadListeners = mutableListOf<DownloadListener>()

    private var scope = MainScope()

    fun addAll(downloads: List<Download>) {
        downloads.forEach { download ->
            download.setStatusCallback(::setPagesFor)
            download.status = Download.State.QUEUE
        }
        queue.addAll(downloads)
        store.addAll(downloads)
        _state.value = queue.toList()
    }

    fun remove(download: Download) {
        val removed = queue.remove(download)
        store.remove(download)
        download.setStatusCallback(null)
        if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
            download.status = Download.State.NOT_DOWNLOADED
        }
        downloadListeners.forEach { it.updateDownload(download) }
        if (removed) {
            _state.value = queue.toList()
        }
    }

    fun updateListeners() {
        val listeners = downloadListeners.toList()
        listeners.forEach { it.updateDownloads() }
    }

    fun remove(chapter: Chapter) {
        find { it.chapter.id == chapter.id }?.let { remove(it) }
    }

    fun remove(chapters: List<Chapter>) {
        for (chapter in chapters) {
            remove(chapter)
        }
    }

    fun remove(manga: Manga) {
        filter { it.manga.id == manga.id }.forEach { remove(it) }
    }

    fun clear() {
        queue.forEach { download ->
            download.setStatusCallback(null)
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
            downloadListeners.forEach { it.updateDownload(download) }
        }
        queue.clear()
        store.clear()
        _state.value = queue.toList()
    }

    private fun setPagesFor(download: Download) {
        if (download.status == Download.State.DOWNLOADING) {
            if (download.pages != null) {
                for (page in download.pages!!) {
                    scope.launch {
                        page.statusFlow.collectLatest {
                            callListeners(download)
                        }
                    }
                }
            }
            callListeners(download)
        } else if (download.status == Download.State.DOWNLOADED || download.status == Download.State.ERROR) {
            if (download.status == Download.State.ERROR) {
                callListeners(download)
            }
        } else {
            callListeners(download)
        }
    }

    private fun callListeners(download: Download) {
        downloadListeners.forEach { it.updateDownload(download) }
    }

    fun addListener(listener: DownloadListener) {
        downloadListeners.add(listener)
    }

    fun removeListener(listener: DownloadListener) {
        downloadListeners.remove(listener)
    }

    interface DownloadListener {
        fun updateDownload(download: Download)

        fun updateDownloads()
    }
}

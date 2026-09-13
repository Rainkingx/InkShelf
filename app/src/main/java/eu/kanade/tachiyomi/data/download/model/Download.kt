package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class Download(
    val source: HttpSource,
    val manga: Manga,
    val chapter: Chapter,
) {
    var pages: List<Page>? = null

    val totalProgress: Int
        get() = pages?.sumOf(Page::progress) ?: 0

    val downloadedImages: Int
        get() = pages?.count { it.status == Page.State.READY } ?: 0

    @Volatile @Transient
    var status: State = State.default
        set(status) {
            field = status
            _statusFlow.value = status
            statusCallback?.invoke(this)
        }

    @Transient private val _statusFlow = MutableStateFlow(State.default)

    /**
     * Emits on every [status] change. The downloader watches this to notice an active download
     * erroring, which frees up its source for the next chapter without the queue itself changing.
     */
    @Transient val statusFlow: StateFlow<State> = _statusFlow.asStateFlow()

    @Transient private var statusCallback: ((Download) -> Unit)? = null

    val pageProgress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).sum()
        }

    val progress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).average().roundToInt()
        }

    fun setStatusCallback(f: ((Download) -> Unit)?) {
        statusCallback = f
    }

    enum class State {
        CHECKED,
        NOT_DOWNLOADED,
        QUEUE,
        DOWNLOADING,
        DOWNLOADED,
        ERROR,

        /** Downloaded status not yet known - the download cache hasn't finished its initial scan. */
        PENDING,
        ;

        companion object {
            val default = NOT_DOWNLOADED
        }
    }
}

package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.ImageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Page-only, read-time fallback. Never adds a favourite, changes Manga IDs,
 * overwrites J2K chapter data, or carries over reading progress blindly.
 *
 * A candidate must have the same title, known year (if any), unambiguous
 * issue number, and EXACT page count. Different page counts are rejected,
 * since borrowing page N from a differently paginated comic is unsafe.
 */
internal object InkShelf2AlternativePages {
    data class RecoveredImage(val bytes: ByteArray, val sourceName: String)

    private data class MatchingIssue(
        val source: HttpSource,
        val pages: List<Page>,
    )
    private data class CacheEntry(val atMillis: Long, val matches: List<MatchingIssue>)

    private const val MATCH_CACHE_MILLIS = 5 * 60_000L
    private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    private val lock = Mutex()
    private val issueCache = LinkedHashMap<String, CacheEntry>()
    private val notified = ConcurrentHashMap.newKeySet<String>()
    private val db: DatabaseHelper by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }
    private val prefs: PreferencesHelper by lazy { Injekt.get() }

    fun enabled(): Boolean = prefs.inkShelfAutoSourceFallback().get()

    suspend fun recoverByChapterId(
        mangaId: Long?,
        chapter: Chapter,
        pageIndex: Int,
        totalPages: Int,
    ): RecoveredImage? {
        if (!enabled() || mangaId == null) return null
        val manga = withContext(Dispatchers.IO) {
            db.getManga(mangaId).executeAsBlocking()
        } ?: return null
        return recover(manga, chapter, pageIndex, totalPages)
    }

    suspend fun recover(
        manga: Manga,
        chapter: Chapter,
        pageIndex: Int,
        totalPages: Int,
    ): RecoveredImage? {
        if (!enabled() || totalPages < 2 || pageIndex !in 0 until totalPages) return null
        if (!chapter.chapter_number.isFinite() || chapter.chapter_number < 0f) return null
        if (InkShelf2ComicIdentity.editionKey(manga).isBlank()) return null
        val matches = try {
            withTimeout(30_000L) { matchingIssues(manga, chapter, totalPages) }
        } catch (cancel: CancellationException) {
            if (cancel !is TimeoutCancellationException) throw cancel
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        for (match in matches) {
            try {
                val bytes = withTimeout(13_000L) {
                    val remote = match.pages[pageIndex]
                    // Never mutate an extension's cached Page object.
                    val page = Page(remote.index, remote.url, remote.imageUrl)
                    if (page.imageUrl.isNullOrBlank()) page.imageUrl = match.source.getImageUrl(page)
                    if (page.imageUrl.isNullOrBlank()) return@withTimeout null
                    match.source.getImage(page).use { response ->
                        withContext(Dispatchers.IO) {
                            val length = response.body.contentLength()
                            if (length > MAX_IMAGE_BYTES) return@withContext null
                            val data = ByteArrayOutputStream()
                            response.body.byteStream().use { stream ->
                                val chunk = ByteArray(16 * 1024)
                                while (true) {
                                    val n = stream.read(chunk)
                                    if (n < 0) break
                                    if (data.size() + n > MAX_IMAGE_BYTES) return@withContext null
                                    data.write(chunk, 0, n)
                                }
                            }
                            val bytes = data.toByteArray()
                            if (bytes.isNotEmpty() &&
                                ImageUtil.findImageType { bytes.inputStream() } != null
                            ) bytes else null
                        }
                    }
                } ?: continue
                return RecoveredImage(bytes, match.source.name)
            } catch (cancel: CancellationException) {
                if (cancel !is TimeoutCancellationException) throw cancel
            } catch (_: Exception) {
                // One source failing must not prevent trying the next.
            }
        }
        return null
    }

    /** Warn once per chapter, not every failed image. */
    fun markFirstRecovery(manga: Manga, chapter: Chapter): Boolean =
        notified.add(manga.source.toString() + ":" + manga.url + ":" + chapter.url)

    private suspend fun matchingIssues(
        manga: Manga,
        chapter: Chapter,
        totalPages: Int,
    ): List<MatchingIssue> {
        val key = manga.source.toString() + ":" + manga.url + ":" + chapter.url + ":" + totalPages
        return lock.withLock {
            val now = System.currentTimeMillis()
            issueCache[key]?.takeIf { now - it.atMillis < MATCH_CACHE_MILLIS }?.let {
                return@withLock it.matches
            }
            val enabledLanguages = prefs.enabledLanguages().get()
            val hiddenSources = prefs.hiddenSources().get()
            val candidates = sourceManager.getCatalogueSources()
                .filterIsInstance<HttpSource>()
                .filter { it.id != manga.source && it.lang in enabledLanguages &&
                    it.id.toString() !in hiddenSources }
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<HttpSource> { InkShelf2SourceBalance.sourceOrder(it.name) }
                        .thenBy { it.name },
                ).take(5)

            val edition = InkShelf2ComicIdentity.editionKey(manga)
            val titleQuery = InkShelf2ComicIdentity.normalizedTitle(manga.title)
            val matches = ArrayList<MatchingIssue>()
            for (source in candidates) {
                if (matches.size >= 3) break
                try {
                    val found = withTimeout(9_000L) {
                        source.getSearchManga(1, titleQuery, source.getFilterList()).mangas
                            .filter { InkShelf2ComicIdentity.editionKey(it.title) == edition }
                            .take(3)
                    }
                    for (series in found) {
                        val chapters = withTimeout(10_000L) { source.getChapterList(series) }
                        val issueMatches = chapters.filter {
                            it.chapter_number.isFinite() &&
                                abs(it.chapter_number - chapter.chapter_number) < 0.01f
                        }
                        // Duplicate issue numbers could be variants or different volumes.
                        if (issueMatches.size != 1) continue
                        val pages = withTimeout(10_000L) {
                            source.getPageList(issueMatches.single())
                        }
                        if (pages.size == totalPages) {
                            matches += MatchingIssue(source, pages)
                            break
                        }
                    }
                } catch (cancel: CancellationException) {
                    if (cancel !is TimeoutCancellationException) throw cancel
                } catch (_: Exception) {
                    // Source failed, try next catalogue.
                }
            }
            if (issueCache.size >= 64) issueCache.remove(issueCache.keys.first())
            issueCache[key] = CacheEntry(now, matches)
            matches
        }
    }
}

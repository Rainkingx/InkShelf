package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * InkShelf 2.0 Compose adapter to J2K extension catalogue sources.
 *
 * Keeps the original source interface, installed extensions, chapter sync,
 * reading progress, database and reader. No alternate comic backend.
 */
internal class InkShelf2SourcesEngine {
    private val database: DatabaseHelper = Injekt.get()
    private val sources: SourceManager = Injekt.get()

    data class Page(val comics: List<Manga>, val hasMore: Boolean)

    suspend fun page(source: CatalogueSource, page: Int, query: String, latest: Boolean = false): Page =
        withContext(Dispatchers.IO) {
            val result = when {
                query.isNotBlank() -> source.getSearchManga(page, query.trim(), source.getFilterList())
                latest && source.supportsLatest -> source.getLatestUpdates(page)
                else -> source.getPopularManga(page)
            }
            val comics = result.mangas.map { remote ->
                // A catalogue result can be viewed without automatically adding
                // every search result to the user's database.
                database.getManga(remote.url, source.id).executeAsBlocking()
                    ?: Manga.create(remote.url, remote.title, source.id).apply {
                        copyFrom(remote)
                    }
            }.distinctBy { it.url }
            Page(comics, result.hasNextPage)
        }

    /** Select one comic: register it in the original J2K database and load issues. */
    suspend fun prepareComic(comic: Manga): Manga = withContext(Dispatchers.IO) {
        val existing = database.getManga(comic.url, comic.source).executeAsBlocking()
        val local = existing ?: comic.also {
            it.id = database.insertManga(it).executeAsBlocking().insertedId()
        }
        val source = sources.get(local.source) ?: return@withContext local
        // Prefer source metadata when the locally cached result is incomplete.
        if (!local.initialized || local.description.isNullOrBlank()) {
            runCatching {
                val updated = source.getMangaDetails(local.copy())
                local.copyFrom(updated)
                local.initialized = true
                database.updateMangaInfo(local).executeAsBlocking()
            }
        }
        // Use J2K's chapter synchronizer, which preserves read/download state.
        runCatching {
            val chapters = source.getChapterList(local)
            if (chapters.isNotEmpty()) {
                syncChaptersWithSource(database, chapters, local, source)
            }
        }
        local
    }
}

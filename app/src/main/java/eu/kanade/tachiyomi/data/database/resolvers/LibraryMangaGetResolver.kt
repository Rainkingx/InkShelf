package eu.kanade.tachiyomi.data.database.resolvers

import android.database.Cursor
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import eu.kanade.tachiyomi.data.database.mappers.BaseMangaGetResolver
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.util.chapter.ChapterUtil

class LibraryMangaGetResolver :
    DefaultGetResolver<LibraryManga>(),
    BaseMangaGetResolver {
    companion object {
        val INSTANCE = LibraryMangaGetResolver()
    }

    override fun mapFromCursor(cursor: Cursor): LibraryManga {
        val manga = LibraryManga()

        mapBaseFromCursor(manga, cursor)
        manga.unread =
            cursor.resolveChapterCount(manga, MangaTable.COL_UNREAD_COUNT, MangaTable.COL_UNREAD)
        manga.category = cursor.getInt(cursor.getColumnIndex(MangaTable.COL_CATEGORY))
        manga.read =
            cursor.resolveChapterCount(manga, MangaTable.COL_READ_COUNT, MangaTable.COL_HAS_READ)
        manga.bookmarkCount = cursor.getInt(cursor.getColumnIndex(MangaTable.COL_BOOKMARK_COUNT))
        manga.score = cursor.getFloat(cursor.getColumnIndex(MangaTable.COL_SCORE))

        return manga
    }

    /**
     * The query already computes a plain COUNT(*) for the common case. Scanlator names are only
     * concatenated (and thus only need parsing here) for manga that actually have a per-manga
     * scanlator filter set - see [eu.kanade.tachiyomi.data.database.queries.libraryQuery].
     */
    private fun Cursor.resolveChapterCount(
        manga: LibraryManga,
        countColumn: String,
        scanlatorListColumn: String,
    ): Int {
        if (manga.filtered_scanlators.isNullOrBlank()) {
            return getInt(getColumnIndex(countColumn))
        }
        return getString(getColumnIndex(scanlatorListColumn)).filterChaptersByScanlators(manga)
    }

    private fun String.filterChaptersByScanlators(manga: LibraryManga): Int {
        if (isEmpty()) return 0
        val list = split(" [.] ")
        val filteredScanlators = ChapterUtil.getScanlators(manga.filtered_scanlators)
        return list.count { ChapterUtil.getScanlators(it).none { group -> filteredScanlators.contains(group) } }
    }
}

package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slowdowns the app. The cache is invalidated by the time
 * defined in [renewInterval] as we don't have any control over the filesystem and the user can
 * delete the folders at any time without the app noticing.
 *
 * @param context the application context.
 * @param provider the downloads directories provider.
 * @param sourceManager the source manager.
 * @param preferences the preferences of the app.
 */
class DownloadCache(
    private val context: Context,
    private val provider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val preferences: PreferencesHelper = Injekt.get(),
    private val protoBuf: ProtoBuf = Injekt.get(),
) {
    /**
     * The interval after which this cache should be invalidated. 1 hour shouldn't cause major
     * issues, as the cache is only used for UI feedback.
     */
    private val renewInterval = TimeUnit.HOURS.toMillis(1)

    /**
     * The last time the cache was refreshed.
     */
    @Volatile
    private var lastRenew = 0L

    /**
     * The in-flight renewal job, if any. Guards against overlapping tree walks when
     * [checkRenew] and [forceRenewCache] are triggered close together from different threads.
     */
    private var renewJob: Job? = null

    /**
     * Serialises [renew] against the incremental mutators. [renew] replaces whole per-manga sets
     * from a directory listing, so an [addChapter] landing mid-scan would be dropped by a listing
     * that predates the file - which is why [checkRenew] and the mutators shared a monitor before
     * renewal moved off the caller's thread. Reads are deliberately left unguarded; they only
     * touch [ConcurrentHashMap] and must never block the thread binding the UI.
     */
    private val renewLock = Any()

    /**
     * File names are stored lowercased so lookups can use direct hash membership instead of
     * case-insensitive linear scans. Both the outer map and the per-manga sets are backed by
     * [ConcurrentHashMap] since reads happen on whichever thread is binding UI while writes
     * happen from the downloader and the renewal job.
     */
    private var mangaFiles: MutableMap<Long, MutableSet<String>> = ConcurrentHashMap()

    private val _isInitialized = MutableStateFlow(false)

    /**
     * True once the first scan of the downloads directory has completed. Reads made before this
     * flips true (e.g. right after a cold app start) reflect an empty cache, not "nothing is
     * downloaded" - callers that need to distinguish those two cases should check this first.
     */
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    // SupervisorJob so a failure in one child (a renewal that hits a revoked SAF grant, a
    // missing directory, a DB error) doesn't cancel this scope and silently kill every future
    // renewal, disk write and preference collector for the rest of the process' life.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Rebuilt from a full filesystem scan every time it goes stale, but that scan is expensive -
     * this snapshot lets a cold app start skip it entirely and reuse whatever was last known,
     * same as a normal renewal would until the next [renewInterval] elapses.
     */
    private val diskCacheFile: File
        get() = File(context.cacheDir, "dl_index_cache")

    init {
        scope.launch { loadDiskCache() }
        preferences
            .downloadsDirectory()
            .asFlow()
            .drop(1)
            .onEach {
                lastRenew = 0L // invalidate cache
                diskCacheFile.delete()
            }.launchIn(scope)
    }

    private fun loadDiskCache() {
        if (!diskCacheFile.exists()) return
        try {
            val snapshot =
                diskCacheFile.inputStream().use {
                    protoBuf.decodeFromByteArray<DiskCacheSnapshot>(it.readBytes())
                }
            snapshot.mangaFiles.forEach { (id, files) ->
                mangaFiles[id] = files.toCollection(ConcurrentHashMap.newKeySet())
            }
            lastRenew = System.currentTimeMillis()
            _isInitialized.value = true
        } catch (e: Exception) {
            diskCacheFile.delete()
        }
    }

    private var persistJob: Job? = null

    // Reached from the downloader, UI-triggered deletes and the renewal job, so the
    // cancel-then-relaunch has to be atomic - otherwise two writers can both survive the cancel
    // and interleave their writeBytes() into the same file.
    @Synchronized
    private fun persistToDiskDebounced() {
        persistJob?.cancel()
        persistJob =
            scope.launch {
                delay(1000)
                ensureActive()
                try {
                    val snapshot = DiskCacheSnapshot(mangaFiles.mapValues { it.value.toList() })
                    val bytes = protoBuf.encodeToByteArray(snapshot)
                    ensureActive()
                    diskCacheFile.writeBytes(bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Best-effort: a stale or missing disk cache just means the next launch rescans.
                }
            }
    }

    /**
     * Returns the downloads directory from the user's preferences.
     */
    private fun getDirectoryFromPreference(): UniFile {
        val dir = preferences.downloadsDirectory().get()
        return UniFile.fromUri(context, dir.toUri())
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapter the chapter to check.
     * @param manga the manga of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapter: Chapter,
        manga: Manga,
        skipCache: Boolean,
    ): Boolean {
        if (skipCache) {
            val source = sourceManager.get(manga.source) ?: return false
            return provider.findChapterDir(chapter, manga, source) != null
        }

        checkRenew()

        val files = mangaFiles[manga.id] ?: return false
        return provider.getValidChapterDirNames(chapter).any { chapName ->
            val lowerName = chapName.lowercase()
            files.contains(lowerName) || files.contains("$lowerName.cbz")
        }
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(
        manga: Manga,
        forceCheckFolder: Boolean = false,
    ): Int {
        checkRenew()

        if (forceCheckFolder) {
            val source = sourceManager.get(manga.source) ?: return 0
            val mangaDir = provider.findMangaDir(manga, source)

            if (mangaDir != null) {
                val listFiles =
                    mangaDir.listFiles { _, filename -> !filename.endsWith(Downloader.TMP_DIR_SUFFIX) }
                if (!listFiles.isNullOrEmpty()) {
                    return listFiles.size
                }
            }
            return 0
        } else {
            val files = mangaFiles[manga.id] ?: return 0
            return files.count { !it.endsWith(Downloader.TMP_DIR_SUFFIX) }
        }
    }

    /**
     * Checks if the cache needs a renewal and triggers it in the background if so. Never blocks
     * the calling thread — this cache is only used for UI feedback, so a brief staleness window
     * while renewal runs is preferable to stalling whatever screen is reading it.
     */
    private fun checkRenew() = launchRenewIfNeeded(force = false)

    fun forceRenewCache() = launchRenewIfNeeded(force = true)

    /**
     * Suspends until the first scan of the downloads directory has completed, triggering one if
     * none is running or scheduled yet. Returns immediately if already initialized.
     */
    suspend fun awaitInitialScan() {
        launchRenewIfNeeded(force = false)
        isInitialized.first { it }
    }

    @Synchronized
    private fun launchRenewIfNeeded(force: Boolean) {
        if (renewJob?.isActive == true) return
        if (!force && lastRenew + renewInterval >= System.currentTimeMillis()) return
        lastRenew = System.currentTimeMillis()
        renewJob =
            scope.launch {
                try {
                    synchronized(renewLock) { renew() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Flipping the flag below anyway: callers block on it, so swallowing the
                    // failure silently would leave them waiting forever with the UI stuck
                    // mid-scan. A failed scan just means an empty cache until the next renewal.
                    Timber.e(e, "Failed to renew the download cache")
                }
                _isInitialized.value = true
                persistToDiskDebounced()
            }
    }

    /**
     * Renews the downloads cache. Must be called holding [renewLock].
     */
    private fun renew() {
        val onlineSources = sourceManager.getOnlineSources()

        val sourceDirs =
            getDirectoryFromPreference()
                .listFiles()
                .orEmpty()
                .associate { it.name to SourceDirectory(it) }
                .mapNotNullKeys { entry ->
                    onlineSources.find { provider.getSourceDirName(it).equals(entry.key, ignoreCase = true) }?.id
                }

        val db: DatabaseHelper by injectLazy()
        val mangas = db.getMangas().executeAsBlocking().groupBy { it.source }

        sourceDirs.forEach { sourceValue ->
            val sourceMangaRaw = mangas[sourceValue.key]?.toMutableSet() ?: return@forEach

            // Favorites first so they win the lookup on a duplicate-title collision.
            val mangaByDirName = LinkedHashMap<String, Manga>()
            sourceMangaRaw
                .sortedByDescending { it.favorite }
                .forEach { manga ->
                    val key = DiskUtil.buildValidFilename(manga.originalTitle).lowercase()
                    mangaByDirName.putIfAbsent(key, manga)
                }

            val sourceDir = sourceValue.value

            val mangaDirs =
                sourceDir.dir
                    .listFiles()
                    .orEmpty()
                    .mapNotNull { mangaDir ->
                        val name = mangaDir.name ?: return@mapNotNull null
                        val chapterDirs =
                            mangaDir
                                .listFiles()
                                .orEmpty()
                                .mapNotNull { chapterFile ->
                                    chapterFile.name?.substringBeforeLast(".cbz")?.lowercase()
                                }.toCollection(ConcurrentHashMap.newKeySet())
                        name to MangaDirectory(mangaDir, chapterDirs)
                    }.toMap()

            val trueMangaDirs =
                mangaDirs
                    .mapNotNull { mangaDir ->
                        val manga = mangaByDirName[mangaDir.key.lowercase()] ?: return@mapNotNull null
                        val id = manga.id ?: return@mapNotNull null
                        id to mangaDir.value.files
                    }.toMap()

            // Evict manga we know belong to this source but no longer have an on-disk folder,
            // so deletions made outside the app (or by a previous cleanup) don't linger forever.
            val knownMangaIds = sourceMangaRaw.mapNotNull { it.id }
            val staleMangaIds = knownMangaIds.filter { it !in trueMangaDirs }
            staleMangaIds.forEach { mangaFiles.remove(it) }

            mangaFiles.putAll(trueMangaDirs)
        }
    }

    /**
     * Adds a chapter that has just been download to this cache.
     *
     * @param chapterDirName the downloaded chapter's directory name.
     * @param mangaUniFile the directory of the manga.
     * @param manga the manga of the chapter.
     */
    fun addChapter(
        chapterDirName: String,
        manga: Manga,
    ) {
        val id = manga.id ?: return
        synchronized(renewLock) {
            mangaFiles
                .computeIfAbsent(id) { ConcurrentHashMap.newKeySet() }
                .add(chapterDirName.lowercase())
        }
        persistToDiskDebounced()
    }

    /**
     * Removes a list of chapters that have been deleted from this cache.
     *
     * @param chapters the list of chapter to remove.
     * @param manga the manga of the chapter.
     */
    fun removeChapters(
        chapters: List<Chapter>,
        manga: Manga,
    ) {
        val id = manga.id ?: return
        synchronized(renewLock) {
            val files = mangaFiles[id] ?: return
            for (chapter in chapters) {
                provider.getValidChapterDirNames(chapter).forEach { fileName ->
                    files.remove(fileName.lowercase())
                }
            }
        }
        persistToDiskDebounced()
    }

    fun removeFolders(
        folders: List<String>,
        manga: Manga,
    ) {
        val id = manga.id ?: return
        synchronized(renewLock) {
            val files = mangaFiles[id] ?: return
            for (folder in folders) {
                files.remove(folder.lowercase())
            }
        }
        persistToDiskDebounced()
    }

/*fun renameFolder(from: String, to: String, source: Long) {
    val sourceDir = rootDir.files[source] ?: return
    val list = sourceDir.files.toMutableMap()
    val mangaFiles = sourceDir.files[DiskUtil.buildValidFilename(from)] ?: return
    val newFile = UniFile.fromFile(File(sourceDir.dir.filePath + "/" + DiskUtil
        .buildValidFilename(to))) ?: return
    val newDir = MangaDirectory(newFile)
    newDir.files = mangaFiles.files
    list.remove(DiskUtil.buildValidFilename(from))
    list[to] = newDir
    sourceDir.files = list
}*/

    /**
     * Removes a manga that has been deleted from this cache.
     *
     * @param manga the manga to remove.
     */
    fun removeManga(manga: Manga) {
        synchronized(renewLock) { mangaFiles.remove(manga.id) }
        persistToDiskDebounced()
    }

    /**
     * On-disk snapshot of [mangaFiles], written debounced on every change and read back on init
     * so a cold app start can skip the filesystem scan entirely if a recent one exists.
     */
    @Serializable
    private data class DiskCacheSnapshot(
        val mangaFiles: Map<Long, List<String>> = emptyMap(),
    )

    /**
     * Class to store the files under the root downloads directory.
     */
    private class RootDirectory(
        val dir: UniFile,
        var files: Map<Long, SourceDirectory> = hashMapOf(),
    )

    /**
     * Class to store the files under a source directory.
     */
    private class SourceDirectory(
        val dir: UniFile,
        var files: Map<Long, MutableSet<String>> = hashMapOf(),
    )

    /**
     * Class to store the files under a manga directory.
     */
    private class MangaDirectory(
        val dir: UniFile,
        var files: MutableSet<String> = hashSetOf(),
    )

    /**
     * Returns a new map containing only the key entries of [transform] that are not null.
     */
    private inline fun <K, V, R> Map<out K, V>.mapNotNullKeys(transform: (Map.Entry<K?, V>) -> R?): Map<R, V> {
        val destination = LinkedHashMap<R, V>()
        forEach { element -> transform(element)?.let { destination.put(it, element.value) } }
        return destination
    }

    /**
     * Returns a map from a list containing only the key entries of [transform] that are not null.
     */
    private inline fun <T, K, V> Array<T>.associateNotNullKeys(transform: (T) -> Pair<K?, V>): Map<K, V> {
        val destination = LinkedHashMap<K, V>()
        for (element in this) {
            val (key, value) = transform(element)
            if (key != null) {
                destination[key] = value
            }
        }
        return destination
    }
}

package eu.kanade.tachiyomi.data.image.coil

import coil.key.Keyer
import coil.request.Options
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.cache.CoverCache
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.util.storage.DiskUtil

class MangaCoverKeyer : Keyer<Manga> {
    private val coverCache: CoverCache by injectLazy()
    override fun key(
        data: Manga,
        options: Options,
    ): String? {
        val shouldFetchRemotely = options.networkCachePolicy.readEnabled && !options.diskCachePolicy.readEnabled
        if (!shouldFetchRemotely && data.id != null && (options.parameters.value(MangaCoverFetcher.useCustomCover) ?: true)) {
            val custom = coverCache.getCustomCoverFile(data)
            if (custom.exists()) {
                // Isolate local overrides from remote thumbnails and refresh
                // the image key whenever a different page becomes the cover.
                return "custom-cover:${data.id}:${custom.lastModified()}:${custom.length()}"
            }
        }
        if (data.thumbnail_url.isNullOrBlank()) return null
        return if (!data.favorite) {
            data.thumbnail_url!!
        } else {
            DiskUtil.hashKeyForDisk(data.thumbnail_url!!)
        }
    }
}

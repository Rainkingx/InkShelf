package eu.kanade.tachiyomi.ui.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import coil.Coil
import coil.dispose
import coil.request.CachePolicy
import coil.request.ImageRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.InkshelfPickedForYouControllerBinding
import eu.kanade.tachiyomi.databinding.InkshelfPickedForYouItemBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.main.InkShelfHeaderInterface
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * InkShelf's full recommendation feed. Discover remains the quick carousel/teaser; this screen
 * gives each recommendation enough room for the recommendation logic and a short comic synopsis.
 *
 * Comic Vine enrichment in this controller is intentionally an OPTIONAL TEST layer. InkShelf's
 * normal source metadata remains the fallback and Comic Vine is never required to open/read a title.
 */
class PickedForYouController : BaseController<InkshelfPickedForYouControllerBinding>(), InkShelfHeaderInterface {
    private val preferences: PreferencesHelper = Injekt.get()
    private val itemBindings = mutableMapOf<String, InkshelfPickedForYouItemBinding>()
    private var allRecommendations: List<DiscoverRecommendation> = emptyList()
    private var visibleRecommendationCount = INITIAL_PICK_COUNT
    private var isRefreshingRecommendations = false

    override fun getTitle() = ""

    override fun createBinding(inflater: LayoutInflater) = InkshelfPickedForYouControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.inkshelfPfyScroll, true)

        allRecommendations = BrowseController.pickedForYouSnapshot().shuffled()
        visibleRecommendationCount = INITIAL_PICK_COUNT.coerceAtMost(allRecommendations.size)
        val initialItems = allRecommendations.take(visibleRecommendationCount)

        binding.inkshelfPfyEmpty.isVisible = initialItems.isEmpty()
        binding.inkshelfPfyContent.isVisible = initialItems.isNotEmpty()

        if (initialItems.isNotEmpty()) {
            renderRecommendations(initialItems)
        }

        setupComicVineTest(initialItems)
        updateSeeMoreButton()

        binding.inkshelfPfySeeMore.setOnClickListener {
            showMoreRecommendations()
        }

        binding.inkshelfPfyRefresh.setOnClickListener {
            refreshRecommendationsInPlace()
        }
    }

    private fun refreshRecommendationsInPlace() {
        if (isRefreshingRecommendations) return

        val browseController =
            router.backstack
                .map { it.controller }
                .filterIsInstance<BrowseController>()
                .lastOrNull()
                ?: return

        isRefreshingRecommendations = true
        binding.inkshelfPfyRefresh.isEnabled = false
        binding.inkshelfPfyRefresh.text = "Refreshing…"

        browseController.refreshPickedForYouFromChild { freshItems ->
            if (!isBindingInitialized || view == null) return@refreshPickedForYouFromChild

            isRefreshingRecommendations = false
            binding.inkshelfPfyRefresh.isEnabled = true
            binding.inkshelfPfyRefresh.text = "Refresh Picks"

            if (freshItems.isEmpty()) return@refreshPickedForYouFromChild

            allRecommendations = freshItems
            visibleRecommendationCount = INITIAL_PICK_COUNT.coerceAtMost(freshItems.size)

            val visibleItems = freshItems.take(visibleRecommendationCount)
            binding.inkshelfPfyEmpty.isVisible = visibleItems.isEmpty()
            binding.inkshelfPfyContent.isVisible = visibleItems.isNotEmpty()

            renderRecommendations(visibleItems)
            setupComicVineTest(visibleItems)
            updateSeeMoreButton()
        }
    }

    override fun onDestroyView(view: View) {
        itemBindings.clear()
        super.onDestroyView(view)
    }

    private fun renderRecommendations(items: List<DiscoverRecommendation>) {
        val container = binding.inkshelfPfyItems
        container.removeAllViews()
        itemBindings.clear()

        items.forEach { item ->
            val itemBinding =
                InkshelfPickedForYouItemBinding.inflate(
                    LayoutInflater.from(container.context),
                    container,
                    false,
                )
            bindRecommendation(itemBinding, item)
            itemBindings[comicVineItemKey(item.manga.title)] = itemBinding
            container.addView(itemBinding.root)
        }
    }

    private fun appendRecommendations(items: List<DiscoverRecommendation>) {
        val container = binding.inkshelfPfyItems
        items.forEach { item ->
            val itemBinding =
                InkshelfPickedForYouItemBinding.inflate(
                    LayoutInflater.from(container.context),
                    container,
                    false,
                )
            bindRecommendation(itemBinding, item)
            itemBindings[comicVineItemKey(item.manga.title)] = itemBinding
            container.addView(itemBinding.root)
        }
    }

    private fun showMoreRecommendations() {
        if (visibleRecommendationCount >= allRecommendations.size) return

        val nextCount =
            (visibleRecommendationCount + PICKS_PER_PAGE)
                .coerceAtMost(allRecommendations.size)
        val nextItems = allRecommendations.subList(visibleRecommendationCount, nextCount)
        visibleRecommendationCount = nextCount

        appendRecommendations(nextItems)
        setupComicVineTest(nextItems)
        updateSeeMoreButton()
    }

    private fun updateSeeMoreButton() {
        if (!isBindingInitialized) return
        binding.inkshelfPfySeeMore.isVisible =
            allRecommendations.isNotEmpty() && visibleRecommendationCount < allRecommendations.size
    }

    private fun bindRecommendation(
        binding: InkshelfPickedForYouItemBinding,
        item: DiscoverRecommendation,
    ) {
        val manga = item.manga
        val kind = item.reason.substringBefore(" • ", "Picked for you").trim()
        val creator = creatorLine(manga)
        val about = aboutComic(manga)

        binding.inkshelfPfyKind.text = kind
        binding.inkshelfPfyTitle.text = manga.title
        binding.inkshelfPfyCreator.text = creator
        binding.inkshelfPfyCreator.isVisible = creator.isNotBlank()
        binding.inkshelfPfyAbout.text = about
        binding.inkshelfPfyComicVineMeta.isVisible = false

        binding.inkshelfPfyCover.dispose()
        binding.inkshelfPfyCover.alpha = 1f
        if (!manga.thumbnail_url.isNullOrBlank()) {
            val coverRequest =
                ImageRequest.Builder(binding.root.context)
                    .data(manga)
                    .placeholder(android.R.color.transparent)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .target(CoverViewTarget(binding.inkshelfPfyCover, binding.inkshelfPfyCoverProgress))
                    .setParameter(MangaCoverFetcher.useCustomCover, false)
                    .crossfade(false)
                    .build()
            Coil.imageLoader(binding.root.context).enqueue(coverRequest)
        } else {
            binding.inkshelfPfyCoverProgress.isVisible = false
        }

        binding.root.setOnClickListener { openManga(manga) }
    }

    private fun setupComicVineTest(items: List<DiscoverRecommendation>) {
        val context = binding.root.context
        val key = comicVinePreferences(context).getString(COMIC_VINE_KEY, null).orEmpty().trim()
        if (key.isNotBlank() && items.isNotEmpty()) {
            enrichFromComicVine(items, key)
        }
    }

    private fun updateComicVineHeader(context: Context) = Unit

    @Suppress("unused")
    private fun showComicVineKeyDialog(
        context: Context,
        items: List<DiscoverRecommendation>,
    ) {
        val prefs = comicVinePreferences(context)
        val existing = prefs.getString(COMIC_VINE_KEY, null).orEmpty()
        val input =
            EditText(context).apply {
                hint = context.getString(R.string.inkshelf_cv_key_hint)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(existing)
                setSelection(text.length)
            }

        val dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.inkshelf_cv_dialog_title)
                .setMessage(R.string.inkshelf_cv_dialog_message)
                .setView(input)
                .setPositiveButton(R.string.inkshelf_cv_save_and_test) { _, _ ->
                    val newKey = input.text?.toString()?.trim().orEmpty()
                    if (newKey.isBlank()) {
                        prefs.edit().remove(COMIC_VINE_KEY).apply()
                        comicVineSessionCache.clear()
                        resetComicVineLabels()
                        updateComicVineHeader(context)
                        return@setPositiveButton
                    }

                    prefs.edit().putString(COMIC_VINE_KEY, newKey).apply()
                    updateComicVineHeader(context)
                    enrichFromComicVine(items, newKey, force = true)
                }.setNegativeButton(android.R.string.cancel, null)

        if (existing.isNotBlank()) {
            dialog.setNeutralButton(R.string.inkshelf_cv_remove_key) { _, _ ->
                prefs.edit().remove(COMIC_VINE_KEY).apply()
                comicVineSessionCache.clear()
                resetComicVineLabels()
                updateComicVineHeader(context)
            }
        }

        dialog.show()
    }

    private fun enrichFromComicVine(
        items: List<DiscoverRecommendation>,
        apiKey: String,
        force: Boolean = false,
    ) {
        if (items.isEmpty() || apiKey.isBlank()) return

        if (force) {
            comicVineSessionCache.clear()
            resetComicVineLabels()
        }

        viewScope.launchIO {
            items.forEach { item ->
                val title = item.manga.title
                val cacheKey = comicVineItemKey(title)
                val metadata =
                    if (!force && comicVineSessionCache.containsKey(cacheKey)) {
                        comicVineSessionCache[cacheKey]
                    } else {
                        fetchComicVineVolume(item.manga, apiKey).also { result ->
                            comicVineSessionCache[cacheKey] = result
                        }
                    }

                withUIContext {
                    if (!isBindingInitialized) return@withUIContext
                    metadata?.let { applyComicVineMetadata(cacheKey, it) }
                }
            }
        }
    }

    private fun applyComicVineMetadata(
        cacheKey: String,
        metadata: ComicVineMetadata,
    ) {
        val itemBinding = itemBindings[cacheKey] ?: return

        if (metadata.description.isNotBlank()) {
            itemBinding.inkshelfPfyAbout.text = metadata.description
        }

        val existingCreators =
            itemBinding.inkshelfPfyCreator.text
                ?.toString()
                .orEmpty()
                .split(" • ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        val mergedCreators =
            (existingCreators + metadata.creators)
                .distinctBy { normalizePersonName(it) }
                .take(3)
        itemBinding.inkshelfPfyCreator.text = mergedCreators.joinToString(" • ")
        itemBinding.inkshelfPfyCreator.isVisible = mergedCreators.isNotEmpty()

        val issueLabel =
            metadata.issueCount
                .takeIf { it > 0 }
                ?.let { count -> if (count == 1) "1 issue" else "$count issues" }
        val metadataParts =
            listOfNotNull(
                metadata.publisher.takeIf { it.isNotBlank() },
                metadata.startYear.takeIf { it.isNotBlank() },
                issueLabel,
            )

        itemBinding.inkshelfPfyComicVineMeta.text =
            if (metadataParts.isEmpty()) {
                "Comic Vine ↗"
            } else {
                "${metadataParts.joinToString(" • ")}  ·  Comic Vine ↗"
            }
        itemBinding.inkshelfPfyComicVineMeta.isVisible = true
        itemBinding.inkshelfPfyComicVineMeta.isClickable = metadata.siteUrl.isNotBlank()
        itemBinding.inkshelfPfyComicVineMeta.setOnClickListener(
            metadata.siteUrl.takeIf { it.isNotBlank() }?.let { siteUrl ->
                View.OnClickListener {
                    runCatching {
                        itemBinding.root.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl)),
                        )
                    }
                }
            },
        )
    }

    private fun resetComicVineLabels() {
        itemBindings.values.forEach { itemBinding ->
            itemBinding.inkshelfPfyComicVineMeta.isVisible = false
            itemBinding.inkshelfPfyComicVineMeta.isClickable = false
            itemBinding.inkshelfPfyComicVineMeta.setOnClickListener(null)
        }
        BrowseController
            .pickedForYouSnapshot()
            .forEach { item ->
                val itemBinding = itemBindings[comicVineItemKey(item.manga.title)] ?: return@forEach
                val creators = creatorLine(item.manga)
                itemBinding.inkshelfPfyAbout.text = aboutComic(item.manga)
                itemBinding.inkshelfPfyCreator.text = creators
                itemBinding.inkshelfPfyCreator.isVisible = creators.isNotBlank()
            }
    }

    private fun fetchComicVineVolume(
        manga: Manga,
        apiKey: String,
    ): ComicVineMetadata? {
        val requestedTitle = comicVineSearchTitle(manga.title)
        if (requestedTitle.isBlank()) return null

        val query = URLEncoder.encode(requestedTitle, Charsets.UTF_8.name())
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        val searchUrl =
            URL(
                "https://comicvine.gamespot.com/api/search/" +
                    "?api_key=$encodedKey" +
                    "&format=json" +
                    "&resources=volume" +
                    "&limit=50" +
                    "&field_list=id,name,deck,start_year,publisher,site_detail_url,count_of_issues" +
                    "&query=$query",
            )

        val root = fetchComicVineJson(searchUrl) ?: return null
        val results = root.optJSONArray("results") ?: return null
        val preliminary = mutableListOf<ScoredComicVineCandidate>()

        for (i in 0 until results.length()) {
            val candidate = results.optJSONObject(i) ?: continue
            val score = comicVineCandidateScore(manga, candidate)
            if (score >= COMIC_VINE_MIN_SEARCH_SCORE) {
                preliminary += ScoredComicVineCandidate(candidate, score)
            }
        }

        if (preliminary.isEmpty()) return null

        val detailed =
            preliminary
                .sortedByDescending { it.score }
                .take(COMIC_VINE_DETAIL_CANDIDATES)
                .mapNotNull { scored ->
                    val id = scored.json.optInt("id", 0)
                    if (id <= 0) return@mapNotNull null
                    val detail = fetchComicVineVolumeDetails(id, encodedKey) ?: scored.json
                    val finalScore = scored.score + comicVineDetailBonus(manga, detail)
                    ComicVineMatch(parseComicVineMetadata(detail), finalScore)
                }.filter { it.metadata.id > 0 }
                .sortedByDescending { it.score }

        val best = detailed.firstOrNull() ?: return null
        if (best.score < COMIC_VINE_MIN_FINAL_SCORE) return null

        // If two editions are essentially tied and we have no year/creator/publisher evidence,
        // falling back to source metadata is safer than confidently attaching the wrong edition.
        val second = detailed.getOrNull(1)
        val hasStrongSourceEvidence =
            comicVineRequestedYear(manga.title) != null ||
                creatorNames(manga).isNotEmpty() ||
                sourcePublisherHint(manga).isNotBlank()
        if (!hasStrongSourceEvidence && second != null && best.score - second.score < 8) return null

        return best.metadata
    }

    private fun fetchComicVineVolumeDetails(
        id: Int,
        encodedKey: String,
    ): JSONObject? {
        val detailUrl =
            URL(
                "https://comicvine.gamespot.com/api/volume/4050-$id/" +
                    "?api_key=$encodedKey" +
                    "&format=json" +
                    "&field_list=id,name,aliases,deck,description,start_year,publisher,site_detail_url," +
                    "count_of_issues,person_credits",
            )
        return fetchComicVineJson(detailUrl)?.optJSONObject("results")
    }

    private fun fetchComicVineJson(url: URL): JSONObject? {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
            connection.setRequestProperty(
                "User-Agent",
                "InkShelf-ComicVine-Test/1.2 Android non-commercial personal test",
            )
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).takeIf { it.optInt("status_code", 0) == 1 }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun comicVineCandidateScore(
        manga: Manga,
        candidate: JSONObject,
    ): Int {
        val requested = normalizeComicTitle(comicVineSearchTitle(manga.title))
        val candidateTitle = normalizeComicTitle(candidate.optString("name"))
        var score = comicVineTitleScore(requested, candidateTitle)
        if (score <= 0) return 0

        val requestedYear = comicVineRequestedYear(manga.title)
        val candidateYear = candidate.optString("start_year").trim().toIntOrNull()
        if (requestedYear != null && candidateYear != null) {
            if (requestedYear != candidateYear) return 0
            score += 45
        } else if (requestedYear != null) {
            score -= 5
        }

        val issueCount = candidate.optInt("count_of_issues", 0)
        if (comicVineLooksOngoing(manga.title)) {
            score += if (issueCount > 1) 12 else if (issueCount == 1) -18 else 0
        }

        val description =
            cleanComicVineText(candidate.optString("description"))
                .ifBlank { cleanComicVineText(candidate.optString("deck")) }
        if (description.length >= 80) score += 5
        if (looksLikeTranslatedOrForeignEdition(description)) score -= 30

        val publisherHint = sourcePublisherHint(manga)
        val candidatePublisher = candidate.optJSONObject("publisher")?.optString("name").orEmpty()
        if (publisherHint.isNotBlank() && candidatePublisher.isNotBlank()) {
            score += if (publisherNamesMatch(publisherHint, candidatePublisher)) 30 else -30
        }

        return score
    }

    private fun comicVineDetailBonus(
        manga: Manga,
        detail: JSONObject,
    ): Int {
        var score = 0
        val sourceCreators = creatorNames(manga).map(::normalizePersonName).filter { it.isNotBlank() }.toSet()
        val comicVineCreators = comicVinePeople(detail).map(::normalizePersonName).filter { it.isNotBlank() }.toSet()

        if (sourceCreators.isNotEmpty() && comicVineCreators.isNotEmpty()) {
            val matches =
                sourceCreators.count { sourceCreator ->
                    comicVineCreators.any { cvCreator ->
                        sourceCreator == cvCreator ||
                            sourceCreator.contains(cvCreator) ||
                            cvCreator.contains(sourceCreator)
                    }
                }
            score += when {
                matches >= 2 -> 50
                matches == 1 -> 28
                else -> -20
            }
        }

        val description =
            cleanComicVineText(detail.optString("description"))
                .ifBlank { cleanComicVineText(detail.optString("deck")) }
        if (description.length >= 120) score += 8
        if (looksLikeTranslatedOrForeignEdition(description)) score -= 35

        val issueCount = detail.optInt("count_of_issues", 0)
        if (comicVineLooksOngoing(manga.title)) {
            score += if (issueCount > 1) 10 else if (issueCount == 1) -15 else 0
        }

        return score
    }

    private fun parseComicVineMetadata(detail: JSONObject): ComicVineMetadata {
        val description =
            cleanComicVineText(detail.optString("description"))
                .ifBlank { cleanComicVineText(detail.optString("deck")) }
        val publisher =
            detail
                .optJSONObject("publisher")
                ?.optString("name")
                .orEmpty()
                .trim()

        return ComicVineMetadata(
            id = detail.optInt("id", 0),
            name = detail.optString("name").trim(),
            description = description,
            publisher = publisher,
            startYear = detail.optString("start_year").trim(),
            issueCount = detail.optInt("count_of_issues", 0),
            creators = comicVinePeople(detail).take(4),
            siteUrl = detail.optString("site_detail_url").trim(),
        )
    }

    private fun comicVinePeople(detail: JSONObject): List<String> {
        val people = detail.optJSONArray("person_credits") ?: return emptyList()
        return buildList {
            for (i in 0 until people.length()) {
                val name = people.optJSONObject(i)?.optString("name").orEmpty().trim()
                if (name.isNotBlank()) add(name)
            }
        }.distinctBy { normalizePersonName(it) }
    }

    private fun comicVineTitleScore(
        requested: String,
        candidate: String,
    ): Int {
        if (requested.isBlank() || candidate.isBlank()) return 0
        if (requested == candidate) return 100
        if (candidate.contains(requested) || requested.contains(candidate)) return 76

        val requestedTokens = requested.split(" ").filter { it.length > 1 }.toSet()
        val candidateTokens = candidate.split(" ").filter { it.length > 1 }.toSet()
        if (requestedTokens.isEmpty() || candidateTokens.isEmpty()) return 0

        val intersection = requestedTokens.intersect(candidateTokens).size
        val union = requestedTokens.union(candidateTokens).size
        return ((intersection.toDouble() / union.toDouble()) * 100).toInt()
    }

    private fun comicVineItemKey(title: String): String =
        "${normalizeComicTitle(comicVineSearchTitle(title))}|${comicVineRequestedYear(title) ?: 0}"

    private fun comicVineSearchTitle(title: String): String =
        title
            .replace(
                Regex("\\s*[\\(\\[]\\s*(?:19|20)\\d{2}\\s*(?:[-–—]\\s*)?[\\)\\]]\\s*$"),
                "",
            )
            .trim()
            .ifBlank { title.trim() }

    private fun comicVineRequestedYear(title: String): Int? =
        Regex("[\\(\\[]\\s*((?:19|20)\\d{2})\\s*(?:[-–—]\\s*)?[\\)\\]]\\s*$")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun comicVineLooksOngoing(title: String): Boolean =
        Regex("[\\(\\[]\\s*(?:19|20)\\d{2}\\s*[-–—]\\s*[\\)\\]]").containsMatchIn(title)

    private fun normalizeComicTitle(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\b(?:19|20)\\d{2}\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun normalizePersonName(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun looksLikeTranslatedOrForeignEdition(description: String): Boolean {
        if (description.isBlank()) return false
        val value = description.lowercase(Locale.ROOT)
        return COMIC_VINE_FOREIGN_EDITION_MARKERS.any { marker -> value.contains(marker) }
    }

    private fun sourcePublisherHint(manga: Manga): String {
        val text =
            buildString {
                append(manga.description.orEmpty())
                append(' ')
                append(manga.getGenres().orEmpty().joinToString(" "))
            }.lowercase(Locale.ROOT)
        return COMIC_VINE_PUBLISHER_ALIASES.entries
            .firstOrNull { (_, aliases) -> aliases.any { alias -> text.contains(alias) } }
            ?.key
            .orEmpty()
    }

    private fun publisherNamesMatch(
        expected: String,
        actual: String,
    ): Boolean {
        val expectedNormalized = normalizeComicTitle(expected)
        val actualNormalized = normalizeComicTitle(actual)
        if (expectedNormalized == actualNormalized) return true
        val aliases = COMIC_VINE_PUBLISHER_ALIASES[expected].orEmpty()
        return aliases.any { alias -> actual.lowercase(Locale.ROOT).contains(alias) }
    }

    private fun cleanComicVineText(value: String): String {
        if (value.isBlank() || value.equals("null", ignoreCase = true)) return ""
        return HtmlCompat
            .fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun comicVinePreferences(context: Context) =
        context.applicationContext.getSharedPreferences(COMIC_VINE_PREFS, Context.MODE_PRIVATE)

    private fun creatorNames(manga: Manga): List<String> =
        listOfNotNull(manga.author, manga.artist)
            .flatMap { it.split(Regex("[,;/&]")) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalizePersonName(it) }
            .take(4)

    private fun creatorLine(manga: Manga): String = creatorNames(manga).take(3).joinToString(" • ")

    private fun tagLine(manga: Manga): String =
        manga
            .getGenres()
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in setOf("comic", "comics", "manga") }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(4)
            .joinToString(" • ")

    private fun aboutComic(manga: Manga): String {
        val description =
            manga.description
                ?.takeIf { it.isNotBlank() }
                ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()

        if (description.isNotBlank()) return description

        val creator = creatorLine(manga)
        val genres = tagLine(manga)
        return when {
            creator.isNotBlank() && genres.isNotBlank() ->
                "A $genres title by $creator, selected from your enabled comic sources."
            genres.isNotBlank() ->
                "A $genres title selected from your enabled comic sources."
            creator.isNotBlank() ->
                "A title by $creator selected from your enabled comic sources."
            else ->
                "A title selected from your enabled comic sources as part of this recommendation mix."
        }
    }

    private fun openManga(manga: Manga) {
        preferences.lastUsedCatalogueSource().set(manga.source)
        router.pushController(MangaDetailsController(manga, true).withFadeTransaction())
    }

    private data class ComicVineMetadata(
        val id: Int,
        val name: String,
        val description: String,
        val publisher: String,
        val startYear: String,
        val issueCount: Int,
        val creators: List<String>,
        val siteUrl: String,
    )

    private data class ScoredComicVineCandidate(
        val json: JSONObject,
        val score: Int,
    )

    private data class ComicVineMatch(
        val metadata: ComicVineMetadata,
        val score: Int,
    )

    companion object {
        private const val COMIC_VINE_PREFS = "inkshelf_comicvine_test"
        private const val COMIC_VINE_KEY = "api_key"
        private const val COMIC_VINE_MIN_SEARCH_SCORE = 70
        private const val COMIC_VINE_MIN_FINAL_SCORE = 105
        private const val COMIC_VINE_DETAIL_CANDIDATES = 3
        private const val INITIAL_PICK_COUNT = 3
        private const val PICKS_PER_PAGE = 3

        private val COMIC_VINE_FOREIGN_EDITION_MARKERS =
            listOf(
                "french publication",
                "french edition",
                "german edition",
                "spanish edition",
                "italian edition",
                "portuguese edition",
                "dutch edition",
            )

        private val COMIC_VINE_PUBLISHER_ALIASES =
            linkedMapOf(
                "DC Comics" to listOf("dc comics", "dc black label"),
                "Marvel" to listOf("marvel comics", "marvel"),
                "Image Comics" to listOf("image comics"),
                "Skybound" to listOf("skybound"),
                "Dark Horse Comics" to listOf("dark horse"),
                "IDW Publishing" to listOf("idw publishing", "idw"),
                "BOOM! Studios" to listOf("boom! studios", "boom studios"),
                "Dynamite Entertainment" to listOf("dynamite entertainment"),
                "Valiant" to listOf("valiant comics", "valiant"),
            )

        /**
         * Session cache keeps us from spending API calls every time the user opens the page.
         * Nothing here is required for reading comics; source metadata remains the fallback.
         */
        private val comicVineSessionCache = mutableMapOf<String, ComicVineMetadata?>()
    }
}

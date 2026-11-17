package eu.kanade.tachiyomi.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.HomeControllerBinding
import eu.kanade.tachiyomi.databinding.HomeCoverCardBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.main.InkShelfHeaderInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.source.DiscoverRecommendation
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverCache
import eu.kanade.tachiyomi.ui.source.InkShelfDiscoverRefreshWorker
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * InkShelf's own Home root. The library and Discover remain separate tabs.
 *
 * Reuses genuine local reading history and the existing cached Discover
 * recommendations. No fictional placeholder covers, ratings or reading data.
 * Metron can enrich future recommendations without changing this layout.
 */
class HomeController : BaseController<HomeControllerBinding>(), InkShelfHeaderInterface {
    private val db: DatabaseHelper = Injekt.get()
    private var featured: List<DailyPick> = emptyList()
    private var heroIndex = 0
    private var dayTheme: Theme = themeForToday()

    private data class DailyPick(val manga: Manga, val label: String)
    private data class Theme(val title: String, val keywords: List<String>)

    override fun createBinding(inflater: LayoutInflater) = HomeControllerBinding.inflate(inflater)

    override fun getTitle(): String = "Home"

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Reserve the visible, existing InkShelf cosmic header instead of
        // drawing a duplicate masthead above it.
        val header = view.resources.getDimensionPixelSize(R.dimen.inkshelf_header_art_height)
        binding.homeScroll.setPadding(0, header + dp(18), 0, 0)
        dayTheme = themeForToday()
        bindThemeTitle()
        binding.homeThemeSubtitle.text = "Three real comics to explore today"

        binding.homeBrowseButton.setOnClickListener { saveFeaturedPick() }
        binding.homePicksLink.setOnClickListener { openTab(R.id.nav_browse) }
        binding.homeLibraryLink.setOnClickListener { openTab(R.id.nav_library) }
        binding.homeOpenPick.setOnClickListener { startFeaturedPick() }

        var gestureStart = 0f
        binding.homeHeroCover.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStart = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val distance = event.x - gestureStart
                    if (abs(distance) > dp(52) && featured.size > 1) {
                        showHero((heroIndex + if (distance < 0) 1 else featured.size - 1) % featured.size)
                    } else if (abs(distance) < dp(12)) {
                        featured.getOrNull(heroIndex)?.let { openDetails(it.manga) }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        val context = view.context.applicationContext
        InkShelfDiscoverRefreshWorker.schedule(context)
        viewScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val history = runCatching {
                    db.getRecentMangaLimit("", 0, false).executeAsBlocking()
                        .filter { it.manga.favorite }
                        .distinctBy { it.manga.id }
                        .take(12)
                }.getOrDefault(emptyList())
                val new = InkShelfDiscoverCache.load(context, InkShelfDiscoverCache.Section.NEW_THIS_WEEK, db)
                val trending = InkShelfDiscoverCache.load(context, InkShelfDiscoverCache.Section.TRENDING, db)
                val suggested = InkShelfDiscoverCache.load(context, InkShelfDiscoverCache.Section.PICKED_FOR_YOU, db)
                val library = runCatching { db.getLibraryMangas().executeAsBlocking() }.getOrDefault(emptyList())
                val picks = pickToday(new, trending, suggested, library, dayTheme)
                history to picks
            }
            if (!isBindingInitialized || view !== this@HomeController.view) return@launch
            bindHistory(loaded.first)
            featured = loaded.second
            bindPicks(featured)
            showHero(0)
        }
    }

    private fun pickToday(
        new: List<DiscoverRecommendation>,
        trending: List<DiscoverRecommendation>,
        suggested: List<DiscoverRecommendation>,
        library: List<out Manga>,
        theme: Theme,
    ): List<DailyPick> {
        val calendar = Calendar.getInstance()
        val daySeed = calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR)
        val candidates = mutableListOf<DailyPick>()
        val used = mutableSetOf<String>()
        fun add(pool: List<Manga>, label: String) {
            val choices = pool.sortedWith(
                compareByDescending<Manga> { manga ->
                    val text = (manga.title + " " + manga.genre.orEmpty()).lowercase(Locale.ROOT)
                    theme.keywords.count { it in text }
                }.thenBy { ((it.title.hashCode() * 31L) xor daySeed.toLong()) },
            )
            val next = choices.firstOrNull { candidate ->
                candidate.thumbnail_url?.isNotBlank() == true &&
                    candidate.title.lowercase(Locale.ROOT).trim() !in used
            } ?: return
            used += next.title.lowercase(Locale.ROOT).trim()
            candidates += DailyPick(next, label)
        }
        add(new.map { it.manga }, "New this week")
        add(trending.map { it.manga }, "Trending")
        add(suggested.map { it.manga }, "Suggested for you")
        // Show real library items when Discover hasn't refreshed yet. Don't
        // falsely call those items "trending" or "critically acclaimed".
        while (candidates.size < 3) {
            val oldSize = candidates.size
            add(library.filter { it.favorite }, "From your library")
            if (candidates.size == oldSize) break
        }
        return candidates.take(3)
    }

    private fun bindHistory(items: List<MangaChapterHistory>) {
        val row = binding.homeContinueRow
        row.removeAllViews()
        binding.homeContinueEmpty.isVisible = items.isEmpty()
        items.take(8).forEach { item ->
            val manga = item.manga
            val chapter = item.chapter
            val total = chapter.last_page_read + chapter.pages_left
            val progress = if (chapter.last_page_read > 0 && total > 0) {
                (((chapter.last_page_read + 1).toFloat() / total) * 100f).roundToInt().coerceIn(0, 100)
            } else null
            addCoverCard(row, manga, if (progress != null) "$progress% read" else "Continue reading", progress) {
                if (!chapter.read && chapter.id != null) {
                    startActivity(ReaderActivity.newIntent(itemViewContext(), manga, chapter))
                } else {
                    openDetails(manga)
                }
            }
        }
    }

    private fun bindPicks(picks: List<DailyPick>) {
        val row = binding.homePicksRow
        row.removeAllViews()
        binding.homePicksEmpty.isVisible = picks.isEmpty()
        picks.forEach { pick ->
            addCoverCard(row, pick.manga, pick.label, null) { openDetails(pick.manga) }
        }
    }

    private fun addCoverCard(
        row: LinearLayout,
        manga: Manga,
        description: String,
        progress: Int?,
        onClick: () -> Unit,
    ) {
        val card = HomeCoverCardBinding.inflate(LayoutInflater.from(row.context), row, false)
        card.homeCoverImage.loadManga(manga)
        card.homeCoverTitle.text = manga.title
        card.homeCoverDetail.text = description
        card.homeCoverProgressOverlay.isVisible = progress != null
        card.homeCoverTitle.isVisible = progress == null
        card.homeCoverDetail.isVisible = progress == null
        if (progress != null) {
            card.homeCoverProgress.progress = progress
            card.homeCoverPercent.text = "$progress%"
        }
        card.root.contentDescription = "${manga.title}${if (progress != null) ", $progress% read" else ", $description"}"
        card.root.setOnClickListener { onClick() }
        row.addView(card.root)
    }

    private fun showHero(position: Int) {
        heroIndex = position.coerceIn(0, (featured.size - 1).coerceAtLeast(0))
        val pick = featured.getOrNull(heroIndex)
        if (pick != null) {
            binding.homeHeroCover.loadManga(pick.manga)
            binding.homeThemeKicker.text = "DAILY COMICS  •  ${pick.label.uppercase(Locale.ROOT)}"
            binding.homeThemeSubtitle.text =
                "${featured.size} PICKS   •   " +
                    featured.joinToString("   •   ") { daily ->
                        when (daily.label) {
                            "New this week" -> "New"
                            "Trending" -> "Trending"
                            "Suggested for you" -> "For You"
                            else -> "From Library"
                        }
                    }
            binding.homeOpenPick.text = "▶  Read Now"
        } else {
            binding.homeHeroCover.setImageDrawable(null)
            binding.homeThemeKicker.text = "DAILY COMICS"
            binding.homeThemeSubtitle.text = "Explore real comics from your sources"
            binding.homeOpenPick.text = "Discover"
        }

        updateSaveButton()
        binding.homeHeroDots.removeAllViews()
        featured.forEachIndexed { index, _ ->
            val dot = View(itemViewContext())
            val selected = index == heroIndex
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(
                    if (selected) itemViewContext().getResourceColor(R.attr.colorPrimary)
                    else Color.argb(110, 155, 145, 170),
                )
            }
            dot.background = shape
            val width = if (selected) dp(17) else dp(7)
            val params = LinearLayout.LayoutParams(width, dp(7)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            binding.homeHeroDots.addView(dot, params)
            dot.setOnClickListener { showHero(index) }
        }
    }


    /** Split the featured day's name into the white/yellow two-line comic-book masthead. */
    private fun bindThemeTitle() {
        val words = dayTheme.title.trim().split(" ")
        binding.homeThemeTitle.text = words.dropLast(1).joinToString(" ")
        binding.homeThemeAccent.text = words.lastOrNull().orEmpty()
    }

    private fun updateSaveButton() {
        val pick = featured.getOrNull(heroIndex)
        binding.homeBrowseButton.text = when {
            pick == null -> "Discover"
            pick.manga.favorite -> "✓  In Library"
            else -> "+  Save"
        }
    }

    private fun startFeaturedPick() {
        val manga = featured.getOrNull(heroIndex)?.manga ?: run {
            openTab(R.id.nav_browse)
            return
        }
        viewScope.launch {
            val chapter = withContext(Dispatchers.IO) {
                runCatching {
                    val list = db.getChapters(manga).executeAsBlocking()
                    list.filterNot { it.read }
                        .minByOrNull { if (it.chapter_number >= 0f) it.chapter_number else Float.MAX_VALUE }
                        ?: list.firstOrNull()
                }.getOrNull()
            }
            if (!isBindingInitialized) return@launch
            if (chapter?.id != null) {
                startActivity(ReaderActivity.newIntent(itemViewContext(), manga, chapter))
            } else {
                // A freshly discovered series might not have fetched its chapters yet.
                openDetails(manga)
            }
        }
    }

    private fun saveFeaturedPick() {
        val manga = featured.getOrNull(heroIndex)?.manga ?: run {
            openTab(R.id.nav_browse)
            return
        }
        if (manga.favorite) {
            openTab(R.id.nav_library)
            return
        }
        binding.homeBrowseButton.isEnabled = false
        viewScope.launch {
            val added = withContext(Dispatchers.IO) {
                runCatching {
                    val savedManga = db.getManga(manga.url, manga.source).executeAsBlocking() ?: manga
                    if (savedManga.favorite) return@runCatching true
                    savedManga.favorite = true
                    savedManga.date_added = System.currentTimeMillis()
                    if (savedManga.id == null) {
                        val result = db.insertManga(savedManga).executeAsBlocking()
                        savedManga.id = result.insertedId()
                    } else {
                        db.updateMangaFavorite(savedManga).executeAsBlocking()
                        db.updateMangaAdded(savedManga).executeAsBlocking()
                    }
                    manga.favorite = true
                    true
                }.getOrDefault(false)
            }
            if (!isBindingInitialized) return@launch
            binding.homeBrowseButton.isEnabled = true
            updateSaveButton()
            Toast.makeText(
                itemViewContext(),
                if (added) "Saved to your library" else "Couldn't save comic. Try again.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun openDetails(manga: Manga) {
        router.pushController(MangaDetailsController(manga, fromCatalogue = !manga.favorite).withFadeTransaction())
    }

    private fun openTab(tab: Int) {
        (activity as? MainActivity)?.openInkShelfTab(tab)
    }

    private fun itemViewContext() = binding.root.context

    private fun dp(value: Int): Int =
        (value * binding.root.resources.displayMetrics.density).roundToInt()

    private fun themeForToday(): Theme = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> Theme("MUTANT MONDAY", listOf("x-men", "wolverine", "mutant", "x-force"))
        Calendar.TUESDAY -> Theme("INDIE TUESDAY", listOf("image comics", "dark horse", "boom", "idw", "indie"))
        Calendar.WEDNESDAY -> Theme("WHAT'S NEW WEDNESDAY", listOf("new", "absolute", "2026"))
        Calendar.THURSDAY -> Theme("THROWBACK THURSDAY", listOf("classic", "196", "197", "198", "199"))
        Calendar.FRIDAY -> Theme("FANTASTIC FRIDAY", listOf("fantastic", "magic", "fantasy", "cosmic"))
        Calendar.SATURDAY -> Theme("SPIDER-MAN SATURDAY", listOf("spider-man", "spiderman", "spider-verse", "venom"))
        else -> Theme("SUPER SUNDAY", listOf("superman", "batman", "wonder woman", "justice league"))
    }
}

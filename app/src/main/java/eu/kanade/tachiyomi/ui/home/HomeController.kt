package eu.kanade.tachiyomi.ui.home

import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import coil.load
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
    private val weekdays = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
    )
    private var featured: List<DailyPick> = emptyList() // Today's shelf, not the other days' slides
    private var weeklySlides: List<DailySlide> = weekdays.map { day ->
        DailySlide(day, themeForDay(day), emptyList())
    }
    private var heroIndex = 0
    private var themeArtworkFailed = false
    private var dayTheme: Theme = themeForToday()

    private data class DailyPick(val manga: Manga, val label: String)
    private data class Theme(val title: String, val keywords: List<String>)
    private data class DailySlide(val day: Int, val theme: Theme, val picks: List<DailyPick>)

    override fun createBinding(inflater: LayoutInflater) = HomeControllerBinding.inflate(inflater)

    override fun getTitle(): String = "Home"

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Reserve the visible, existing InkShelf cosmic header instead of
        // drawing a duplicate masthead above it.
        val header = view.resources.getDimensionPixelSize(R.dimen.inkshelf_home_header_height)
        // Home scrolls behind the fixed bottom navigation. Reserve a real
        // bottom scroll inset so the last shelf can be brought fully into view.
        binding.homeScroll.setPadding(0, header, 0, dp(124))
        binding.homeScroll.post {
            if (!isBindingInitialized) return@post
            // Content sits behind the activity's fixed bottom nav; measuring it
            // avoids clipping the final row on different Android screen sizes.
            val navHeight = (activity as? MainActivity)
                ?.findViewById<View>(R.id.bottom_nav)?.height?.takeIf { it > 0 } ?: dp(88)
            binding.homeScroll.setPadding(0, header, 0, navHeight + dp(32))
        }
        dayTheme = themeForToday()
        heroIndex = weekdays.indexOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)).coerceAtLeast(0)
        showHero(heroIndex)

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
                    if (abs(distance) > dp(52) && weeklySlides.size > 1) {
                        showHero(
                            (heroIndex + if (distance < 0) 1 else weeklySlides.size - 1) %
                                weeklySlides.size,
                        )
                    } else if (abs(distance) < dp(12)) {
                        currentHeroPick()?.let { openDetails(it.manga) }
                            ?: openTab(R.id.nav_browse)
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
                val slides = weekdays.map { day ->
                    val theme = themeForDay(day)
                    DailySlide(day, theme, pickToday(new, trending, suggested, library, theme))
                }
                history to slides
            }
            if (!isBindingInitialized || view !== this@HomeController.view) return@launch
            bindHistory(loaded.first)
            weeklySlides = loaded.second
            featured = weeklySlides.firstOrNull {
                it.day == Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            }?.picks.orEmpty()
            bindPicks(featured)
            showHero(heroIndex)
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
        // A high search rank is NOT proof that the comic belongs to today's
        // theme. Previously unrelated DC series appeared on Indie Tuesday.
        fun add(pool: List<Manga>, label: String) {
            if (candidates.size >= 3) return
            val choices = pool
                .asSequence()
                .filter { !it.thumbnail_url.isNullOrBlank() }
                .map { manga ->
                    manga to if (theme.title == "WHAT'S NEW WEDNESDAY" && label == "New this week") {
                        // NEW_THIS_WEEK is an explicitly date-filtered Discover shelf.
                        4
                    } else {
                        themeMatchScore(manga, theme)
                    }
                }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<Manga, Int>> { it.second }
                        .thenBy { (manga, _) ->
                            (manga.title.hashCode().toLong() * 31L) xor daySeed.toLong()
                        },
                )
            val next = choices.firstOrNull { (manga, _) ->
                manga.title.lowercase(Locale.ROOT).trim() !in used
            }?.first ?: return
            used += next.title.lowercase(Locale.ROOT).trim()
            candidates += DailyPick(next, label)
        }
        add(new.map { it.manga }, "New this week")
        add(trending.map { it.manga }, "Trending")
        add(suggested.map { it.manga }, "Recommended")
        // Only theme-appropriate comics can fill the spare slots. Never claim
        // a random series from the library is a featured indie or classic.
        for (item in library.filter { it.favorite }) {
            if (candidates.size >= 3) break
            add(listOf(item), "From your library")
        }
        return candidates.take(3)
    }

    /**
     * A conservative match is better than displaying a convincingly
     * presented but wrong recommendation. Generic genre tags (Action, Adventure)
     * do not establish a publisher or themed character.
     */
    private fun themeMatchScore(manga: Manga, theme: Theme): Int {
        val title = manga.title.lowercase(Locale.ROOT)
        val genres = listOfNotNull(manga.genre, manga.originalGenre).joinToString(" ").lowercase(Locale.ROOT)
        val searchable = "$title $genres"
        if (theme.title == "INDIE TUESDAY") {
            val majorPublisher = Regex(
                """\b(dc comics|marvel comics|dc universe|marvel universe|avengers|batman|wonder woman|superman|spider[- ]man|x[- ]men|harley quinn|martian manhunter)\b""",
            )
            if (majorPublisher.containsMatchIn(searchable)) return 0
            val independentPublishers = listOf(
                "image comics", "dark horse", "boom! studios", "boom studios",
                "idw publishing", "idw", "dynamite entertainment", "vault comics",
                "oni press", "mad cave", "aftershock", "indie", "independent comic",
            )
            val knownIndies = listOf(
                "saga", "spawn", "invincible", "monstress", "radiant black",
                "something is killing the children", "the walking dead",
                "descender", "east of west", "paper girls", "hellboy", "the foundry",
                "umbrella academy", "locke & key", "usagi yojimbo",
            )
            return when {
                independentPublishers.any { it in genres } -> 5
                knownIndies.any { name -> title == name || title.startsWith("$name (") || title.startsWith("$name:") } -> 3
                else -> 0
            }
        }
        if (theme.title == "WHAT'S NEW WEDNESDAY") {
            // Today's pool is already provided by Discover; here use only a
            // verified publication year/title or source-supplied new tag.
            val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
            return if (currentYear in title || "new release" in genres || "new this week" in genres) 2 else 0
        }
        return theme.keywords.count { keyword -> keyword in searchable }
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
        // Some history entries have no page-count metadata. Showing their
        // title underneath made the *entire* horizontal shelf taller, leaving
        // a large empty strip before Today's Picks. Every history card now
        // uses the same cover-only footprint, with a "Resume" fallback.
        val isContinueCard = row === binding.homeContinueRow
        card.homeCoverProgressOverlay.isVisible = isContinueCard
        card.homeCoverTitle.isVisible = !isContinueCard
        card.homeCoverDetail.isVisible = !isContinueCard
        card.homeCoverProgress.isVisible = progress != null
        if (progress != null) card.homeCoverProgress.progress = progress
        card.homeCoverPercent.text = if (progress != null) "$progress%" else "Resume"
        card.root.contentDescription = "${manga.title}${if (progress != null) ", $progress% read" else ", $description"}"
        card.root.setOnClickListener { onClick() }
        row.addView(card.root)
    }

    private fun currentHeroPick(): DailyPick? =
        weeklySlides.getOrNull(heroIndex)?.picks?.firstOrNull()

    /**
     * Seven always-available swipeable promo slides, one per weekday.
     * Artwork represents the theme; the button opens the explicitly named
     * featured comic (or Discover when no verified thematic match exists).
     */
    private fun showHero(position: Int) {
        if (!isBindingInitialized || weeklySlides.isEmpty()) return
        heroIndex = position.coerceIn(0, weeklySlides.lastIndex)
        val slide = weeklySlides[heroIndex]
        val pick = currentHeroPick()
        bindThemeTitle(slide.theme)
        bindThemeArtwork(slide.day, pick?.manga)

        binding.homeThemeKicker.text = if (slide.day == Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            "DAILY COMICS  •  TODAY"
        } else {
            "DAILY COMICS  •  WEEKDAY PREVIEW"
        }
        binding.homeThemeSubtitle.text = if (pick != null) {
            "${slide.picks.size} ${if (slide.picks.size == 1) "PICK" else "PICKS"}  •  ${pick.manga.title}"
        } else {
            "Explore comics for ${slide.theme.title.lowercase(Locale.ROOT)}"
        }
        binding.homeOpenPick.text = if (pick == null) "Discover Comics" else "▶  Read Now"
        binding.homeOpenPick.isEnabled = true
        updateSaveButton()
        binding.homeHeroCover.contentDescription =
            "${slide.theme.title}, swipe left or right to change the featured day"

        binding.homeHeroDots.removeAllViews()
        weeklySlides.forEachIndexed { index, _ ->
            val dot = View(itemViewContext())
            val selected = index == heroIndex
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(
                    if (selected) itemViewContext().getResourceColor(R.attr.colorPrimary)
                    else Color.argb(130, 165, 157, 180),
                )
            }
            dot.background = shape
            val width = if (selected) dp(18) else dp(7)
            val params = LinearLayout.LayoutParams(width, dp(7)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            dot.contentDescription = "Show ${weeklySlides[index].theme.title}"
            dot.setOnClickListener { showHero(index) }
            binding.homeHeroDots.addView(dot, params)
        }
    }

    /**
     * Each weekday uses a real source-hosted comic illustration with a source
     * link. Coil cancels earlier image requests if the user swipes quickly.
     */
    private fun bindThemeArtwork(day: Int, fallback: Manga?) {
        themeArtworkFailed = false
        val art = InkShelfDailyHeroArt.forDay(day)
        binding.homeHeroArtCredit.text = art.credit
        binding.homeHeroArtCredit.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(art.sourcePage)))
            }
        }
        binding.homeHeroCover.load(art.imageUrl) {
            crossfade(true)
            listener(
                onError = { _, _ ->
                    if (isBindingInitialized && weeklySlides.getOrNull(heroIndex)?.day == day) {
                        themeArtworkFailed = true
                        fallback?.let { binding.homeHeroCover.loadManga(it) }
                    }
                },
            )
        }
    }

    /** Two-line angled masthead remains over the full-bleed artwork. */
    private fun bindThemeTitle(theme: Theme) {
        val words = theme.title.trim().split(" ")
        binding.homeThemeTitle.text = words.dropLast(1).joinToString(" ")
        binding.homeThemeAccent.text = words.lastOrNull().orEmpty()
    }

    private fun updateSaveButton() {
        val pick = currentHeroPick()
        binding.homeBrowseButton.isEnabled = pick != null
        binding.homeBrowseButton.text = when {
            pick == null -> "No Pick"
            pick.manga.favorite -> "✓  In Library"
            else -> "+  Save"
        }
    }

    private fun startFeaturedPick() {
        val manga = currentHeroPick()?.manga ?: run {
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
        val manga = currentHeroPick()?.manga ?: run {
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
                    if (savedManga.favorite) {
                        manga.favorite = true
                        manga.id = savedManga.id
                        return@runCatching true
                    }
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
                    manga.id = savedManga.id
                    manga.date_added = savedManga.date_added
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

    private fun themeForToday(): Theme = themeForDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))

    private fun themeForDay(day: Int): Theme = when (day) {
        Calendar.MONDAY -> Theme("MUTANT MONDAY", listOf("x-men", "wolverine", "mutant", "x-force"))
        Calendar.TUESDAY -> Theme("INDIE TUESDAY", listOf("image comics", "dark horse", "boom", "idw", "indie"))
        Calendar.WEDNESDAY -> Theme("WHAT'S NEW WEDNESDAY", listOf("new", "absolute", "2026"))
        Calendar.THURSDAY -> Theme("THROWBACK THURSDAY", listOf("classic", "196", "197", "198", "199"))
        Calendar.FRIDAY -> Theme("FANTASTIC FRIDAY", listOf("fantastic", "magic", "fantasy", "cosmic"))
        Calendar.SATURDAY -> Theme("SPIDER-MAN SATURDAY", listOf("spider-man", "spiderman", "spider-verse", "venom"))
        else -> Theme("SUPER SUNDAY", listOf("superman", "batman", "wonder woman", "justice league"))
    }
}

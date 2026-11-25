package eu.kanade.tachiyomi.ui.inkshelf.shell

import eu.kanade.tachiyomi.ui.source.InkShelfRecommendationRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard against unrelated movie/franchise stories appearing beneath a comic.
 * These are pure headline-matching tests; live source/reader integration is
 * still validated by the normal Android build and device checks.
 */
class InkShelf2PulseMatcherTest {

    private fun item(title: String, date: Long = 1L, video: Boolean = false) =
        InkShelf2PulseArticle(
            title = title,
            url = "https://aiptcomics.com/${title.hashCode()}/",
            publisher = "Test publisher",
            publishedAt = date,
            imageUrl = null,
            isVideo = video,
        )

    @Test
    fun moreLikeThisDoesNotTreatTmntAsXMenBecauseBothAreMutants() {
        fun comic(title: String) =
            eu.kanade.tachiyomi.data.database.models.Manga.create(
                "/comics/${title.hashCode()}", title, 2002L,
            ).apply {
                thumbnail_url = "https://images.example.org/cover.jpg"
                genre = "Mutant, Action, Adventure"
            }

        val focus = comic("Teenage Mutant Ninja Turtles (2011)")
        val lastRonin = comic("Teenage Mutant Ninja Turtles: The Last Ronin (2020)")
        val xMen = comic("Midnight X-Men (2026)")
        val wolverine = comic("Ultimate Wolverine (2025)")
        val picks = InkShelf2MoreLikeThis.select(
            focus = focus,
            candidates = listOf(xMen, lastRonin, wolverine),
            enabledSources = setOf(2002L),
        )
        assertEquals(listOf(lastRonin.title), picks.map { it.manga.title })
        assertEquals("TMNT universe", picks.single().reason)
    }

    @Test
    fun manualRefreshUsesOlderHistoryOnlyAfterAllFreshChoices() {
        val pool = InkShelfRecommendationRotation.eligible(
            candidates = listOf("Batman", "Superman", "Daredevil", "X-Men"),
            titleKey = { it.lowercase() },
            recentHistory = setOf("batman", "superman", "daredevil"),
            visibleTitles = setOf("superman"),
        )
        assertEquals(listOf("X-Men"), pool.unseen)
        assertEquals(listOf("Batman", "Daredevil"), pool.older)
    }

    @Test
    fun refreshNeverRepeatsVisibleComicsOrMirroredSourceTitles() {
        data class Candidate(val title: String, val source: Long)
        val pool = InkShelfRecommendationRotation.eligible(
            candidates = listOf(
                Candidate("Absolute Batman", 1),
                Candidate("Absolute Batman", 2),
                Candidate("Wonder Woman", 2),
                Candidate("Superman", 2),
            ),
            titleKey = { it.title.lowercase() },
            recentHistory = setOf("wonder woman", "superman"),
            visibleTitles = setOf("absolute batman", "superman"),
        )
        assertTrue(pool.unseen.isEmpty())
        assertEquals(listOf("Wonder Woman"), pool.older.map { it.title })
    }

    @Test
    fun disabledExtensionCannotCountAsAHealthyRecommendationShelf() {
        val disabled = eu.kanade.tachiyomi.data.database.models.Manga.create(
            "/comic/from-xoxo", "Batman (2016)", 1001L,
        )
        val enabled = eu.kanade.tachiyomi.data.database.models.Manga.create(
            "/comic/from-batcave", "Superman (2023)", 2002L,
        )
        val oldCache = InkShelf2Engine.Discover(
            trending = listOf(disabled, enabled),
            newest = listOf(disabled),
            recommended = listOf(disabled),
            recommendationDetails = listOf(
                InkShelf2Engine.PersonalPick(disabled, "Old XOXO pick"),
            ),
        )
        val missing = InkShelf2RecommendationRecovery.missing(oldCache, setOf(2002L))
        assertTrue(missing.trending)
        assertTrue(missing.newest)
        assertTrue(missing.personal)
        assertTrue(missing.any)

        val restored = InkShelf2Engine.Discover(
            trending = listOf(enabled, enabled),
            newest = listOf(enabled, enabled),
            recommended = listOf(enabled, enabled),
            recommendationDetails = listOf(
                InkShelf2Engine.PersonalPick(
                    enabled, InkShelf2RecommendationRecovery.FALLBACK_REASON,
                ),
            ),
        )
        val needPersonal = InkShelf2RecommendationRecovery.missing(restored, setOf(2002L))
        assertTrue(needPersonal.personal)
        assertFalse(needPersonal.feeds)
        assertFalse(
            InkShelf2RecommendationRecovery.missing(oldCache, emptySet()).any,
        )
    }

    @Test
    fun pulseHeaderDotOnlyShowsNewsNewerThanLastVisit() {
        val entries = listOf(
            item("Older comic", 1_000L),
            item("Newer comic", 2_000L),
            item("Video update", 3_000L, video = true),
        )
        assertTrue(InkShelf2PulseStatus.hasNewerArticles(entries, 0L))
        assertTrue(InkShelf2PulseStatus.hasNewerArticles(entries, 1_500L))
        assertFalse(InkShelf2PulseStatus.hasNewerArticles(entries, 2_000L))
        assertFalse(InkShelf2PulseStatus.hasNewerArticles(entries, 5_000L))
        assertFalse(
            InkShelf2PulseStatus.hasNewerArticles(
                listOf(item("Only video", 5_000L, video = true)), 0L,
            ),
        )
        assertFalse(
            InkShelf2PulseStatus.hasNewerArticles(
                listOf(item("No publication date", 0L)), 0L,
            ),
        )
    }

    @Test
    fun pulseExtractsRealPublisherImagesWithoutPickingTrackers() {
        val feedHtml = """
            &lt;div&gt;
                &lt;img src="https://example.com/tracking-pixel.gif" /&gt;
                &lt;img data-lazy-src="https://bleedingcool.com/wp-content/uploads/2026/11/cover.webp?width=640&amp;amp;quality=80" /&gt;
            &lt;/div&gt;
        """.trimIndent()
        assertEquals(
            "https://bleedingcool.com/wp-content/uploads/2026/11/cover.webp?width=640&quality=80",
            InkShelf2PulseImages.fromFeedHtml(feedHtml),
        )
        val page = """
            <html><head>
                <meta property="og:title" content="Comic News">
                <meta property="og:image" content="https://bleedingcool.com/wp-content/uploads/2026/11/featured.jpg?x=1&amp;y=2">
                <meta name="twitter:image" content="https://bleedingcool.com/alternate.jpg">
            </head></html>
        """.trimIndent()
        assertEquals(
            "https://bleedingcool.com/wp-content/uploads/2026/11/featured.jpg?x=1&y=2",
            InkShelf2PulseImages.fromArticleMetadata(
                page, "https://bleedingcool.com/comics/example-story/",
            ),
        )
        assertEquals(
            null,
            InkShelf2PulseImages.fromArticleMetadata(
                """<meta property="og:image" content="http://bleedingcool.com/image.jpg">""",
                "https://bleedingcool.com/comics/example-story/",
            ),
        )
        assertEquals(null, InkShelf2PulseImages.fromFeedHtml("<p>Summary without pictures.</p>"))
    }

    @Test
    fun screenRantAppearsInHomePreviewAndEarlyPulseWithoutDuplication() {
        fun news(title: String, publisher: String) =
            item(title).copy(publisher = publisher)
        val originals = (1..15).map { index ->
            news("Other publisher story $index", if (index % 2 == 0) "The Beat" else "AIPT Comics")
        }.toMutableList()
        originals.add(12, news("Screen Rant comic headline", "Screen Rant"))
        originals.add(14, news("Screen Rant movie update", "Screen Rant"))
        val promoted = InkShelf2PulseTopics.promoteScreenRant(originals)
        assertEquals(originals.size, promoted.size)
        assertTrue(promoted.take(2).any { it.publisher == "Screen Rant" })
        assertEquals(2, promoted.take(8).count { it.publisher == "Screen Rant" })
        assertEquals(originals.map { it.url }.toSet(), promoted.map { it.url }.toSet())
        assertEquals(
            originals.filterNot { it.publisher == "Screen Rant" }.map { it.url },
            promoted.filterNot { it.publisher == "Screen Rant" }.map { it.url },
        )
    }

    @Test
    fun screenRantPromotionDoesNotInventStoriesWhenPublisherAbsentOrSparse() {
        val other = listOf(
            item("Comic update").copy(publisher = "The Beat"),
            item("DC industry news").copy(publisher = "AIPT Comics"),
        )
        assertEquals(other, InkShelf2PulseTopics.promoteScreenRant(other))
        val one = other + item("Screen Rant feature").copy(publisher = "Screen Rant")
        val promoted = InkShelf2PulseTopics.promoteScreenRant(one)
        assertEquals(1, promoted.count { it.publisher == "Screen Rant" })
        assertTrue(promoted.take(2).any { it.publisher == "Screen Rant" })
        assertEquals(one.map { it.url }.toSet(), promoted.map { it.url }.toSet())
    }

    @Test
    fun screenRantEditorialFeedsUseComicsAndFilmTvFiltering() {
        // A comics-specific feed establishes relevance even for headlines
        // that do not repeat the word "comic".
        assertEquals(
            InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopics.classify(
                "The Next Batman Storyline Changes Everything",
                dedicatedComicsFeed = true,
            ),
        )
        // The general RSS fallback must still match a comic franchise.
        assertEquals(
            InkShelf2PulseTopic.FILM_TV,
            InkShelf2PulseTopics.classify("Spider-Man: Brand New Day trailer details"),
        )
        assertEquals(
            null,
            InkShelf2PulseTopics.classify("Celebrity wedding trends for November"),
        )
    }

    @Test
    fun spotlightShowsSixActiveDistinctEventsAndKeepsDoomFirst() {
        val template = InkShelf2Spotlight.Event(
            id = "template", title = "Test spotlight", publisher = "Marvel",
            label = "FEATURED", summary = "Test event",
            officialUrl = "https://example.com",
            featuredFrom = "2026-12-01", expires = "2026-12-31",
            pulseKeywords = emptyList(),
            series = listOf(InkShelf2Spotlight.Series("series", "Test Comic", 2026, "Test")),
        )
        val active = (1..7).map { template.copy(id = "event-$it") }
        val doom = template.copy(id = "doom-essential")
        val future = template.copy(id = "future", featuredFrom = "2027-01-01", expires = "2027-01-31")
        val expired = template.copy(id = "expired", featuredFrom = "2026-11-01", expires = "2026-11-30")
        val visible = InkShelf2Spotlight.selectVisible(
            listOf(future, expired, active.first()) + active + doom, "2026-12-01",
        )
        assertEquals(listOf(doom.id) + active.take(5).map { it.id }, visible.map { it.id })
        assertEquals(6, InkShelf2Spotlight.MAX_ACTIVE)
        assertEquals(emptyList<InkShelf2Spotlight.Event>(),
            InkShelf2Spotlight.selectVisible(active + doom, "2027-01-01"))
    }

    @Test
    fun doomReadingGuideRespectsStartDateAndIssueRanges() {
        val doom = InkShelf2Spotlight.Event(
            id = "doom-essential", title = "DOCTOR DOOM", publisher = "Marvel",
            label = "ESSENTIAL READING", summary = "Ten Doom essentials for December.",
            officialUrl = "https://share.marvel.com/comics/discover/2493/one-world-under-doom",
            featuredFrom = "2026-12-01", expires = "2027-02-28",
            pulseKeywords = listOf("doctor doom"),
            series = listOf(InkShelf2Spotlight.Series("origin", "Books of Doom", 2005,
                "Begin with Doom's early years.", "#1–6")),
        )
        assertEquals(emptyList<InkShelf2Spotlight.Event>(),
            InkShelf2Spotlight.selectVisible(listOf(doom), "2026-11-30"))
        assertEquals(listOf(doom),
            InkShelf2Spotlight.selectVisible(listOf(doom), "2026-12-01"))
        assertEquals(emptyList<InkShelf2Spotlight.Event>(),
            InkShelf2Spotlight.selectVisible(listOf(doom), "2027-03-01"))
        assertEquals("#1–6", doom.series.first().issues)
        assertEquals(InkShelf2Spotlight.Edition.VERIFIED,
            InkShelf2Spotlight.edition(doom.series.first(), "Books of Doom (2005)"))
    }

    @Test
    fun theUltimatesEditorialMatchHandlesBatCaveNameAndYear() {
        val series = InkShelf2Spotlight.Series("ultimates", "The Ultimates", 2024, "The team")
        assertEquals(InkShelf2Spotlight.Edition.VERIFIED,
            InkShelf2Spotlight.edition(series, "The Ultimates (2024)"))
        assertEquals(InkShelf2Spotlight.Edition.VERIFIED,
            InkShelf2Spotlight.edition(series, "Ultimates (2024)"))
        assertEquals(InkShelf2Spotlight.Edition.CHECK_YEAR,
            InkShelf2Spotlight.edition(series, "The Ultimates"))
        assertEquals(InkShelf2Spotlight.Edition.DIFFERENT,
            InkShelf2Spotlight.edition(series, "The Ultimates (2002)"))
    }

    @Test
    fun officialMarvelFeedSupportsComicsAndAdaptationsButNotMerchandise() {
        assertEquals(
            InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopics.classifyOfficialMarvel(
                "November 18's New Marvel Comics: The Full List",
            ),
        )
        assertEquals(
            InkShelf2PulseTopic.FILM_TV,
            InkShelf2PulseTopics.classifyOfficialMarvel(
                "Meet the Cast of VisionQuest in a New TV Series",
            ),
        )
        assertEquals(
            null,
            InkShelf2PulseTopics.classifyOfficialMarvel(
                "Shop Marvel Must Haves: Official Doctor Doom Toys and Merchandise",
            ),
        )
    }

    @Test
    fun officialMarvelAppearsEarlyWithoutRemovingScreenRantOrDuplicatingHeadlines() {
        val stories = (0..17).map { i ->
            item("Another publisher article $i").copy(publisher = "The Beat")
        }.toMutableList()
        stories[15] = stories[15].copy(publisher = "Marvel Official")
        stories[13] = stories[13].copy(publisher = "Screen Rant")
        stories[17] = stories[17].copy(publisher = "Screen Rant")
        val promoted = InkShelf2PulseTopics.promoteOfficialNews(stories)
        assertEquals(stories.size, promoted.size)
        assertTrue(promoted.take(2).any { it.publisher == "Screen Rant" })
        assertTrue(promoted.take(5).any { it.publisher == "Marvel Official" })
        assertEquals(stories.map { it.url }.toSet(), promoted.map { it.url }.toSet())
        assertEquals(
            stories.filter { it.publisher == "The Beat" }.map { it.url },
            promoted.filter { it.publisher == "The Beat" }.map { it.url },
        )
    }

    @Test
    fun officialMarvelNewsClassifiesComicsAndRelevantFilmTv() {
        assertEquals(
            InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopics.classify(
                "Marvel Reveals New Amazing Spider-Man Comic Series",
            ),
        )
        assertEquals(
            InkShelf2PulseTopic.FILM_TV,
            InkShelf2PulseTopics.classify("Marvel Studios Announces Daredevil TV Season 4"),
        )
        assertEquals(
            null,
            InkShelf2PulseTopics.classify("Marvel Rivals game update for PlayStation"),
        )
    }

    @Test
    fun pulseSelectsComicNewsAndRelevantFilmTvButRejectsGeneralEntertainment() {
        assertEquals(
            InkShelf2PulseTopic.COMICS,
            InkShelf2PulseTopics.classify(
                "Absolute Batman #23 preview", dedicatedComicsFeed = true,
            ),
        )
        assertEquals(
            InkShelf2PulseTopic.FILM_TV,
            InkShelf2PulseTopics.classify(
                "Marvel Daredevil Season 3 trailer released",
            ),
        )
        assertEquals(
            InkShelf2PulseTopic.INDUSTRY,
            InkShelf2PulseTopics.classify(
                "Marvel and DC comic book retailers report rising sales",
            ),
        )
        assertEquals(
            null,
            InkShelf2PulseTopics.classify(
                "Upcoming PlayStation video games revealed",
            ),
        )
        assertEquals(
            null,
            InkShelf2PulseTopics.classify(
                "New celebrity dating series on Netflix",
            ),
        )
    }

    @Test
    fun pulseMixedNewsFollowsTargetSplitWithEnoughPublisherContent() {
        val entries = buildList {
            repeat(15) { index ->
                add(item("Comics $index", index.toLong())
                    .copy(topic = InkShelf2PulseTopic.COMICS))
            }
            repeat(10) { index ->
                add(item("Film $index", (index + 20).toLong())
                    .copy(topic = InkShelf2PulseTopic.FILM_TV))
            }
            repeat(10) { index ->
                add(item("Industry $index", (index + 40).toLong())
                    .copy(topic = InkShelf2PulseTopic.INDUSTRY))
            }
        }
        val mixed = InkShelf2PulseTopics.mix(entries, 20)
        assertEquals(20, mixed.size)
        assertEquals(12, mixed.count { it.topic == InkShelf2PulseTopic.COMICS })
        assertEquals(5, mixed.count { it.topic == InkShelf2PulseTopic.FILM_TV })
        assertEquals(3, mixed.count { it.topic == InkShelf2PulseTopic.INDUSTRY })
        assertEquals(5, InkShelf2PulseTopics.mix(
            entries.filter { it.topic == InkShelf2PulseTopic.COMICS }, 5,
        ).size)
    }

    @Test
    fun multiwordSeriesMatchesItsWholeNameOnly() {
        assertTrue(
            InkShelf2PulseMatcher.isMatch(
                "Absolute Batman (2024-)",
                item("Absolute Batman #5 review: new DC Comics issue"),
            ),
        )
        assertFalse(
            InkShelf2PulseMatcher.isMatch(
                "Absolute Batman (2024-)",
                item("Batman comic movie news and reviews"),
            ),
        )
        assertFalse(
            InkShelf2PulseMatcher.isMatch(
                "Ultimate Spider-Man (2024-)",
                item("Amazing Spider-Man issue reviews"),
            ),
        )
    }

    @Test
    fun singleWordFranchiseRequiresComicContext() {
        assertFalse(
            InkShelf2PulseMatcher.isMatch(
                "Batman (2016)",
                item("Batman movie trailer and casting updates"),
            ),
        )
        assertTrue(
            InkShelf2PulseMatcher.isMatch(
                "Batman (2016)",
                item("Batman comic issue #149 review"),
            ),
        )
    }

    @Test
    fun relatedResultsKeepTwoNewsAndTwoVideosInRecencyOrder() {
        val entries = listOf(
            item("Absolute Batman comic news 1", 100),
            item("Absolute Batman comic news 2", 200),
            item("Absolute Batman video review 1", 300, video = true),
            item("Absolute Batman video review 2", 400, video = true),
            item("Absolute Batman comic news 3", 500),
            item("Batman film trailer", 900),
        )
        val result = InkShelf2PulseMatcher.related("Absolute Batman (2024-)", entries)
        assertEquals(
            listOf(
                "Absolute Batman comic news 3",
                "Absolute Batman video review 2",
                "Absolute Batman comic news 2",
                "Absolute Batman video review 1",
            ),
            result.map { it.title },
        )
    }

    @Test
    fun guidesHeroPrefersRealNamedEventsOverSimilarSequels() {
        fun guide(id: Int, title: String, publisher: String) = InkShelf2Guide(
            id = id,
            name = title,
            type = "EVENT",
            author = "test",
            attribution = "",
            rating = 0.0,
            publisher = publisher,
        )
        val available = listOf(
            guide(1, "Civil War II", "marvel"),
            guide(2, "Civil War (2006)", "marvel"),
            guide(3, "Secret Wars (2015)", "marvel"),
            guide(4, "Crisis on Infinite Earths", "dc"),
            guide(5, "Knightfall", "dc"),
            guide(6, "Absolute Power", "dc"),
        )
        val featured = InkShelf2FeaturedGuides.match(available)
        assertEquals(
            listOf("Civil War", "Secret Wars", "Absolute Power", "Knightfall", "Crisis on Infinite Earths"),
            featured.map { it.label },
        )
        assertEquals(2, featured.first().guide.id)
        assertFalse(featured.any { it.guide.id == 1 })
    }

    @Test
    fun guideCatalogueKeepsModernEventsSeparateFromOldAndAmbiguousRuns() {
        fun guide(id: Int, name: String, publisher: String) = InkShelf2Guide(
            id = id, name = name, type = "EVENT", author = "test",
            attribution = "", rating = 1.0, publisher = publisher,
        )
        val available = listOf(
            guide(1, "Civil War (2006)", "marvel"),
            guide(2, "Civil War II", "marvel"),
            guide(3, "Secret Wars", "marvel"),
            guide(4, "Secret Wars (1984)", "marvel"),
            guide(5, "Secret Wars (2015)", "marvel"),
            guide(6, "Crisis on Infinite Earths", "dc"),
            guide(7, "Knightfall", "dc"),
            guide(8, "Captain America Golden Age (1941)", "marvel"),
        )
        val modern = InkShelf2GuideFocus.choose(
            available, InkShelf2GuideFocus.Era.MODERN,
        )
        assertEquals(listOf(1, 5), modern.map { it.id }.sorted())
        assertEquals(
            listOf(6, 7),
            InkShelf2GuideFocus.choose(available, InkShelf2GuideFocus.Era.CLASSIC)
                .map { it.id }.sorted(),
        )
        assertEquals(null, InkShelf2GuideFocus.identify(available[2]))
        assertEquals(null, InkShelf2GuideFocus.identify(available[3]))
        assertEquals(null, InkShelf2GuideFocus.identify(available[7]))
    }

    @Test
    fun roadToCivilWarCollectionHasSixVerifiedOriginalIssues() {
        fun guide(name: String) = InkShelf2Guide(
            id = 2006, name = name, type = "EVENT", author = "Metron",
            attribution = "", rating = 0.0, publisher = "marvel",
        )
        val collection = InkShelf2GuideCollections.forGuide(
            guide("Civil War (2006)"),
        ).first { it.id == "marvel-civil-war-2006-road" }
        assertEquals("Road to Civil War", collection.query)
        assertEquals(6, collection.issueCount)
        assertEquals(
            listOf(
                "Amazing Spider-Man (1999) #529–531",
                "Fantastic Four (1998) #536–537",
                "New Avengers: Illuminati #1",
            ),
            collection.included.map { it.label },
        )
        assertTrue(InkShelf2GuideCollections.forGuide(guide("Civil War II")).isEmpty())
        assertEquals(
            listOf("marvel-secret-wars-2015"),
            InkShelf2GuideCollections.forGuide(guide("Secret Wars (2015)"))
                .map { it.id },
        )
    }

    @Test
    fun collectedBookTitleDoesNotProveSourceContents() {
        val collection = InkShelf2GuideCollections.forGuide(
            InkShelf2Guide(
                id = 14, name = "Civil War (2006)", type = "EVENT",
                author = "test", attribution = "", rating = 1.0, publisher = "marvel",
            ),
        ).first { it.id == "marvel-civil-war-2006-road" }
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.TITLE_MATCH,
            InkShelf2GuideCollections.classifySourceTitle(
                collection, "Civil War: The Road to Civil War (2007)",
            ),
        )
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.TITLE_MATCH,
            InkShelf2GuideCollections.classifySourceTitle(
                collection, "The Road to Civil War TPB",
            ),
        )
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.POSSIBLE,
            InkShelf2GuideCollections.classifySourceTitle(
                collection, "Road to Civil War Complete Collection Vol 1",
            ),
        )
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.OTHER,
            InkShelf2GuideCollections.classifySourceTitle(
                collection, "Civil War Omnibus",
            ),
        )
        assertFalse(
            InkShelf2GuideCollections.sourceDescriptionConfirmsContents(collection, null),
        )
        assertFalse(
            InkShelf2GuideCollections.sourceDescriptionConfirmsContents(
                collection, "Collects Amazing Spider-Man #529-531 only.",
            ),
        )
        assertTrue(
            InkShelf2GuideCollections.sourceDescriptionConfirmsContents(
                collection,
                "Collects Amazing Spider-Man (1999) #529-531, " +
                    "Fantastic Four (1997) #536-537 and New Avengers: Illuminati One-Shot.",
            ),
        )
    }


    @Test
    fun collectedEditionsExpandToNamedModernEventsButNotOtherRuns() {
        fun guide(name: String, publisher: String) = InkShelf2Guide(
            id = name.hashCode(),
            name = name,
            type = "EVENT",
            author = "test",
            attribution = "",
            rating = 1.0,
            publisher = publisher,
        )
        assertEquals(
            2,
            InkShelf2GuideCollections.forGuide(guide("Civil War (2006)", "marvel")).size,
        )
        val expected = listOf(
            Triple("Secret Wars (2015)", "marvel", 9),
            Triple("House of M", "marvel", 8),
            Triple("Absolute Power", "dc", 5),
            Triple("Flashpoint", "dc", 5),
            Triple("Blackest Night", "dc", 9),
            Triple("Crisis on Infinite Earths", "dc", 12),
            Triple("Infinite Crisis", "dc", 7),
        )
        expected.forEach { (event, publisher, count) ->
            val found = InkShelf2GuideCollections.forGuide(guide(event, publisher))
            assertEquals("Expected one published collection for $event", 1, found.size)
            assertEquals("Unexpected core issues for $event", count, found.single().issueCount)
        }
        assertTrue(InkShelf2GuideCollections.forGuide(guide("Secret Wars", "marvel")).isEmpty())
        assertTrue(InkShelf2GuideCollections.forGuide(guide("Secret Wars (1984)", "marvel")).isEmpty())
        assertTrue(InkShelf2GuideCollections.forGuide(guide("Civil War II", "marvel")).isEmpty())
    }

    @Test
    fun collectedEditionSearchDoesNotConfuseDifferentYearsAndTieIns() {
        fun guide(name: String, publisher: String) = InkShelf2Guide(
            id = name.hashCode(), name = name, type = "EVENT", author = "test",
            attribution = "", rating = 1.0, publisher = publisher,
        )
        val wars = InkShelf2GuideCollections.forGuide(
            guide("Secret Wars (2015)", "marvel"),
        ).single()
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.OTHER,
            InkShelf2GuideCollections.classifySourceTitle(wars, "Secret Wars (1984)"),
        )
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.POSSIBLE,
            InkShelf2GuideCollections.classifySourceTitle(wars, "Secret Wars (2015)"),
        )
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.TITLE_MATCH,
            InkShelf2GuideCollections.classifySourceTitle(
                wars, "Secret Wars by Jonathan Hickman TPB (2016)",
            ),
        )
        val house = InkShelf2GuideCollections.forGuide(guide("House of M", "marvel")).single()
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.OTHER,
            InkShelf2GuideCollections.classifySourceTitle(house, "House of M: Iron Man"),
        )
        assertTrue(
            InkShelf2GuideCollections.sourceDescriptionConfirmsContents(
                house, "Collects House of M (2005) #1-8.",
            ),
        )
        val civilWar = InkShelf2GuideCollections.forGuide(
            guide("Civil War", "marvel"),
        ).last()
        assertEquals(
            InkShelf2GuideCollections.SourceMatch.OTHER,
            InkShelf2GuideCollections.classifySourceTitle(
                civilWar, "Civil War: The Road to Civil War",
            ),
        )
    }

    @Test
    fun guideIssueDoesNotConfuseDifferentNewAvengersRuns() {
        val issue = InkShelf2GuideIssue(
            id = 10024,
            position = 9,
            series = "New Avengers",
            volume = 1,
            number = "24",
            role = "Tie-In",
            yearBegan = 2004,
        )
        assertEquals("New Avengers (2004) #24", issue.displayName)
        assertEquals(
            InkShelf2GuideEdition.Match.MATCHING,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2004)"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.MATCHING,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2004–2010)"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.OTHER_RUN,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2013)"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.UNVERIFIED,
            InkShelf2GuideEdition.classify(issue, "New Avengers"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.OTHER_TITLE,
            InkShelf2GuideEdition.classify(issue, "The New Avengers (2004)"),
        )
        assertEquals("New Avengers", InkShelf2GuideEdition.sourceSearchQuery(issue))
    }

    @Test
    fun guideSourceIssueMatcherHandlesIssueCollectionsWithoutGuessing() {
        fun chapter(name: String, number: Float = -1f) =
            eu.kanade.tachiyomi.data.database.models.Chapter.create().apply {
                this.name = name
                this.url = "/comic/" + name
                this.chapter_number = number
            }
        val requested = InkShelf2GuideIssue(
            id = 24,
            position = 1,
            series = "New Avengers",
            volume = 1,
            number = "24",
            role = "Core Issue",
            yearBegan = 2004,
        )
        val direct = chapter("New Avengers #24")
        val combined = chapter("New Avengers #23-#25")
        val annual = chapter("New Avengers Annual #24")
        assertEquals(
            direct,
            InkShelf2GuideChapterFinder.find(requested, listOf(combined, direct, annual))?.chapter,
        )
        assertFalse(
            InkShelf2GuideChapterFinder.find(requested, listOf(combined, direct))!!.grouped,
        )
        assertTrue(
            InkShelf2GuideChapterFinder.find(requested, listOf(combined))!!.grouped,
        )
        assertEquals(null, InkShelf2GuideChapterFinder.find(requested, listOf(annual)))
        assertEquals(
            null,
            InkShelf2GuideChapterFinder.find(requested, listOf(direct, chapter("#24"))),
        )
        val wrong = chapter("#25")
        assertEquals(null, InkShelf2GuideChapterFinder.find(requested, listOf(wrong)))
    }

    @Test
    fun guideYearMayNeedVerificationWhenCoverDateDiffers() {
        val issue = InkShelf2GuideIssue(
            id = 24,
            position = 1,
            series = "New Avengers",
            volume = 1,
            number = "24",
            role = "Core Issue",
            yearBegan = 2004,
        )
        assertEquals(
            InkShelf2GuideEdition.Match.UNVERIFIED,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2005)"),
        )
        assertEquals(
            InkShelf2GuideEdition.Match.OTHER_RUN,
            InkShelf2GuideEdition.classify(issue, "New Avengers (2013)"),
        )
    }

    @Test
    fun emptyAndAmbiguousTitlesDoNotProduceResults() {
        assertEquals(null, InkShelf2PulseMatcher.searchTerm("???"))
        assertTrue(InkShelf2PulseMatcher.related("???", listOf(item("comics news"))).isEmpty())
    }
}

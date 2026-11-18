package eu.kanade.tachiyomi.ui.home

import java.util.Calendar

/**
 * Existing publisher and creator artwork for the seven Daily Comics themes.
 *
 * These are remote images from their original source websites; copyrighted art
 * is NOT bundled in InkShelf. Show a source credit and keep the existing cover
 * as an offline/network-error fallback. Do not replace this with generated art.
 */
internal object InkShelfDailyHeroArt {
    data class Artwork(
        val imageUrl: String,
        val sourcePage: String,
        val credit: String,
    )

    fun forDay(day: Int): Artwork = when (day) {
        Calendar.MONDAY -> Artwork(
            imageUrl = "https://cdn.marvel.com/content/2x/034wlv_com_mas_mob_03_0.jpg",
            sourcePage = "https://www.marvel.com/characters/wolverine-logan",
            credit = "Wolverine artwork © Marvel",
        )
        Calendar.TUESDAY -> Artwork(
            imageUrl = "https://substack-post-media.s3.amazonaws.com/public/images/8139e61a-5252-4fb2-93f9-7dc3f9120575_1600x1000.jpeg",
            sourcePage = "https://newsletter.blackmarket.la/p/radiant-black-the-story-so-far",
            credit = "Radiant Black art © its creators / Image Comics",
        )
        Calendar.WEDNESDAY -> Artwork(
            imageUrl = "https://static.dc.com/2026-06/LEGION-OF-SUPER-HEROES-1-3000_1000.jpg?w=1200",
            sourcePage = "https://www.dc.com/blog/2026-06-17/dc-announces-three-new-next-level-comic-book-series",
            credit = "Legion of Super-Heroes artwork © DC",
        )
        Calendar.THURSDAY -> Artwork(
            imageUrl = "https://static.dc.com/2025-07/sdcc_newHistory_3x1.png",
            sourcePage = "https://www.dc.com/blog/2025/07/01/explore-d-cs-golden-age-legacy",
            credit = "Golden Age illustration © DC / Scott Koblish",
        )
        Calendar.FRIDAY -> Artwork(
            imageUrl = "https://cdn.marvel.com/content/1x/sdcc_ffitd_com_mas_dsk_01.jpg",
            sourcePage = "https://www.marvel.com/ffdepths-sdcc",
            credit = "Fantastic Four artwork © Marvel",
        )
        Calendar.SATURDAY -> Artwork(
            imageUrl = "https://cdn.marvel.com/u/prod/marvel/i/mg/c/c0/5b7f294f1d3fd/clean.jpg",
            sourcePage = "https://www.marvel.com/comics/guides/1133/dan-slotts-spider-man-part-1",
            credit = "Spider-Man artwork © Marvel",
        )
        else -> Artwork(
            imageUrl = "https://static.dc.com/2025-05/2025_06_02_SupermanSummer_Marquee_3x1.jpg",
            sourcePage = "https://www.dc.com/blog/2025/06/02/how-to-have-a-super-man-summer-vacation",
            credit = "Superman artwork © DC",
        )
    }
}

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
            imageUrl = "https://cdn.imagecomics.com/assets/i/releases/2512/saga-25_0c8177eef2.jpg",
            sourcePage = "https://imagecomics.com/comics/releases/saga-25",
            credit = "Saga #25 • Fiona Staples / Image Comics",
        )
        Calendar.WEDNESDAY -> Artwork(
            imageUrl = "https://static.dc.com/2026-02/T2513300015001.jpg?w=1200",
            sourcePage = "https://www.dc.com/comics",
            credit = "Absolute Wonder Woman Annual • DC",
        )
        Calendar.THURSDAY -> Artwork(
            imageUrl = "https://images.squarespace-cdn.com/content/v1/5f9f06c44dd1ed19b7080797/3022ce9d-247a-4367-bd58-2bd030bb8541/VszAFo4MyfCCmhNVF6odi9.jpg",
            sourcePage = "https://www.imaginaryworldspodcast.org/episodes/how-jack-kirby-made-his-mark-on-marvel",
            credit = "Fantastic Four • art by Jack Kirby",
        )
        Calendar.FRIDAY -> Artwork(
            imageUrl = "https://cdn.marvel.com/u/prod/marvel/i/mg/8/a0/614260a6baf4b/clean.jpg",
            sourcePage = "https://www.marvel.com/comics/issue/89645/fantastic_four_2018_35",
            credit = "Fantastic Four #35 • Marvel",
        )
        Calendar.SATURDAY -> Artwork(
            imageUrl = "https://1.bp.blogspot.com/-1RfnRirQdYQ/Xxlr9jpMHcI/AAAAAAACYNQ/yHpq6O7Ewl4tVzzly2QLwH2tBWkR_v3yACLcBGAsYHQ/s1600/Game%2BInformer%2B-%2BMarvel%2527s%2BSpider-Man%2Bby%2BAlex%2BRoss.jpg",
            sourcePage = "https://geekynerfherder.blogspot.com/2020/07/artoftheday-spider-man-by-alex-ross.html",
            credit = "Spider-Man • art by Alex Ross",
        )
        else -> Artwork(
            imageUrl = "https://static.dc.com/2025-04/2025_04_28_DefiningSupermanMoments_BlogRoll_Mobile_4x3.jpg",
            sourcePage = "https://www.dc.com/blog/2023/04/13/taking-flight-twelve-moments-that-defined-superman",
            credit = "Superman • DC",
        )
    }
}

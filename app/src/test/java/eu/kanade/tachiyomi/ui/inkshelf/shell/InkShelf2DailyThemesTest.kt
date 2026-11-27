package eu.kanade.tachiyomi.ui.inkshelf.shell

import java.util.Calendar
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InkShelf2DailyThemesTest {
    private fun accepted(day: Int, vararg titles: String) = titles.forEach {
        assertNotNull(it, InkShelf2DailyRotation.themeReason(day, it))
    }
    private fun rejected(day: Int, vararg titles: String) = titles.forEach {
        assertNull(it, InkShelf2DailyRotation.themeReason(day, it, "superhero, fantasy, magic, cosmic, action"))
    }

    @Test fun mutantMondayRejectsUnrelatedHeroes() {
        accepted(Calendar.MONDAY, "Uncanny X-Men (1981-2011)", "Wolverine", "Storm (2024-)", "X-23")
        rejected(Calendar.MONDAY, "Batman", "The Storm Riders", "Eight Billion Genies")
    }
    @Test fun indieTuesdayRequiresRecognisedTitleOrExplicitPublisher() {
        accepted(Calendar.TUESDAY, "Eight Billion Genies (2022-)", "Saga (2012-)", "Once & Future")
        rejected(Calendar.TUESDAY, "Batman", "Superman")
        assertNotNull(InkShelf2DailyRotation.themeReason(Calendar.TUESDAY, "New Independent Title", "action, Image Comics"))
    }
    @Test fun wednesdayRequiresLatestProvenance() {
        assertNull(InkShelf2DailyRotation.themeReason(Calendar.WEDNESDAY, "New Comic (2026)"))
        assertNotNull(InkShelf2DailyRotation.themeReason(Calendar.WEDNESDAY, "New Comic", fromLatest = true))
    }
    @Test fun thursdayRequiresKnownPre2000Year() {
        accepted(Calendar.THURSDAY, "Fantastic Four (1961-1996)", "Superman (1939)", "Hellboy (1994)")
        rejected(Calendar.THURSDAY, "Fantastic Four (2025)", "Superman", "Batman (2000)")
    }
    @Test fun fantasticFridayRejectsGenericFantasyAndCosmicHeroes() {
        accepted(Calendar.FRIDAY, "Fantastic Four (1961)", "Silver Surfer", "Doctor Doom", "Future Foundation", "The Thing", "FF (2011)")
        rejected(Calendar.FRIDAY, "Doctor Strange", "Thor", "Scarlet Witch", "Black Science", "Eight Billion Genies", "Swamp Thing")
    }
    @Test fun spiderSaturdayRejectsGenericSuperheroes() {
        accepted(Calendar.SATURDAY, "Midnight Spider-Man (2026-)", "Miles Morales", "Venom", "Spider-Gwen", "Silk", "Scarlet Spider")
        rejected(Calendar.SATURDAY, "Superman", "The Ultimates", "The Silk Road")
    }
    @Test fun superSundayMatchesSupermanFamilyOnly() {
        accepted(Calendar.SUNDAY, "Superman", "Action Comics (1938-)", "Supergirl", "Super Sons", "Lois Lane")
        rejected(Calendar.SUNDAY, "Eight Billion Genies (2022-)", "Midnight Spider-Man (2026-)", "The Ultimates (2002-2004)", "Batman", "Justice League")
    }
}

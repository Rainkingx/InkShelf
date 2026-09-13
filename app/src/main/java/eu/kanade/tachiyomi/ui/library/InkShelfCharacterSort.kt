package eu.kanade.tachiyomi.ui.library

import java.util.Locale

internal object InkShelfCharacterSort {

    private data class Family(
        val key: String,
        val label: String,
        val aliases: List<String>,
    )

    private val families =
        listOf(
            Family(
                "batman",
                "Batman",
                listOf(
                    "batman",
                    "absolute batman",
                    "detective comics",
                    "dark knight",
                    "batman beyond",
                    "nightwing",
                    "batgirl",
                    "batgirls",
                    "robin",
                    "red hood",
                    "catwoman",
                    "joker",
                    "harley quinn",
                    "gotham knights",
                    "birds of prey",
                ),
            ),

            Family(
                "black panther",
                "Black Panther",
                listOf(
                    "black panther",
                    "wakanda",
                ),
            ),

            Family(
                "captain america",
                "Captain America",
                listOf(
                    "captain america",
                    "sam wilson captain america",
                ),
            ),

            Family(
                "daredevil",
                "Daredevil",
                listOf(
                    "daredevil",
                    "elektra",
                ),
            ),

            Family(
                "deadpool",
                "Deadpool",
                listOf("deadpool"),
            ),

            Family(
                "doctor strange",
                "Doctor Strange",
                listOf(
                    "doctor strange",
                    "dr strange",
                    "strange academy",
                ),
            ),

            Family(
                "flash",
                "Flash",
                listOf(
                    "the flash",
                    "flash",
                    "flashpoint",
                ),
            ),

            Family(
                "green arrow",
                "Green Arrow",
                listOf("green arrow"),
            ),

            Family(
                "green lantern",
                "Green Lantern",
                listOf(
                    "green lantern",
                    "hal jordan",
                    "john stewart",
                ),
            ),

            Family(
                "hulk",
                "Hulk",
                listOf(
                    "hulk",
                    "planet hulk",
                    "she hulk",
                    "red hulk",
                ),
            ),

            Family(
                "iron man",
                "Iron Man",
                listOf("iron man"),
            ),

            Family(
                "moon knight",
                "Moon Knight",
                listOf("moon knight"),
            ),

            Family(
                "spider man",
                "Spider-Man",
                listOf(
                    "spider man",
                    "spiderman",
                    "amazing spider man",
                    "spectacular spider man",
                    "superior spider man",
                    "ultimate spider man",
                    "miles morales",
                    "spider gwen",
                    "ghost spider",
                    "scarlet spider",
                    "spider man 2099",
                    "venom",
                    "carnage",
                    "silk",
                ),
            ),

            Family(
                "superman",
                "Superman",
                listOf(
                    "superman",
                    "action comics",
                    "man of steel",
                    "supergirl",
                    "superboy",
                    "lois lane",
                ),
            ),

            Family(
                "thor",
                "Thor",
                listOf("thor"),
            ),

            Family(
                "wonder woman",
                "Wonder Woman",
                listOf(
                    "wonder woman",
                    "sensation comics",
                ),
            ),

            Family(
                "wolverine",
                "Wolverine",
                listOf(
                    "wolverine",
                    "old man logan",
                    "weapon x",
                ),
            ),

            Family(
                "aquaman",
                "Aquaman",
                listOf("aquaman"),
            ),

            Family(
                "avengers",
                "Avengers",
                listOf(
                    "avengers",
                    "west coast avengers",
                    "young avengers",
                ),
            ),

            Family(
                "fantastic four",
                "Fantastic Four",
                listOf("fantastic four"),
            ),

            Family(
                "justice league",
                "Justice League",
                listOf(
                    "justice league",
                    "justice society",
                    "jla",
                    "jsa",
                ),
            ),

            Family(
                "teen titans",
                "Teen Titans",
                listOf(
                    "teen titans",
                    "titans",
                ),
            ),

            Family(
                "x men",
                "X-Men",
                listOf(
                    "x men",
                    "xmen",
                    "uncanny x men",
                    "exceptional x men",
                    "astonishing x men",
                    "new x men",
                    "x force",
                    "x factor",
                    "new mutants",
                ),
            ),

            Family(
                "punisher",
                "Punisher",
                listOf("punisher"),
            ),

            Family(
                "ghost rider",
                "Ghost Rider",
                listOf("ghost rider"),
            ),
        ).map { family ->
            family.copy(
                aliases = family.aliases.map(::normalize),
            )
        }

    private fun matchedFamily(title: String): Family? {
        val normalizedTitle = normalize(title)

        val matches =
            families.filter { family ->
                family.aliases.any { alias ->
                    containsPhrase(normalizedTitle, alias)
                }
            }

        // IMPORTANT:
        // More than one matching family means the title is ambiguous/crossover.
        // Never guess which shelf it belongs to.
        return matches.singleOrNull()
    }

    fun shelfLabel(title: String): String =
        matchedFamily(title)?.label ?: "Other"

    fun familyKey(title: String): String {
        val family = matchedFamily(title)

        return if (family != null) {
            "0|${family.key}"
        } else {
            "1|other"
        }
    }

    fun titleKey(title: String): String =
        normalize(title).removePrefix("the ")

    private fun containsPhrase(
        title: String,
        alias: String,
    ): Boolean =
        title == alias || " $title ".contains(" $alias ")

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace("’", "'")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
package eu.kanade.tachiyomi.ui.inkshelf.shell

/** Original editorial premise summaries; no outcomes or late-story revelations. */
internal object InkShelf2EventSynopses {
    private val descriptions = mapOf(
        "absolute-power" to "Amanda Waller launches a campaign to strip Earth's heroes of their powers. With the Justice League scattered, the remaining heroes must find a way to unite against her forces.",
        "batman-zero-year" to "A young Bruce Wayne begins his crusade as Batman while Gotham faces a growing criminal threat. His early battles test what it means to become the city's protector.",
        "blood-hunt" to "An unnatural darkness spreads across the world, giving vampires the chance to launch a coordinated assault. Marvel's heroes must confront an enemy that threatens humanity itself.",
        "civil-war-2006" to "A catastrophic incident involving young superheroes sparks a government demand for registration. Captain America and Iron Man find themselves on opposing sides of a debate over freedom and accountability.",
        "civil-war-ii" to "A new Inhuman's ability to foresee possible futures divides the superhero community. Captain Marvel and Iron Man clash over whether heroes should act on predictions before crimes occur.",
        "dc-k-o" to "DC's heroes and villains face a high-stakes tournament in which extraordinary power is on the line. Their battles carry consequences far beyond the arena.",
        "dark-nights-metal" to "Batman uncovers secrets tied to mysterious metals and a shadowy multiverse. The Justice League must confront a threat from worlds beyond their own.",
        "death-of-the-family" to "The Joker returns to Gotham with a disturbing plan aimed at Batman and his closest allies. The Bat-Family faces a deeply personal challenge to their trust in one another.",
        "fear-itself" to "An ancient force of fear emerges as powerful mystical weapons fall into dangerous hands. Earth's heroes must face both a worldwide crisis and their deepest fears.",
        "flashpoint" to "Barry Allen awakens in a world that bears little resemblance to the one he remembers. With familiar heroes and nations at odds, he must discover what changed reality.",
        "house-of-m" to "The Avengers and X-Men gather to decide how to help Wanda Maximoff after her powers become dangerously unstable. Soon they find themselves facing a world radically different from the one they know.",
        "infinite-crisis" to "A series of escalating disasters exposes divisions among the world's greatest heroes. As reality itself comes under threat, the DC Universe faces a reckoning with its past.",
        "king-in-black" to "Knull, the ancient god of symbiotes, brings his forces toward Earth. Venom and the heroes of the Marvel Universe confront an overwhelming invasion.",
        "night-of-the-owls" to "The secretive Court of Owls unleashes its assassins across Gotham. Batman and his allies must protect the city from a conspiracy hidden within its history.",
        "original-sin" to "The Watcher is found murdered on the Moon, and the investigation draws Marvel's heroes into a mystery involving hidden secrets and dangerous knowledge.",
        "secret-invasion" to "The discovery that shape-shifting Skrulls have infiltrated Earth leaves the heroes questioning whom they can trust. A hidden invasion threatens to erupt into open conflict.",
        "secret-wars-2015" to "Colliding universes bring the multiverse to the brink of destruction. Survivors must navigate a strange world shaped by the fragments of familiar realities.",
        "spider-verse" to "Spider-powered heroes from across the multiverse become targets of a family of powerful hunters. They must find one another and work together to survive.",
        "war-of-the-realms" to "Malekith's war spreads from the other realms to Earth. Thor and Marvel's heroes face an invasion that threatens all of the Ten Realms.",
        "world-war-hulk" to "The Hulk returns to Earth after his exile and seeks answers from the heroes responsible. His arrival sets the stage for an explosive confrontation.",
        "x-men-original-sin" to "Wolverine and Professor Xavier investigate long-buried secrets connected to Wolverine's past and his family. Their search brings them into conflict with dangerous enemies.",
    )
    fun get(slug: String?): String? = descriptions[slug]
}

package eu.kanade.tachiyomi.ui.inkshelf.shell

/** Verified metadata transcribed from user-selected saved event pages.
 * No HTML, browser state, signed image URLs or comic pages are bundled.
 * Provenance and page validation: editorial/saved-event-guides.json.
 */
internal object InkShelf2SavedEventOrders {
    internal data class Event(val slug: String, val title: String, val publisher: String,
        val completeCount: Int, val essentialPositions: Set<Int>, val rows: String) {
        val entries: List<Entry> by lazy {
            rows.trimIndent().lineSequence().filter { it.isNotBlank() }.map { line ->
                val fields = line.split('|')
                require(fields.size == 6)
                val position = fields[0].toInt()
                val sourceTitle = fields[1]
                val year = Regex("""\(((?:19|20)\d{2})""").find(sourceTitle)
                    ?.groupValues?.get(1)?.toIntOrNull()
                val title = sourceTitle.replace(Regex("""\s*\((?:19|20)\d{2}[^)]*\)$"""), "")
                Entry(InkShelf2GuideIssue(
                    id = position, position = position, series = title, volume = null,
                    number = fields[2], yearBegan = year,
                    role = if (position in essentialPositions) "Core Issue" else "Optional tie-in",
                    batcaveSeriesPath = fields[3].takeIf { it.isNotBlank() },
                    batcaveIssuePath = if (fields[3].isNotBlank()) "/event/$slug/$position" else null,
                    sourceUnavailable = fields[3].isBlank(),
                    issueLabel = fields[5].takeIf { it.isNotBlank() },
                ), fields[4].toIntOrNull())
            }.toList().also { entries ->
                require(entries.map { it.issue.position } == (1..completeCount).toList())
                require(essentialPositions.all { it in 1..completeCount })
            }
        }
    }
    internal data class Entry(val issue: InkShelf2GuideIssue, val websiteChapterId: Int?)

    // Columns: position | source series | issue | verified series path | website chapter ID | named special.
    val events: List<Event> = listOf(
        Event("absolute-power", "Absolute Power", "DC", 32, setOf(1, 3, 12, 23, 32), """
            1|Absolute Power: Ground Zero (2024-)|1|/32778-absolute-power-ground-zero-2024.html|242034|
            2|FCBD 2024 Collection|Full|/32673-fcbd-2024-collection.html|230704|Absolute Power
            3|Absolute Power (2024-)|1|/32800-absolute-power-2024.html|241823|
            4|Wonder Woman (2023-)|11|/6944-wonder-woman-2023.html|241578|
            5|Absolute Power: Task Force VII (2024-)|1|/32818-absolute-power-task-force-vii-2024.html|242035|
            6|Batman (2016-2026)|150|/561-batman.html|241876|
            7|Superman (2023-)|16|/5345-superman.html|241556|
            8|Green Arrow (2023-)|14|/2386-green-arrow.html|243359|
            9|Green Lantern (2023-)|13|/2395-green-lantern.html|231343|
            10|Absolute Power: Task Force VII (2024-)|2|/32818-absolute-power-task-force-vii-2024.html|232146|
            11|Absolute Power: Task Force VII (2024-)|3|/32818-absolute-power-task-force-vii-2024.html|232266|
            12|Absolute Power (2024-)|2|/32800-absolute-power-2024.html|241824|
            13|Green Lantern (2023-)|14|/2395-green-lantern.html|232417|
            14|Green Arrow (2023-)|15|/2386-green-arrow.html|243360|
            15|Wonder Woman (2023-)|12|/6944-wonder-woman-2023.html|241579|
            16|Batman (2016-2026)|151|/561-batman.html|232330|
            17|Batman (2016-2026)|152|/561-batman.html|232693|
            18|Green Arrow (2023-)|16|/2386-green-arrow.html|243361|
            19|Superman (2023-)|17|/5345-superman.html|241557|
            20|Superman (2023-)|18|/5345-superman.html|241558|
            21|Absolute Power: Task Force VII (2024-)|4|/32818-absolute-power-task-force-vii-2024.html|232418|
            22|Absolute Power: Task Force VII (2024-)|5|/32818-absolute-power-task-force-vii-2024.html|232645|
            23|Absolute Power (2024-)|3|/32800-absolute-power-2024.html|241825|
            24|Green Lantern (2023-)|15|/2395-green-lantern.html|232751|
            25|Absolute Power: Super Son (2024-)|1|/32994-absolute-power-super-son-2024.html|232827|
            26|Absolute Power: Task Force VII (2024-)|6|/32818-absolute-power-task-force-vii-2024.html|232756|
            27|Wonder Woman (2023-)|13|/6944-wonder-woman-2023.html|241580|
            28|Absolute Power: Task Force VII (2024-)|7|/32818-absolute-power-task-force-vii-2024.html|233353|
            29|Absolute Power: Origins (2024-)|1|/32859-absolute-power-origins-2024.html|232148|
            30|Absolute Power: Origins (2024-)|2|/32859-absolute-power-origins-2024.html|232646|
            31|Absolute Power: Origins (2024-)|3|/32859-absolute-power-origins-2024.html|233354|
            32|Absolute Power (2024-)|4|/32800-absolute-power-2024.html|241826|
        """),
        Event("dark-nights-metal", "Dark Nights: Metal", "DC", 26, setOf(1, 2, 3, 4, 14, 21, 23, 26), """
            1|Dark Days: The Forge (2017-)|1|/1424-dark-days-the-forge.html|7863|
            2|Dark Days: The Casting|Full|/12626-dark-days-the-casting.html|83887|
            3|Dark Nights: Metal (2017-)|1|/1448-dark-nights-metal.html|7953|
            4|Dark Nights: Metal (2017-)|2|/1448-dark-nights-metal.html|7952|
            5|Batman: The Red Death (2017-)|1|/690-batman-the-red-death.html|3914|
            6|Batman: The Murder Machine (2017-)|1|/689-batman-the-murder-machine.html|3913|
            7|Batman: The Dawnbreaker (2017-)|1|/676-batman-the-dawnbreaker.html|3884|
            8|Batman: The Drowned (2017-)|1|/680-batman-the-drowned.html|3893|
            9|Teen Titans (2016)|12|/17723-teen-titans-2016.html|119656|
            10|Nightwing (2016-)|29|/3988-nightwing.html|241278|
            11|Suicide Squad (2016-)|26|/5304-suicide-squad.html|27710|
            12|Green Arrow (2016-)|32|/2385-green-arrow.html|12507|
            13|Batman: The Merciless (2017)|1|/688-batman-the-merciless.html|3912|
            14|Dark Nights: Metal (2017-)|3|/1448-dark-nights-metal.html|7951|
            15|The Flash (2016-)|33|/5731-the-flash-2016.html|29888|
            16|Batman: The Devastator (2017-)|1|/679-batman-the-devastator.html|3892|
            17|Justice League (2016)|32|/10400-justice-league-2016.html|68273|
            18|Hal Jordan And The Green Lantern Corps|32|/16668-hal-jordan-and-the-green-lantern-corps.html|112466|
            19|Justice League (2016)|33|/10400-justice-league-2016.html|68274|
            20|Batman: Lost (2017-)|1|/640-batman-lost.html|3789|
            21|Dark Nights: Metal (2017-)|4|/1448-dark-nights-metal.html|7950|
            22|Hawkman: Found (2017-)|1|/2603-hawkman-found.html|13626|
            23|Dark Nights: Metal (2017-)|5|/1448-dark-nights-metal.html|7949|
            24|Dark Knights Rising: The Wild Hunt (2018-)|1|/1433-dark-knights-rising-the-wild-hunt.html|7927|
            25|Dark Nights: The Batman Who Laughs (2017-)|1|/1449-dark-nights-the-batman-who-laughs.html|7954|
            26|Dark Nights: Metal (2017-)|6|/1448-dark-nights-metal.html|7948|
        """),
        Event("infinite-crisis", "Infinite Crisis", "DC", 98, setOf(1, 33, 45, 58, 77, 84, 87), """
            1|Infinite Crisis (2005-2006)|1|/37861-infinite-crisis-2005-2006.html|266792|
            2|Gotham Central|37|/16175-gotham-central.html|109568|
            3|JLA (1997)|124|/19116-jla-1997.html|130980|
            4|JLA (1997)|125|/19116-jla-1997.html|130981|
            5|Outsiders (2003-2007)|29|/23100-outsiders-2003-2007.html|159376|
            6|Outsiders (2003-2007)|30|/23100-outsiders-2003-2007.html|159377|
            7|Teen Titans (2003)|29|/17720-teen-titans-2003.html|236207|
            8|Wonder Woman (1987-2006)|222|/31686-wonder-woman-1987-2006.html|224633|
            9|Superman (1987-2006)|223|/26650-superman-1987-2006.html|184949|
            10|Firestorm (2004)|19|/15108-firestorm-2004.html|101777|
            11|Teen Titans (2003)|30|/17720-teen-titans-2003.html|236208|
            12|Teen Titans (2003)|31|/17720-teen-titans-2003.html|236209|
            13|Green Lantern by Geoff Johns (2005)|7|/9263-green-lantern-2005.html|236492|
            14|Green Lantern by Geoff Johns (2005)|8|/9263-green-lantern-2005.html|236493|
            15|Birds of Prey (1999)|86|/9985-birds-of-prey-1999.html|65495|
            16|Birds of Prey (1999)|87|/9985-birds-of-prey-1999.html|65496|
            17|Birds of Prey (1999)|88|/9985-birds-of-prey-1999.html|65497|
            18|Birds of Prey (1999)|89|/9985-birds-of-prey-1999.html|65498|
            19|Birds of Prey (1999)|90|/9985-birds-of-prey-1999.html|65499|
            20|Aquaman (2003)|32|/8104-aquaman-2003.html|48377|
            21|Aquaman (2003)|33|/8104-aquaman-2003.html|48378|
            22|Aquaman (2003)|34|/8104-aquaman-2003.html|48379|
            23|Aquaman (2003)|35|/8104-aquaman-2003.html|48380|
            24|Aquaman (2003)|36|/8104-aquaman-2003.html|48381|
            25|Adventures of Superman (1987)|646|/7406-adventures-of-superman-1987.html|43568|
            26|Adventures of Superman (1987)|647|/7406-adventures-of-superman-1987.html|43569|
            27|Gotham Central|38|/16175-gotham-central.html|109569|
            28|Gotham Central|39|/16175-gotham-central.html|109570|
            29|Gotham Central|40|/16175-gotham-central.html|109571|
            30|Superman (1987-2006)|224|/26650-superman-1987-2006.html|184950|
            31|Wonder Woman (1987-2006)|223|/31686-wonder-woman-1987-2006.html|224634|
            32|JSA: Classified|4|/19331-jsa-classified.html|132459|
            33|Infinite Crisis (2005-2006)|2|/37861-infinite-crisis-2005-2006.html|266793|
            34|Firestorm (2004)|20|/15108-firestorm-2004.html|101778|
            35|Outsiders (2003-2007)|31|/23100-outsiders-2003-2007.html|159378|
            36|Outsiders (2003-2007)|32|/23100-outsiders-2003-2007.html|159379|
            37|Outsiders (2003-2007)|33|/23100-outsiders-2003-2007.html|159380|
            38|Hawkman (2002)|47|/16879-hawkman-2002.html|113913|
            39|Hawkman (2002)|48|/16879-hawkman-2002.html|113914|
            40|Hawkman (2002)|49|/16879-hawkman-2002.html|113915|
            41|Superman (1987-2006)|225|/26650-superman-1987-2006.html|184951|
            42|JSA (1999)|81|/19317-jsa-1999.html|132376|
            43|Aquaman (2003)|37|/8104-aquaman-2003.html|48382|
            44|Wonder Woman (1987-2006)|224|/31686-wonder-woman-1987-2006.html|224635|
            45|Infinite Crisis (2005-2006)|3|/37861-infinite-crisis-2005-2006.html|266794|
            46|JSA (1999)|82|/19317-jsa-1999.html|132377|
            47|Aquaman (2003)|38|/8104-aquaman-2003.html|48383|
            48|Aquaman (2003)|39|/8104-aquaman-2003.html|48384|
            49|Robin (1993-2009)|143|/24320-robin-1993-2009.html|167905|
            50|Robin (1993-2009)|144|/24320-robin-1993-2009.html|167906|
            51|Robin (1993-2009)|145|/24320-robin-1993-2009.html|167907|
            52|Day of Vengeance: Infinite Crisis Special|Full|/12834-day-of-vengeance-infinite-crisis-special.html|84983|
            53|JSA: Classified|8|/19331-jsa-classified.html|132463|
            54|JSA: Classified|9|/19331-jsa-classified.html|132464|
            55|Batman (1940-2011)|648|/6961-batman-1940.html|37711|
            56|Batman (1940-2011)|649|/6961-batman-1940.html|37712|
            57|Batman (1940-2011)|650|/6961-batman-1940.html|37713|
            58|Infinite Crisis (2005-2006)|4|/37861-infinite-crisis-2005-2006.html|266795|
            59|Nightwing (1996-2009)|116|/22737-nightwing-1996-2009.html|156446|
            60|Teen Titans (2003)|32|/17720-teen-titans-2003.html|236210|
            61|Wonder Woman (1987-2006)|225|/31686-wonder-woman-1987-2006.html|224636|
            62|Robin (1993-2009)|146|/24320-robin-1993-2009.html|167908|
            63|Robin (1993-2009)|147|/24320-robin-1993-2009.html|167909|
            64|Firestorm (2004)|21|/15108-firestorm-2004.html|101779|
            65|Firestorm (2004)|22|/15108-firestorm-2004.html|101780|
            66|Rann/Thanagar War: Infinite Crisis Special (2006-)|Full|/23926-rannthanagar-war-infinite-crisis-special-2006.html|165181|
            67|Manhunter (2004)|16|/20859-manhunter-2004.html|143033|
            68|Manhunter (2004)|17|/20859-manhunter-2004.html|143034|
            69|Manhunter (2004)|18|/20859-manhunter-2004.html|143035|
            70|Manhunter (2004)|19|/20859-manhunter-2004.html|143036|
            71|Catwoman (2002)|52|/11327-catwoman-2002.html|75434|
            72|Birds of Prey (1999)|91|/9985-birds-of-prey-1999.html|65500|
            73|Wonder Woman (1987-2006)|226|/31686-wonder-woman-1987-2006.html|224637|
            74|Adventures of Superman (1987)|648|/7406-adventures-of-superman-1987.html|43570|
            75|Teen Titans (2003)|Full|/17720-teen-titans-2003.html|236176|Annual 1
            76|Nightwing (1996-2009)|117|/22737-nightwing-1996-2009.html|156447|
            77|Infinite Crisis (2005-2006)|5|/37861-infinite-crisis-2005-2006.html|266796|
            78|Superman (1987-2006)|226|/26650-superman-1987-2006.html|184952|
            79|Action Comics (1938)|836|/7303-action-comics-1938.html|42053|
            80|Adventures of Superman (1987)|649|/7406-adventures-of-superman-1987.html|43571|
            81|Teen Titans (2003)|33|/17720-teen-titans-2003.html|236211|
            82|Infinite Crisis Secret Files 2006|Full|/18715-infinite-crisis-secret-files-2006.html|128655|
            83|Batman (1940-2011)|Full|/6961-batman-1940.html|37036|Annual 25
            84|Infinite Crisis (2005-2006)|6|/37861-infinite-crisis-2005-2006.html|266797|
            85|The OMAC Project: Infinite Crisis Special (2006-)|Full|/28804-the-omac-project-infinite-crisis-special-2006.html|200700|
            86|Villains United: Infinite Crisis Special (2006-)|Full|/30834-villains-united-infinite-crisis-special-2006.html|216613|
            87|Infinite Crisis (2005-2006)|7|/37861-infinite-crisis-2005-2006.html|266798|
            88|Green Arrow (2001)|54|/16231-green-arrow-2001.html|110084|
            89|Green Arrow (2001)|55|/16231-green-arrow-2001.html|110085|
            90|Green Arrow (2001)|56|/16231-green-arrow-2001.html|110086|
            91|Green Arrow (2001)|57|/16231-green-arrow-2001.html|110087|
            92|Green Arrow (2001)|58|/16231-green-arrow-2001.html|110088|
            93|Green Arrow (2001)|59|/16231-green-arrow-2001.html|110089|
            94|Superman/Batman (2003-2011)|26|/26711-supermanbatman-2003-2011.html|185277|
            95|Crisis Aftermath: The Spectre|1|/12263-crisis-aftermath-the-spectre.html|81139|
            96|Crisis Aftermath: The Spectre|2|/12263-crisis-aftermath-the-spectre.html|81140|
            97|Crisis Aftermath: The Spectre|3|/12263-crisis-aftermath-the-spectre.html|81141|
            98|Superman (1939)|712|/6963-superman-1939.html|38277|
        """),
        Event("king-in-black", "King in Black", "Marvel", 65, setOf(12, 23, 38, 53, 62), """
            1|Symbiote Spider-Man: King In Black (2020)|1|/5415-symbiote-spider-man-king-in-black-2020.html|28317|
            2|Symbiote Spider-Man: King In Black (2020)|2|/5415-symbiote-spider-man-king-in-black-2020.html|28316|
            3|Symbiote Spider-Man: King In Black (2020)|3|/5415-symbiote-spider-man-king-in-black-2020.html|28315|
            4|Symbiote Spider-Man: King In Black (2020)|4|/5415-symbiote-spider-man-king-in-black-2020.html|28314|
            5|Symbiote Spider-Man: King In Black (2020)|5|/5415-symbiote-spider-man-king-in-black-2020.html|28313|
            6|King In Black: Namor (2020-)|1|/3204-king-in-black-namor.html|16760|
            7|King In Black: Namor (2020-)|2|/3204-king-in-black-namor.html|16759|
            8|King In Black: Namor (2020-)|3|/3204-king-in-black-namor.html|16758|
            9|King In Black: Namor (2020-)|4|/3204-king-in-black-namor.html|16757|
            10|Atlantis Attacks (2020)|5|/447-atlantis-attacks.html|2580|
            11|King In Black: Wiccan And Hulking (2021-)|1|/3210-king-in-black-wiccan-and-hulking.html|16775|
            12|King In Black (2020-)|1|/3192-king-in-black.html|16742|
            13|The Union (2020-)|1|/6094-the-union-2020.html|31445|
            14|The Union (2020-)|2|/6094-the-union-2020.html|31444|
            15|Black Cat (2020-)|1|/874-black-cat.html|4658|
            16|Spider-Woman (2020-)|7|/4982-spider-woman.html|26128|
            17|Spider-Woman (2020-)|8|/4982-spider-woman.html|26127|
            18|Guardians Of The Galaxy (2020-)|10|/2496-guardians-of-the-galaxy.html|12966|
            19|King In Black: Ghost Rider (2021)|1|/3198-king-in-black-ghost-rider.html|16748|
            20|King In Black: Black Knight (2021)|1|/3195-king-in-black-black-knight.html|16745|
            21|King In Black: Black Panther (2021)|1|/3196-king-in-black-black-panther.html|16746|
            22|Venom (2018-2021)|31|/6453-venom-2018.html|33516|
            23|King In Black (2020-)|2|/3192-king-in-black.html|16741|
            24|Venom (2018-2021)|32|/6453-venom-2018.html|33515|
            25|King In Black: Iron Man/Doom (2020)|1|/3202-king-in-black-iron-man/doom.html|16754|
            26|King In Black: Return Of The Valkyries (2021-)|1|/3206-king-in-black-return-of-the-valkyries.html|16767|
            27|King In Black: Planet Of The Symbiotes (2021-)|1|/3205-king-in-black-planet-of-the-symbiotes.html|16763|
            28|King In Black: Planet Of The Symbiotes (2021-)|2|/3205-king-in-black-planet-of-the-symbiotes.html|16762|
            29|King In Black: Thunderbolts (2021-)|1|/3209-king-in-black-thunderbolts.html|16774|
            30|King In Black: Thunderbolts (2021-)|2|/3209-king-in-black-thunderbolts.html|16773|
            31|King In Black: Thunderbolts (2021-)|3|/3209-king-in-black-thunderbolts.html|16772|
            32|King In Black: Gwenom vs. Carnage (2021-)|1|/3199-king-in-black-gwenom-vs-carnage.html|16751|
            33|King In Black: Gwenom vs. Carnage (2021-)|2|/3199-king-in-black-gwenom-vs-carnage.html|16750|
            34|King In Black: Gwenom vs. Carnage (2021-)|3|/3199-king-in-black-gwenom-vs-carnage.html|16749|
            35|King In Black: Immortal Hulk (2020)|1|/3201-king-in-black-immortal-hulk.html|16753|
            36|S.W.O.R.D. (2020-2021)|2|/4600-sword.html|23515|
            37|S.W.O.R.D. (2020-2021)|3|/4600-sword.html|23514|
            38|King In Black (2020-)|3|/3192-king-in-black.html|16740|
            39|Daredevil (2019-)|26|/1402-daredevil.html|7757|
            40|Daredevil (2019-)|27|/1402-daredevil.html|7756|
            41|Deadpool (2019-)|10|/1576-deadpool.html|8581|
            42|King In Black: Marauders (2021)|1|/3203-king-in-black-marauders.html|16755|
            43|Savage Avengers (2019-)|17|/4630-savage-avengers.html|23699|
            44|Savage Avengers (2019-)|18|/4630-savage-avengers.html|23698|
            45|Savage Avengers (2019-)|19|/4630-savage-avengers.html|246741|
            46|Miles Morales: Spider-Man (2018-)|23|/3761-miles-morales-spider-man.html|19231|
            47|King In Black: Return Of The Valkyries (2021-)|2|/3206-king-in-black-return-of-the-valkyries.html|16766|
            48|King In Black: Return Of The Valkyries (2021-)|3|/3206-king-in-black-return-of-the-valkyries.html|16765|
            49|King In Black: Return Of The Valkyries (2021-)|4|/3206-king-in-black-return-of-the-valkyries.html|16764|
            50|Black Cat (2020-)|2|/874-black-cat.html|4657|
            51|Black Cat (2020-)|3|/874-black-cat.html|4656|
            52|Venom (2018-2021)|33|/6453-venom-2018.html|33514|
            53|King In Black (2020-)|4|/3192-king-in-black.html|16739|
            54|Fantastic Four (2018-)|29|/2013-fantastic-four.html|10886|
            55|King In Black: Captain America (2021)|1|/3197-king-in-black-captain-america.html|16747|
            56|King In Black: Scream (2021)|1|/3207-king-in-black-scream.html|16769|
            57|King In Black: Spider-Man (2021)|1|/3208-king-in-black-spider-man.html|16771|
            58|S.W.O.R.D. (2020-2021)|4.1|/4600-sword.html|23513|
            59|Fantastic Four (2018-)|30|/2013-fantastic-four.html|10885|
            60|Venom (2018-2021)|34|/6453-venom-2018.html|33513|
            61|Avengers (2018-2023)|45|/471-avengers.html|2743|
            62|King In Black (2020-)|5|/3192-king-in-black.html|16738|
            63|King In Black: Namor (2020-)|5|/3204-king-in-black-namor.html|16756|
            64|King In Black: Planet Of The Symbiotes (2021-)|3|/3205-king-in-black-planet-of-the-symbiotes.html|16761|
            65|Venom (2018-2021)|200|/6453-venom-2018.html|33512|
        """),
        Event("secret-invasion", "Secret Invasion", "Marvel", 126, setOf(1, 2, 3, 4, 5, 44, 45, 71, 84, 110, 118, 120, 122), """
            1|New Avengers: Illuminati (2007)|1|/22526-new-avengers-illuminati-2007.html|155145|
            2|New Avengers: Illuminati (2007)|2|/22526-new-avengers-illuminati-2007.html|155146|
            3|New Avengers: Illuminati (2007)|3|/22526-new-avengers-illuminati-2007.html|155147|
            4|New Avengers: Illuminati (2007)|4|/22526-new-avengers-illuminati-2007.html|155148|
            5|Secret Invasion Prologue (2008-)|Full|/24914-secret-invasion-prologue-2008.html|171789|
            6|New Avengers (2004)|32|/22518-new-avengers-2005.html|155017|
            7|New Avengers (2004)|33|/22518-new-avengers-2005.html|155018|
            8|The Mighty Avengers (2007-2010)|7|/28619-the-mighty-avengers-2007-2010.html|199217|
            9|New Avengers (2004)|34|/22518-new-avengers-2005.html|155019|
            10|The Mighty Avengers (2007-2010)|8|/28619-the-mighty-avengers-2007-2010.html|199218|
            11|New Avengers (2004)|35|/22518-new-avengers-2005.html|155020|
            12|New Avengers (2004)|36|/22518-new-avengers-2005.html|155021|
            13|New Avengers (2004)|37|/22518-new-avengers-2005.html|155022|
            14|New Avengers (2004)|Full|/22518-new-avengers-2005.html|154984|Annual 2
            15|New Avengers: Illuminati (2007)|5|/22526-new-avengers-illuminati-2007.html|155149|
            16|New Avengers (2004)|38|/22518-new-avengers-2005.html|155023|
            17|Avengers: The Initiative|Full|/8689-avengers-the-initiative.html|55888|Annual 1
            18|The Mighty Avengers (2007-2010)|9|/28619-the-mighty-avengers-2007-2010.html|199219|
            19|The Mighty Avengers (2007-2010)|10|/28619-the-mighty-avengers-2007-2010.html|199220|
            20|The Mighty Avengers (2007-2010)|11|/28619-the-mighty-avengers-2007-2010.html|199221|
            21|Captain Marvel (2008)|1|/11192-captain-marvel-2008.html|74132|
            22|Captain Marvel (2008)|2|/11192-captain-marvel-2008.html|74133|
            23|Captain Marvel (2008)|3|/11192-captain-marvel-2008.html|74134|
            24|Captain Marvel (2008)|4|/11192-captain-marvel-2008.html|74135|
            25|Captain Marvel (2008)|5|/11192-captain-marvel-2008.html|74136|
            26|New Avengers (2004)|39|/22518-new-avengers-2005.html|155024|
            27|Ms. Marvel (2006)|25|/22152-ms-marvel-2006.html|152554|
            28|Ms. Marvel (2006)|26|/22152-ms-marvel-2006.html|152555|
            29|Ms. Marvel (2006)|27|/22152-ms-marvel-2006.html|152556|
            30|Secret Invasion Saga (2008-)|Full|/24915-secret-invasion-saga-2008.html|171790|
            31|The Mighty Avengers (2007-2010)|12|/28619-the-mighty-avengers-2007-2010.html|199222|
            32|The Mighty Avengers (2007-2010)|13|/28619-the-mighty-avengers-2007-2010.html|199223|
            33|New Avengers (2004)|40|/22518-new-avengers-2005.html|155025|
            34|Avengers: The Initiative|14|/8689-avengers-the-initiative.html|55903|
            35|X-Factor (2006)|33|/17419-x-factor-2006.html|117807|
            36|She-Hulk (2005-2009)|31|/25195-she-hulk-2005-2009.html|174061|
            37|X-Factor (2006)|34|/17419-x-factor-2006.html|117808|
            38|Guardians of the Galaxy (2008)|4|/16520-guardians-of-the-galaxy-2008.html|111677|
            39|Guardians of the Galaxy (2008)|5|/16520-guardians-of-the-galaxy-2008.html|111678|
            40|Guardians of the Galaxy (2008)|6|/16520-guardians-of-the-galaxy-2008.html|111679|
            41|Incredible Hercules|117|/18641-incredible-hercules.html|128252|
            42|Secret Invasion: Inhumans (2008-2009)|1|/24920-secret-invasion-inhumans-2008-2009.html|171808|
            43|Secret Invasion: Inhumans (2008-2009)|2|/24920-secret-invasion-inhumans-2008-2009.html|171809|
            44|Secret Invasion (2008-2009)|1|/24911-secret-invasion-2008-2009.html|171775|
            45|Secret Invasion (2008-2009)|2|/24911-secret-invasion-2008-2009.html|171776|
            46|New Avengers (2004)|41|/22518-new-avengers-2005.html|155026|
            47|New Avengers (2004)|42|/22518-new-avengers-2005.html|155027|
            48|New Avengers (2004)|43|/22518-new-avengers-2005.html|155028|
            49|Thunderbolts (1997-2012)|122|/29797-thunderbolts-1997-2012.html|209029|
            50|Thunderbolts (1997-2012)|123|/29797-thunderbolts-1997-2012.html|209030|
            51|Thunderbolts (1997-2012)|124|/29797-thunderbolts-1997-2012.html|209031|
            52|Secret Invasion: Fantastic Four (2008)|1|/24917-secret-invasion-fantastic-four-2008.html|171792|
            53|Secret Invasion: Fantastic Four (2008)|2|/24917-secret-invasion-fantastic-four-2008.html|171793|
            54|Secret Invasion: Fantastic Four (2008)|3|/24917-secret-invasion-fantastic-four-2008.html|171794|
            55|Nova (2007-2010)|16|/22862-nova-2007-2010.html|233673|
            56|Nova (2007-2010)|17|/22862-nova-2007-2010.html|233674|
            57|Nova (2007-2010)|18|/22862-nova-2007-2010.html|233675|
            58|The Mighty Avengers (2007-2010)|14|/28619-the-mighty-avengers-2007-2010.html|199224|
            59|The Mighty Avengers (2007-2010)|15|/28619-the-mighty-avengers-2007-2010.html|199225|
            60|The Mighty Avengers (2007-2010)|16|/28619-the-mighty-avengers-2007-2010.html|199226|
            61|The Mighty Avengers (2007-2010)|17|/28619-the-mighty-avengers-2007-2010.html|199227|
            62|New Warriors (2007)|14|/22576-new-warriors-2007.html|155496|
            63|New Warriors (2007)|15|/22576-new-warriors-2007.html|155497|
            64|Secret Invasion: Front Line (2008-2009)|1|/24918-secret-invasion-front-line-2008-2009.html|232375|
            65|Secret Invasion: Front Line (2008-2009)|2|/24918-secret-invasion-front-line-2008-2009.html|232376|
            66|Deadpool (2008)|1|/13113-deadpool-2008.html|86397|
            67|Secret Invasion: The Amazing Spider-Man (2008)|1|/24924-secret-invasion-the-amazing-spider-man-2008.html|171821|
            68|Secret Invasion: The Amazing Spider-Man (2008)|2|/24924-secret-invasion-the-amazing-spider-man-2008.html|171822|
            69|Secret Invasion: The Amazing Spider-Man (2008)|3|/24924-secret-invasion-the-amazing-spider-man-2008.html|171823|
            70|Secret Invasion: Runaways/Young Avengers (2008)|1|/24923-secret-invasion-runawaysyoung-avengers-2008.html|171818|
            71|Secret Invasion (2008-2009)|3|/24911-secret-invasion-2008-2009.html|171777|
            72|Secret Invasion: Runaways/Young Avengers (2008)|2|/24923-secret-invasion-runawaysyoung-avengers-2008.html|171819|
            73|Secret Invasion: Runaways/Young Avengers (2008)|3|/24923-secret-invasion-runawaysyoung-avengers-2008.html|171820|
            74|Avengers: The Initiative|15|/8689-avengers-the-initiative.html|55904|
            75|New Avengers (2004)|44|/22518-new-avengers-2005.html|155029|
            76|New Avengers (2004)|45|/22518-new-avengers-2005.html|155030|
            77|Incredible Hercules|118|/18641-incredible-hercules.html|128253|
            78|Secret Invasion: Thor (2008)|1|/24926-secret-invasion-thor-2008.html|171835|
            79|Secret Invasion: Thor (2008)|2|/24926-secret-invasion-thor-2008.html|171836|
            80|Secret Invasion: Thor (2008)|3|/24926-secret-invasion-thor-2008.html|171837|
            81|Secret Invasion: Who Do You Trust? (2008-)|Full|/24928-secret-invasion-who-do-you-trust-2008.html|171839|
            82|New Avengers (2004)|46|/22518-new-avengers-2005.html|155031|
            83|The Mighty Avengers (2007-2010)|18|/28619-the-mighty-avengers-2007-2010.html|199228|
            84|Secret Invasion (2008-2009)|4|/24911-secret-invasion-2008-2009.html|171778|
            85|Secret Invasion: Front Line (2008-2009)|3|/24918-secret-invasion-front-line-2008-2009.html|232377|
            86|Ms. Marvel (2006)|28|/22152-ms-marvel-2006.html|152557|
            87|Ms. Marvel (2006)|29|/22152-ms-marvel-2006.html|152558|
            88|Ms. Marvel (2006)|30|/22152-ms-marvel-2006.html|152559|
            89|Punisher War Journal (2007-2009)|24|/23757-punisher-war-journal-2007-2009.html|164156|
            90|Punisher War Journal (2007-2009)|25|/23757-punisher-war-journal-2007-2009.html|164157|
            91|Secret Invasion: X-Men (2008-2009)|1|/24929-secret-invasion-x-men-2008-2009.html|171840|
            92|Secret Invasion: X-Men (2008-2009)|2|/24929-secret-invasion-x-men-2008-2009.html|171841|
            93|Secret Invasion: X-Men (2008-2009)|3|/24929-secret-invasion-x-men-2008-2009.html|171842|
            94|Secret Invasion: X-Men (2008-2009)|4|/24929-secret-invasion-x-men-2008-2009.html|171843|
            95|Captain Britain and MI13|1|/11150-captain-britain-and-mi13.html|73803|
            96|Captain Britain and MI13|2|/11150-captain-britain-and-mi13.html|73804|
            97|Captain Britain and MI13|3|/11150-captain-britain-and-mi13.html|73805|
            98|Captain Britain and MI13|4|/11150-captain-britain-and-mi13.html|73806|
            99|Black Panther (2005)|39|/10114-black-panther-2005.html|66319|
            100|Black Panther (2005)|40|/10114-black-panther-2005.html|66320|
            101|Black Panther (2005)|41|/10114-black-panther-2005.html|66321|
            102|Secret Invasion: Inhumans (2008-2009)|3|/24920-secret-invasion-inhumans-2008-2009.html|171810|
            103|Secret Invasion: Inhumans (2008-2009)|4|/24920-secret-invasion-inhumans-2008-2009.html|171811|
            104|She-Hulk (2005-2009)|32|/25195-she-hulk-2005-2009.html|174062|
            105|She-Hulk (2005-2009)|33|/25195-she-hulk-2005-2009.html|174063|
            106|Avengers: The Initiative|16|/8689-avengers-the-initiative.html|55905|
            107|Iron Man (2005)|33|/17352-iron-man-2005.html|117250|
            108|Iron Man (2005)|34|/17352-iron-man-2005.html|117251|
            109|Iron Man (2005)|35|/17352-iron-man-2005.html|117252|
            110|Secret Invasion (2008-2009)|5|/24911-secret-invasion-2008-2009.html|171779|
            111|Avengers: The Initiative|17|/8689-avengers-the-initiative.html|55906|
            112|Deadpool (2008)|2|/13113-deadpool-2008.html|86398|
            113|Deadpool (2008)|3|/13113-deadpool-2008.html|86399|
            114|Incredible Hercules|119|/18641-incredible-hercules.html|128254|
            115|Incredible Hercules|120|/18641-incredible-hercules.html|128255|
            116|Avengers: The Initiative|18|/8689-avengers-the-initiative.html|55907|
            117|The Mighty Avengers (2007-2010)|19|/28619-the-mighty-avengers-2007-2010.html|199229|
            118|Secret Invasion (2008-2009)|6|/24911-secret-invasion-2008-2009.html|171780|
            119|Secret Invasion: Front Line (2008-2009)|4|/24918-secret-invasion-front-line-2008-2009.html|232378|
            120|Secret Invasion (2008-2009)|7|/24911-secret-invasion-2008-2009.html|171781|
            121|Thunderbolts (1997-2012)|125|/29797-thunderbolts-1997-2012.html|209032|
            122|Secret Invasion (2008-2009)|8|/24911-secret-invasion-2008-2009.html|171782|
            123|New Avengers (2004)|47|/22518-new-avengers-2005.html|155032|
            124|Avengers: The Initiative|19|/8689-avengers-the-initiative.html|55908|
            125|Secret Invasion: Front Line (2008-2009)|5|/24918-secret-invasion-front-line-2008-2009.html|232379|
            126|The Mighty Avengers (2007-2010)|20|/28619-the-mighty-avengers-2007-2010.html|199230|
        """),
        Event("secret-wars-2015", "Secret Wars (2015)", "Marvel", 230, setOf(3, 4, 24, 35, 156, 212, 218, 222, 223, 224), """
            1|Black Widow (2014)|19|/10169-black-widow-2014.html|66642|
            2|Black Widow (2014)|20|/10169-black-widow-2014.html|66643|
            3|Secret Wars (2015-2016)|0|/24958-secret-wars-2015-2016.html|172087|
            4|Secret Wars (2015-2016)|1|/24958-secret-wars-2015-2016.html|172088|
            5|Loki: Agent of Asgard|14|/20455-loki-agent-of-asgard.html|140552|
            6|Loki: Agent of Asgard|15|/20455-loki-agent-of-asgard.html|140553|
            7|Captain America and the Mighty Avengers|9|/11063-captain-america-and-the-mighty-avengers.html|73314|
            8|Ms. Marvel (2014)|16|/22153-ms-marvel-2014.html|152595|
            9|Ms. Marvel (2014)|17|/22153-ms-marvel-2014.html|152596|
            10|Ms. Marvel (2014)|18|/22153-ms-marvel-2014.html|152597|
            11|Ms. Marvel (2014)|19|/22153-ms-marvel-2014.html|152598|
            12|Magneto (2014)|18|/20785-magneto-2014.html|142514|
            13|Magneto (2014)|19|/20785-magneto-2014.html|142515|
            14|Magneto (2014)|20|/20785-magneto-2014.html|142516|
            15|Magneto (2014)|21|/20785-magneto-2014.html|142517|
            16|The Punisher (2014-2015)|19|/28960-the-punisher-2014-2015.html|201904|
            17|The Punisher (2014-2015)|20|/28960-the-punisher-2014-2015.html|201905|
            18|Ant-Man: Last Days|Full|/8066-ant-man-last-days.html|48050|
            19|Silk (2015)|7|/25362-silk-2015.html|174891|
            20|Silver Surfer (2014-2016)|13|/25392-silver-surfer-2014-2016.html|175216|
            21|Silver Surfer (2014-2016)|14|/25392-silver-surfer-2014-2016.html|175217|
            22|Loki: Agent of Asgard|16|/20455-loki-agent-of-asgard.html|140554|
            23|Loki: Agent of Asgard|17|/20455-loki-agent-of-asgard.html|140555|
            24|Secret Wars (2015-2016)|2|/24958-secret-wars-2015-2016.html|172089|
            25|Secret Wars: Battleworld (2015)|1|/24967-secret-wars-battleworld-2015.html|172200|
            26|Secret Wars: Battleworld (2015)|2|/24967-secret-wars-battleworld-2015.html|172201|
            27|Secret Wars: Battleworld (2015)|3|/24967-secret-wars-battleworld-2015.html|172202|
            28|Secret Wars: Battleworld (2015)|4|/24967-secret-wars-battleworld-2015.html|172203|
            29|Secret Wars Journal (2015-)|1|/24962-secret-wars-journal-2015.html|172191|
            30|Secret Wars Journal (2015-)|2|/24962-secret-wars-journal-2015.html|172192|
            31|Secret Wars Journal (2015-)|3|/24962-secret-wars-journal-2015.html|172193|
            32|Secret Wars Journal (2015-)|4|/24962-secret-wars-journal-2015.html|172194|
            33|Secret Wars Journal (2015-)|5|/24962-secret-wars-journal-2015.html|172195|
            34|Old Man Logan|1|||
            35|Secret Wars (2015-2016)|3|/24958-secret-wars-2015-2016.html|172090|
            36|Master of Kung Fu (2015)|1|/21405-master-of-kung-fu-2015.html|148104|
            37|Master of Kung Fu (2015)|2|/21405-master-of-kung-fu-2015.html|148105|
            38|Master of Kung Fu (2015)|3|/21405-master-of-kung-fu-2015.html|148106|
            39|Master of Kung Fu (2015)|4|/21405-master-of-kung-fu-2015.html|148107|
            40|A-Force (2015)|1|/7426-a-force-2015.html|43727|
            41|A-Force (2015)|2|/7426-a-force-2015.html|43728|
            42|A-Force (2015)|3|/7426-a-force-2015.html|43729|
            43|A-Force (2015)|4|/7426-a-force-2015.html|43730|
            44|Planet Hulk (2015)|1|/23402-planet-hulk-2015.html|161686|
            45|Planet Hulk (2015)|2|/23402-planet-hulk-2015.html|161687|
            46|Planet Hulk (2015)|3|/23402-planet-hulk-2015.html|161688|
            47|Planet Hulk (2015)|4|/23402-planet-hulk-2015.html|161689|
            48|Planet Hulk (2015)|5|/23402-planet-hulk-2015.html|161690|
            49|Spider-Verse [II] (2015)|1|/26052-spider-verse-ii-2015.html|179182|
            50|Spider-Verse [II] (2015)|2|/26052-spider-verse-ii-2015.html|179183|
            51|Spider-Verse [II] (2015)|3|/26052-spider-verse-ii-2015.html|179184|
            52|Spider-Verse [II] (2015)|4|/26052-spider-verse-ii-2015.html|179185|
            53|Spider-Verse [II] (2015)|5|/26052-spider-verse-ii-2015.html|179186|
            54|M.O.D.O.K. Assassin|1|/20672-modok-assassin.html|141960|
            55|M.O.D.O.K. Assassin|2|/20672-modok-assassin.html|141961|
            56|M.O.D.O.K. Assassin|3|/20672-modok-assassin.html|141962|
            57|M.O.D.O.K. Assassin|4|/20672-modok-assassin.html|141963|
            58|M.O.D.O.K. Assassin|5|/20672-modok-assassin.html|141964|
            59|Infinity Gauntlet (2015)|1|/18734-infinity-gauntlet-2015.html|128808|
            60|Infinity Gauntlet (2015)|2|/18734-infinity-gauntlet-2015.html|128809|
            61|Infinity Gauntlet (2015)|3|/18734-infinity-gauntlet-2015.html|128810|
            62|Infinity Gauntlet (2015)|4|/18734-infinity-gauntlet-2015.html|128811|
            63|Infinity Gauntlet (2015)|5|/18734-infinity-gauntlet-2015.html|128812|
            64|Inferno (2015)|1|/18698-inferno-2015.html|128600|
            65|Inferno (2015)|2|/18698-inferno-2015.html|128601|
            66|Inferno (2015)|3|/18698-inferno-2015.html|128602|
            67|Inferno (2015)|4|/18698-inferno-2015.html|128603|
            68|Inferno (2015)|5|/18698-inferno-2015.html|128604|
            69|Secret Wars 2099 (2015)|1|/24960-secret-wars-2099-2015.html|172177|
            70|Secret Wars 2099 (2015)|2|/24960-secret-wars-2099-2015.html|172178|
            71|Secret Wars 2099 (2015)|3|/24960-secret-wars-2099-2015.html|172179|
            72|Secret Wars 2099 (2015)|4|/24960-secret-wars-2099-2015.html|172180|
            73|Secret Wars 2099 (2015)|5|/24960-secret-wars-2099-2015.html|172181|
            74|Where Monsters Dwell (2015)|1|/31327-where-monsters-dwell-2015.html|221511|
            75|Where Monsters Dwell (2015)|2|/31327-where-monsters-dwell-2015.html|221512|
            76|Where Monsters Dwell (2015)|3|/31327-where-monsters-dwell-2015.html|221513|
            77|Where Monsters Dwell (2015)|4|/31327-where-monsters-dwell-2015.html|221514|
            78|Where Monsters Dwell (2015)|5|/31327-where-monsters-dwell-2015.html|221515|
            79|X-Men '92|1|||
            80|X-Men '92|2|||
            81|X-Men '92|3|||
            82|X-Men '92|4|||
            83|Years of Future Past (2015)|1|/31929-years-of-future-past-2015.html|226507|
            84|Years of Future Past (2015)|2|/31929-years-of-future-past-2015.html|226508|
            85|Years of Future Past (2015)|3|/31929-years-of-future-past-2015.html|226509|
            86|Years of Future Past (2015)|4|/31929-years-of-future-past-2015.html|226510|
            87|Years of Future Past (2015)|5|/31929-years-of-future-past-2015.html|226511|
            88|X-Tinction Agenda (2015)|1|/31908-x-tinction-agenda-2015.html|226320|
            89|X-Tinction Agenda (2015)|2|/31908-x-tinction-agenda-2015.html|226321|
            90|X-Tinction Agenda (2015)|3|/31908-x-tinction-agenda-2015.html|226322|
            91|X-Tinction Agenda (2015)|4|/31908-x-tinction-agenda-2015.html|226323|
            92|Giant-Size Little Marvel: AvX|1|/15916-giant-size-little-marvel-avx.html|107901|
            93|Giant-Size Little Marvel: AvX|2|/15916-giant-size-little-marvel-avx.html|107902|
            94|Giant-Size Little Marvel: AvX|3|/15916-giant-size-little-marvel-avx.html|107903|
            95|Giant-Size Little Marvel: AvX|4|/15916-giant-size-little-marvel-avx.html|107904|
            96|Future Imperfect|1|/15466-future-imperfect.html|105214|
            97|Future Imperfect|2|/15466-future-imperfect.html|105215|
            98|Future Imperfect|3|/15466-future-imperfect.html|105216|
            99|Future Imperfect|4|/15466-future-imperfect.html|105217|
            100|Amazing Spider-Man: Renew Your Vows (2015)|1|/7817-amazing-spider-man-renew-your-vows-2015.html|46509|
            101|Amazing Spider-Man: Renew Your Vows (2015)|2|/7817-amazing-spider-man-renew-your-vows-2015.html|46510|
            102|Amazing Spider-Man: Renew Your Vows (2015)|3|/7817-amazing-spider-man-renew-your-vows-2015.html|46511|
            103|Amazing Spider-Man: Renew Your Vows (2015)|4|/7817-amazing-spider-man-renew-your-vows-2015.html|46512|
            104|Amazing Spider-Man: Renew Your Vows (2015)|5|/7817-amazing-spider-man-renew-your-vows-2015.html|46513|
            105|Armor Wars|0.5|/8344-armor-wars.html|52423|
            106|Armor Wars|1|/8344-armor-wars.html|52424|
            107|Armor Wars|2|/8344-armor-wars.html|52425|
            108|Armor Wars|3|/8344-armor-wars.html|52426|
            109|Armor Wars|4|/8344-armor-wars.html|52427|
            110|Armor Wars|5|/8344-armor-wars.html|52428|
            111|Marvel Zombies (2015)|1|/21282-marvel-zombies-2015.html|147452|
            112|Marvel Zombies (2015)|2|/21282-marvel-zombies-2015.html|147453|
            113|Marvel Zombies (2015)|3|/21282-marvel-zombies-2015.html|147454|
            114|Marvel Zombies (2015)|4|/21282-marvel-zombies-2015.html|147455|
            115|Weirdworld (2015)|1|/31201-weirdworld-2015.html|220646|
            116|Weirdworld (2015)|2|/31201-weirdworld-2015.html|220647|
            117|Weirdworld (2015)|3|/31201-weirdworld-2015.html|220648|
            118|Weirdworld (2015)|4|/31201-weirdworld-2015.html|220649|
            119|1602 Witch Hunter Angela|1|/7020-1602-witch-hunter-angela.html|40083|
            120|1602 Witch Hunter Angela|2|/7020-1602-witch-hunter-angela.html|40084|
            121|1602 Witch Hunter Angela|3|/7020-1602-witch-hunter-angela.html|40085|
            122|1602 Witch Hunter Angela|4|/7020-1602-witch-hunter-angela.html|40086|
            123|Captain Marvel & the Carol Corps|1|/11185-captain-marvel--the-carol-corps.html|73993|
            124|Captain Marvel & the Carol Corps|2|/11185-captain-marvel--the-carol-corps.html|73994|
            125|Captain Marvel & the Carol Corps|3|/11185-captain-marvel--the-carol-corps.html|73995|
            126|Captain Marvel & the Carol Corps|4|/11185-captain-marvel--the-carol-corps.html|73996|
            127|Mrs. Deadpool and the Howling Commandos|1|/22148-mrs-deadpool-and-the-howling-commandos.html|152497|
            128|Mrs. Deadpool and the Howling Commandos|2|/22148-mrs-deadpool-and-the-howling-commandos.html|152498|
            129|Mrs. Deadpool and the Howling Commandos|3|/22148-mrs-deadpool-and-the-howling-commandos.html|152499|
            130|Mrs. Deadpool and the Howling Commandos|4|/22148-mrs-deadpool-and-the-howling-commandos.html|152500|
            131|1872|1|/7022-1872.html|40088|
            132|1872|2|/7022-1872.html|40089|
            133|1872|3|/7022-1872.html|40090|
            134|1872|4|/7022-1872.html|40091|
            135|Runaways (2015)|1|/24561-runaways-2015.html|169222|
            136|Runaways (2015)|2|/24561-runaways-2015.html|169223|
            137|Runaways (2015)|3|/24561-runaways-2015.html|169224|
            138|Runaways (2015)|4|/24561-runaways-2015.html|169225|
            139|Old Man Logan|2|||
            140|Squadron Sinister (2015-2016)|1|/26132-squadron-sinister-2015-2016.html|179678|
            141|Squadron Sinister (2015-2016)|2|/26132-squadron-sinister-2015-2016.html|179679|
            142|Squadron Sinister (2015-2016)|3|/26132-squadron-sinister-2015-2016.html|179680|
            143|Squadron Sinister (2015-2016)|4|/26132-squadron-sinister-2015-2016.html|179681|
            144|Age of Ultron Vs. Marvel Zombies|1|/7465-age-of-ultron-vs-marvel-zombies.html|43872|
            145|Age of Ultron Vs. Marvel Zombies|2|/7465-age-of-ultron-vs-marvel-zombies.html|43873|
            146|Age of Ultron Vs. Marvel Zombies|3|/7465-age-of-ultron-vs-marvel-zombies.html|43874|
            147|Age of Ultron Vs. Marvel Zombies|4|/7465-age-of-ultron-vs-marvel-zombies.html|43875|
            148|E is for Extinction|1|/14118-e-is-for-extinction.html|94823|
            149|E is for Extinction|2|/14118-e-is-for-extinction.html|94824|
            150|E is for Extinction|3|/14118-e-is-for-extinction.html|94825|
            151|E is for Extinction|4|/14118-e-is-for-extinction.html|94826|
            152|Korvac Saga|1|/19847-korvac-saga.html|136431|
            153|Korvac Saga|2|/19847-korvac-saga.html|136432|
            154|Korvac Saga|3|/19847-korvac-saga.html|136433|
            155|Korvac Saga|4|/19847-korvac-saga.html|136434|
            156|Secret Wars (2015-2016)|4|/24958-secret-wars-2015-2016.html|172091|
            157|Star-Lord & Kitty Pryde (2015)|1|/26233-star-lord-kitty-pryde-2015.html|180743|
            158|Star-Lord & Kitty Pryde (2015)|2|/26233-star-lord-kitty-pryde-2015.html|180744|
            159|Star-Lord & Kitty Pryde (2015)|3|/26233-star-lord-kitty-pryde-2015.html|180745|
            160|Inhumans: Attilan Rising|1|/18769-inhumans-attilan-rising.html|129001|
            161|Inhumans: Attilan Rising|2|/18769-inhumans-attilan-rising.html|129002|
            162|Inhumans: Attilan Rising|3|/18769-inhumans-attilan-rising.html|129003|
            163|Inhumans: Attilan Rising|4|/18769-inhumans-attilan-rising.html|129004|
            164|Inhumans: Attilan Rising|5|/18769-inhumans-attilan-rising.html|129005|
            165|Future Imperfect|5|/15466-future-imperfect.html|105218|
            166|Secret Wars: Secret Love (2015-)|Full|/24970-secret-wars-secret-love-2015.html|172207|
            167|Ghost Racers|1|/15793-ghost-racers.html|106900|
            168|Ghost Racers|2|/15793-ghost-racers.html|106901|
            169|Ghost Racers|3|/15793-ghost-racers.html|106902|
            170|Ghost Racers|4|/15793-ghost-racers.html|106903|
            171|Thors (2015-2016)|1|/29755-thors-2015-2016.html|208738|
            172|Thors (2015-2016)|2|/29755-thors-2015-2016.html|208739|
            173|Thors (2015-2016)|3|/29755-thors-2015-2016.html|208740|
            174|Red Skull (2015)|1|/24049-red-skull-2015.html|166017|
            175|Red Skull (2015)|2|/24049-red-skull-2015.html|166018|
            176|Red Skull (2015)|3|/24049-red-skull-2015.html|166019|
            177|Spider-Island (2015)|1|/25867-spider-island-2015.html|178269|
            178|Spider-Island (2015)|2|/25867-spider-island-2015.html|178270|
            179|Spider-Island (2015)|3|/25867-spider-island-2015.html|178271|
            180|Spider-Island (2015)|4|/25867-spider-island-2015.html|178272|
            181|Spider-Island (2015)|5|/25867-spider-island-2015.html|178273|
            182|Captain Britain and the Mighty Defenders|1|/11151-captain-britain-and-the-mighty-defenders.html|73818|
            183|Captain Britain and the Mighty Defenders|2|/11151-captain-britain-and-the-mighty-defenders.html|73819|
            184|Civil War (2015)|1|/11664-civil-war-2015.html|77408|
            185|Civil War (2015)|2|/11664-civil-war-2015.html|77409|
            186|Civil War (2015)|3|/11664-civil-war-2015.html|77410|
            187|Civil War (2015)|4|/11664-civil-war-2015.html|77411|
            188|Civil War (2015)|5|/11664-civil-war-2015.html|77412|
            189|Age of Apocalypse (2015)|1|/7452-age-of-apocalypse-2015.html|43830|
            190|Age of Apocalypse (2015)|2|/7452-age-of-apocalypse-2015.html|43831|
            191|Age of Apocalypse (2015)|3|/7452-age-of-apocalypse-2015.html|43832|
            192|Age of Apocalypse (2015)|4|/7452-age-of-apocalypse-2015.html|43833|
            193|Age of Apocalypse (2015)|5|/7452-age-of-apocalypse-2015.html|43834|
            194|Guardians Of Knowhere|1|/16517-guardians-of-knowhere.html|111580|
            195|Guardians Of Knowhere|2|/16517-guardians-of-knowhere.html|111581|
            196|Guardians Of Knowhere|3|/16517-guardians-of-knowhere.html|111582|
            197|Guardians Of Knowhere|4|/16517-guardians-of-knowhere.html|111583|
            198|Hail Hydra|1|/16662-hail-hydra.html|112419|
            199|Hail Hydra|2|/16662-hail-hydra.html|112420|
            200|Hail Hydra|3|/16662-hail-hydra.html|112421|
            201|Hail Hydra|4|/16662-hail-hydra.html|112422|
            202|Old Man Logan|3|||
            203|Ultimate End (2015-2016)|1|/30370-ultimate-end-2015-2016.html|213081|
            204|Ultimate End (2015-2016)|2|/30370-ultimate-end-2015-2016.html|213082|
            205|Ultimate End (2015-2016)|3|/30370-ultimate-end-2015-2016.html|213083|
            206|Ultimate End (2015-2016)|4|/30370-ultimate-end-2015-2016.html|213084|
            207|House of M (2015)|1|/18300-house-of-m-2015.html|126207|
            208|House of M (2015)|2|/18300-house-of-m-2015.html|126208|
            209|House of M (2015)|3|/18300-house-of-m-2015.html|126209|
            210|House of M (2015)|4|/18300-house-of-m-2015.html|126210|
            211|Howard the Human|Full|/18339-howard-the-human.html|126793|
            212|Secret Wars (2015-2016)|5|/24958-secret-wars-2015-2016.html|172092|
            213|Siege (2015)|1|/25330-siege-2015.html|174758|
            214|Siege (2015)|2|/25330-siege-2015.html|174759|
            215|Siege (2015)|3|/25330-siege-2015.html|174760|
            216|Old Man Logan|4|||
            217|Hank Johnson, Agent of Hydra|1|/16721-hank-johnson-agent-of-hydra.html|112672|
            218|Secret Wars (2015-2016)|6|/24958-secret-wars-2015-2016.html|172093|
            219|Siege (2015)|4|/25330-siege-2015.html|174761|
            220|Secret Wars: Agents of Atlas (2015-)|Full|/24966-secret-wars-agents-of-atlas-2015.html|172199|
            221|Thors (2015-2016)|4|/29755-thors-2015-2016.html|208741|
            222|Secret Wars (2015-2016)|7|/24958-secret-wars-2015-2016.html|172094|
            223|Secret Wars (2015-2016)|8|/24958-secret-wars-2015-2016.html|172095|
            224|Secret Wars (2015-2016)|9|/24958-secret-wars-2015-2016.html|172096|
            225|Ultimate End (2015-2016)|5|/30370-ultimate-end-2015-2016.html|213085|
            226|A-Force (2015)|5|/7426-a-force-2015.html|43731|
            227|Old Man Logan|5|||
            228|Weirdworld (2015)|5|/31201-weirdworld-2015.html|220650|
            229|Silver Surfer (2014-2016)|15|/25392-silver-surfer-2014-2016.html|175218|
            230|Secret Wars Too (2016-)|Full|/24965-secret-wars-too-2016.html|172198|
        """),
        Event("war-of-the-realms", "War of the Realms", "Marvel", 63, setOf(3, 15, 23, 39, 43, 60), """
            1|Tony Stark: Iron Man (2018-)|12|/6236-tony-stark-iron-man-2018.html|32338|
            2|Tony Stark: Iron Man (2018-)|13|/6236-tony-stark-iron-man-2018.html|32337|
            3|War Of The Realms (2019-)|1|/6510-war-of-the-realms-2019.html|33783|
            4|War Of The Realms: Punisher (2019-)|1|/6519-war-of-the-realms-punisher-2019.html|33801|
            5|War Of The Realms: Punisher (2019-)|2|/6519-war-of-the-realms-punisher-2019.html|33800|
            6|War Of The Realms: Punisher (2019-)|3|/6519-war-of-the-realms-punisher-2019.html|33799|
            7|War Of The Realms: Journey Into Mystery (2019-)|1|/6517-war-of-the-realms-journey-into-mystery-2019.html|33794|
            8|The Unbeatable Squirrel Girl Vol. 2 (2015)|43|/6087-the-unbeatable-squirrel-girl-vol-2-2015.html|31357|
            9|Asgardians of the Galaxy (2018-)|8|/420-asgardians-of-the-galaxy.html|2424|
            10|Fantastic Four (2018-)|10|/2013-fantastic-four.html|10905|
            11|Venom (2018-2021)|13|/6453-venom-2018.html|33534|
            12|Venom (2018-2021)|14|/6453-venom-2018.html|33533|
            13|Venom (2018-2021)|15|/6453-venom-2018.html|33532|
            14|Thor (2018-)|12|/6179-thor-2018.html|31976|
            15|War Of The Realms (2019-)|2|/6510-war-of-the-realms-2019.html|33781|
            16|Asgardians of the Galaxy (2018-)|9|/420-asgardians-of-the-galaxy.html|2423|
            17|Avengers (2018-2023)|18|/471-avengers.html|2770|
            18|Avengers (2018-2023)|19|/471-avengers.html|2769|
            19|War Of The Realms: Uncanny X-Men (2019-)|1|/6521-war-of-the-realms-uncanny-x-men-2019.html|33807|
            20|Giant-Man (2019)|1|/2280-giant-man.html|12074|
            21|Giant-Man (2019)|2|/2280-giant-man.html|12073|
            22|Giant-Man (2019)|3|/2280-giant-man.html|12072|
            23|War Of The Realms (2019-)|3|/6510-war-of-the-realms-2019.html|33780|
            24|War Of The Realms Strikeforce: The Dark Elf Realm (2019)|1|/6514-war-of-the-realms-strikeforce-the-dark-elf-realm-2019.html|33787|
            25|War Of The Realms Strikeforce: The War Avengers (2019)|1|/6516-war-of-the-realms-strikeforce-the-war-avengers-2019.html|33789|
            26|War Of The Realms Strikeforce: The Land Of Giants (2019)|1|/6515-war-of-the-realms-strikeforce-the-land-of-giants-2019.html|33788|
            27|Superior Spider-Man (2018-)|7|/5340-superior-spider-man.html|27939|
            28|Superior Spider-Man (2018-)|8|/5340-superior-spider-man.html|27938|
            29|Moon Girl and Devil Dinosaur (2015-)|43|/3817-moon-girl-and-devil-dinosaur.html|19535|
            30|War Of The Realms: Journey Into Mystery (2019-)|2|/6517-war-of-the-realms-journey-into-mystery-2019.html|33793|
            31|War Of The Realms: Journey Into Mystery (2019-)|3|/6517-war-of-the-realms-journey-into-mystery-2019.html|33792|
            32|War Of The Realms: Journey Into Mystery (2019-)|4|/6517-war-of-the-realms-journey-into-mystery-2019.html|33791|
            33|War Of The Realms: New Agents Of Atlas (2019)|1|/6518-war-of-the-realms-new-agents-of-atlas-2019.html|33798|
            34|War Of The Realms: New Agents Of Atlas (2019)|2|/6518-war-of-the-realms-new-agents-of-atlas-2019.html|33797|
            35|Champions (2019-)|5|/1169-champions.html|6342|
            36|The Unbeatable Squirrel Girl Vol. 2 (2015)|44|/6087-the-unbeatable-squirrel-girl-vol-2-2015.html|31356|
            37|The Unbeatable Squirrel Girl Vol. 2 (2015)|45|/6087-the-unbeatable-squirrel-girl-vol-2-2015.html|31355|
            38|The Unbeatable Squirrel Girl Vol. 2 (2015)|46|/6087-the-unbeatable-squirrel-girl-vol-2-2015.html|31354|
            39|War Of The Realms (2019-)|4|/6510-war-of-the-realms-2019.html|33779|
            40|Thor (2018-)|13|/6179-thor-2018.html|31975|
            41|War Of The Realms: Uncanny X-Men (2019-)|2|/6521-war-of-the-realms-uncanny-x-men-2019.html|33806|
            42|War Of The Realms: Uncanny X-Men (2019-)|3|/6521-war-of-the-realms-uncanny-x-men-2019.html|33805|
            43|War Of The Realms (2019-)|5|/6510-war-of-the-realms-2019.html|33778|
            44|War Of The Realms: War Scrolls (2019-)|1|/6522-war-of-the-realms-war-scrolls-2019.html|33810|
            45|War Of The Realms: War Scrolls (2019-)|2|/6522-war-of-the-realms-war-scrolls-2019.html|33809|
            46|War Of The Realms: War Scrolls (2019-)|3|/6522-war-of-the-realms-war-scrolls-2019.html|33808|
            47|War Of The Realms: Journey Into Mystery (2019-)|5|/6517-war-of-the-realms-journey-into-mystery-2019.html|33790|
            48|Champions (2019-)|6|/1169-champions.html|6341|
            49|Deadpool (2018-)|13|/1575-deadpool.html|8568|
            50|Deadpool (2018-)|14|/1575-deadpool.html|8567|
            51|War Of The Realms: New Agents Of Atlas (2019)|3|/6518-war-of-the-realms-new-agents-of-atlas-2019.html|33796|
            52|War Of The Realms: New Agents Of Atlas (2019)|4|/6518-war-of-the-realms-new-agents-of-atlas-2019.html|33795|
            53|War Of The Realms: Spider-Man & The League Of Realms (2019-)|1|/6520-war-of-the-realms-spider-man--the-league-of-realms-2019.html|33804|
            54|War Of The Realms: Spider-Man & The League Of Realms (2019-)|2|/6520-war-of-the-realms-spider-man--the-league-of-realms-2019.html|33803|
            55|War Of The Realms: Spider-Man & The League Of Realms (2019-)|3|/6520-war-of-the-realms-spider-man--the-league-of-realms-2019.html|33802|
            56|Captain Marvel (2019-)|6|/1100-captain-marvel.html|5969|
            57|Captain Marvel (2019-)|7|/1100-captain-marvel.html|5968|
            58|Avengers (2018-2023)|20|/471-avengers.html|2768|
            59|Asgardians of the Galaxy (2018-)|10|/420-asgardians-of-the-galaxy.html|2422|
            60|War Of The Realms (2019-)|6|/6510-war-of-the-realms-2019.html|33777|
            61|Thor (2018-)|14|/6179-thor-2018.html|31974|
            62|Thor (2018-)|15|/6179-thor-2018.html|31973|
            63|War Of The Realms Omega (2019)|1|/6512-war-of-the-realms-omega-2019.html|33785|
        """),
    )

    fun get(slug: String, page: Int): InkShelf2GuidePage? {
        val event = events.firstOrNull { it.slug == slug } ?: return null
        return InkShelf2GuidePage(if (page == 1) event.entries.map { it.issue } else emptyList(), false)
    }

    // Source website identifiers are retained for provenance, never used as extension URLs.
    fun websiteChapterId(slug: String, position: Int): Int? =
        events.firstOrNull { it.slug == slug }?.entries
            ?.firstOrNull { it.issue.position == position }?.websiteChapterId
}

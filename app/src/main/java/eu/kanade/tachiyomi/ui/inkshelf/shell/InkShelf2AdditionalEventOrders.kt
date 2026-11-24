package eu.kanade.tachiyomi.ui.inkshelf.shell

/** Reading-order facts from the user-supplied saved event indexes, 24 Nov 2026.
 * No HTML, account data, image URLs or comic pages retained. Four indexes have no short path.
 */
internal object InkShelf2AdditionalEventOrders {
    val events = listOf(
        InkShelf2SavedEventOrders.Event("batman-zero-year", "Batman: Zero Year", "DC", 25, setOf(), """
            1|Batman (2011-2016)|0|/13578-batman-2011.html|90088|
            2|Batman (2011-2016)|21|/13578-batman-2011.html|90109|
            3|Batman (2011-2016)|22|/13578-batman-2011.html|90110|
            4|Batman (2011-2016)|23|/13578-batman-2011.html|90111|
            5|Batman (2011-2016)|24|/13578-batman-2011.html|90116|
            6|Detective Comics (2011)|25|/13397-detective-comics-2011.html|89087|
            7|Batwing|25|/9657-batwing.html|63061|
            8|Action Comics Vol. 2|25|||
            9|Green Arrow (2011)|25|/16232-green-arrow-2011.html|110138|
            10|Batman (2011-2016)|25|/13578-batman-2011.html|90117|
            11|Nightwing (2011-2014)|25|/22738-nightwing-2011-2014.html|156511|
            12|Batgirl (2011)|25|/9565-batgirl-2011.html|62284|
            13|Green Lantern Corps (2011)|25|/9269-green-lantern-corps-2011.html|60289|
            14|Red Hood And The Outlaws (2011-2015)|25|/24024-red-hood-and-the-outlaws-2011-2015.html|165840|
            15|Batwoman (2011)|25|/9659-batwoman-2011.html|63100|
            16|Birds of Prey (2011)|25|/9987-birds-of-prey-2011.html|65577|
            17|Catwoman (2011)|25|/11328-catwoman-2011.html|234372|
            18|The Flash (2011)|25|/8835-the-flash-2011.html|57408|
            19|Batman (2011-2016)|26|/13578-batman-2011.html|90118|
            20|Batman (2011-2016)|27|/13578-batman-2011.html|90119|
            21|Batman (2011-2016)|29|/13578-batman-2011.html|90121|
            22|Batman (2011-2016)|30|/13578-batman-2011.html|90122|
            23|Batman (2011-2016)|31|/13578-batman-2011.html|90123|
            24|Batman (2011-2016)|32|/13578-batman-2011.html|90124|
            25|Batman (2011-2016)|33|/13578-batman-2011.html|90125|
        """),
        InkShelf2SavedEventOrders.Event("blood-hunt", "Blood Hunt", "Marvel", 54, setOf(1, 4, 15, 34, 41, 50), """
            1|FCBD 2024 Collection|Full|/32673-fcbd-2024-collection.html|230737|Blood Hunt-X-Men
            2|Vengeance of the Moon Knight (2024-)|5|/32370-vengeance-of-the-moon-knight-2024.html|241068|
            3|Dracula: Blood Hunt (2024-)|1|/32678-dracula-blood-hunt-2024.html|230728|
            4|Blood Hunt (2024-)|1|/32660-blood-hunt-2024.html|241899|
            5|Fantastic Four (2022-2025)|21|/2014-fantastic-four.html|231252|
            6|Blood Hunters (2024-)|1|/32681-blood-hunters-2024.html|240640|
            7|Blood Hunters (2024-)|2|/32681-blood-hunters-2024.html|240641|
            8|Blood Hunters (2024-)|3|/32681-blood-hunters-2024.html|240642|
            9|Blood Hunters (2024-)|4|/32681-blood-hunters-2024.html|240643|
            10|X-Men: Blood Hunt - Psylocke (2024)|1|/32804-x-men-blood-hunt-psylocke-2024.html|241926|
            11|The Avengers (2023-)|14|/5589-the-avengers-2023.html|240569|
            12|The Avengers (2023-)|15|/5589-the-avengers-2023.html|240570|
            13|The Avengers (2023-)|16|/5589-the-avengers-2023.html|240571|
            14|Doctor Strange (2023-)|15|/1740-doctor-strange.html|230751|
            15|Blood Hunt (2024-)|2|/32660-blood-hunt-2024.html|241901|
            16|Strange Academy: Blood Hunt (2024-)|1|/32677-strange-academy-blood-hunt-2024.html|230726|
            17|Strange Academy: Blood Hunt (2024-)|2|/32677-strange-academy-blood-hunt-2024.html|231279|
            18|X-Men: Blood Hunt - Jubilee (2024-)|1|/32760-x-men-blood-hunt-jubilee-2024.html|231296|
            19|The Amazing Spider-Man (2022-2025)|49|/5568-the-amazing-spider-man-2022.html|230759|
            20|The Amazing Spider-Man: Blood Hunt (2024-)|1|/32694-the-amazing-spider-man-blood-hunt-2024.html|230818|
            21|The Amazing Spider-Man: Blood Hunt (2024-)|2|/32694-the-amazing-spider-man-blood-hunt-2024.html|231256|
            22|The Amazing Spider-Man: Blood Hunt (2024-)|3|/32694-the-amazing-spider-man-blood-hunt-2024.html|241953|
            23|X-Men: Blood Hunt - Laura Kinney The Wolverine (2024-)|1|/32838-x-men-blood-hunt-laura-kinney-the-wolverine-2024.html|241925|
            24|Venom (2021-2024)|33|/6454-venom-2021.html|230748|
            25|Venom (2021-2024)|34|/6454-venom-2021.html|231084|
            26|Union Jack the Ripper: Blood Hunt (2024-)|1|/32708-union-jack-the-ripper-blood-hunt-2024.html|230939|
            27|Union Jack the Ripper: Blood Hunt (2024-)|2|/32708-union-jack-the-ripper-blood-hunt-2024.html|241948|
            28|Union Jack the Ripper: Blood Hunt (2024-)|3|/32708-union-jack-the-ripper-blood-hunt-2024.html|241949|
            29|Midnight Sons: Blood Hunt (2024-)|1|/32718-midnight-sons-blood-hunt-2024.html|231009|
            30|Midnight Sons: Blood Hunt (2024-)|2|/32718-midnight-sons-blood-hunt-2024.html|241951|
            31|Black Panther: Blood Hunt (2024-)|1|/32714-black-panther-blood-hunt-2024.html|230992|
            32|Black Panther: Blood Hunt (2024-)|2|/32714-black-panther-blood-hunt-2024.html|231336|
            33|Black Panther: Blood Hunt (2024-)|3|/32714-black-panther-blood-hunt-2024.html|241952|
            34|Blood Hunt (2024-)|3|/32660-blood-hunt-2024.html|241903|
            35|Miles Morales: Spider-Man (2022-)|21|/3762-miles-morales-spider-man.html|240605|
            36|Dracula: Blood Hunt (2024-)|2|/32678-dracula-blood-hunt-2024.html|231340|
            37|Dracula: Blood Hunt (2024-)|3|/32678-dracula-blood-hunt-2024.html|241950|
            38|Miles Morales: Spider-Man (2022-)|22|/3762-miles-morales-spider-man.html|240606|
            39|Doctor Strange (2023-)|16|/1740-doctor-strange.html|231332|
            40|Doctor Strange (2023-)|17|/1740-doctor-strange.html|241896|
            41|Blood Hunt (2024-)|4|/32660-blood-hunt-2024.html|241905|
            42|Vengeance of the Moon Knight (2024-)|6|/32370-vengeance-of-the-moon-knight-2024.html|241069|
            43|Vengeance of the Moon Knight (2024-)|7|/32370-vengeance-of-the-moon-knight-2024.html|241070|
            44|Werewolf By Night: Blood Hunt (2024-)|1|/32812-werewolf-by-night-blood-hunt-2024.html|241924|
            45|Wolverine: Blood Hunt (2024-)|1|/32732-wolverine-blood-hunt-2024.html|231093|
            46|Wolverine: Blood Hunt (2024-)|2|/32732-wolverine-blood-hunt-2024.html|231319|
            47|Wolverine: Blood Hunt (2024-)|3|/32732-wolverine-blood-hunt-2024.html|241928|
            48|X-Men: Blood Hunt - Magik (2024)|1|/32787-x-men-blood-hunt-magik-2024.html|241927|
            49|The Incredible Hulk: Blood Hunt (2024-)|1|/32824-the-incredible-hulk-blood-hunt-2024.html|241923|
            50|Blood Hunt (2024-)|5|/32660-blood-hunt-2024.html|241907|
            51|Wolverine: Blood Hunt (2024-)|4|/32732-wolverine-blood-hunt-2024.html|232149|
            52|Strange Academy: Blood Hunt (2024-)|3|/32677-strange-academy-blood-hunt-2024.html|232151|
            53|Fantastic Four (2022-2025)|22|/2014-fantastic-four.html|232255|
            54|Midnight Sons: Blood Hunt (2024-)|3|/32718-midnight-sons-blood-hunt-2024.html|232232|
        """),
        InkShelf2SavedEventOrders.Event("civil-war-ii", "Civil War II", "Marvel", 152, setOf(14, 15, 16, 17, 18, 19, 20, 51, 81, 122, 137, 138, 145, 146, 152), """
            1|The Totally Awesome Hulk (2016-)|7|/34464-the-totally-awesome-hulk-2016.html|248073|
            2|The Totally Awesome Hulk (2016-)|8|/34464-the-totally-awesome-hulk-2016.html|248074|
            3|Invincible Iron Man (2015)|6|/6977-invincible-iron-man-2015.html|39815|
            4|Invincible Iron Man (2015)|7|/6977-invincible-iron-man-2015.html|39816|
            5|Invincible Iron Man (2015)|8|/6977-invincible-iron-man-2015.html|39817|
            6|Invincible Iron Man (2015)|9|/6977-invincible-iron-man-2015.html|39818|
            7|Invincible Iron Man (2015)|10|/6977-invincible-iron-man-2015.html|39819|
            8|Invincible Iron Man (2015)|11|/6977-invincible-iron-man-2015.html|39820|
            9|The Ultimates (2016-)|7|/29499-the-ultimates-2016.html|206578|
            10|Civil War II|0|||
            11|All-New Wolverine (2016)|8|/37562-all-new-wolverine-2016.html|269362|
            12|All-New Wolverine (2016)|9|/37562-all-new-wolverine-2016.html|269365|
            13|Ms. Marvel (2015-)|7|/3861-ms-marvel.html|19846|
            14|Civil War II: Ulysses Infinite Comic|1|/11672-civil-war-ii-ulysses-infinite-comic.html|77442|
            15|Civil War II: Ulysses Infinite Comic|2|/11672-civil-war-ii-ulysses-infinite-comic.html|77443|
            16|Civil War II: Ulysses Infinite Comic|3|/11672-civil-war-ii-ulysses-infinite-comic.html|77444|
            17|Civil War II: Ulysses Infinite Comic|4|/11672-civil-war-ii-ulysses-infinite-comic.html|77445|
            18|Civil War II: Ulysses Infinite Comic|5|/11672-civil-war-ii-ulysses-infinite-comic.html|77446|
            19|Civil War II: Ulysses Infinite Comic|6|/11672-civil-war-ii-ulysses-infinite-comic.html|77447|
            20|Civil War II (2016-2017)|1|/1222-civil-war-ii.html|6548|
            21|Captain Marvel (2016)|6|/11195-captain-marvel-2016.html|74174|
            22|Free Comic Book Day 2016 (Civil War II)|Full|/15347-free-comic-book-day-2016-civil-war-ii.html|104338|
            23|Civil War II: Gods of War|1|/11669-civil-war-ii-gods-of-war.html|77433|
            24|New Avengers (2015)|12|/22521-new-avengers-2015.html|155131|
            25|Civil War II: Choosing Sides|1|/11668-civil-war-ii-choosing-sides.html|77427|
            26|Deadpool (2016)|14|/13115-deadpool-2016.html|86527|
            27|Deadpool (2016)|15|/13115-deadpool-2016.html|86528|
            28|Deadpool (2016)|16|/13115-deadpool-2016.html|86529|
            29|Deadpool (2016)|17|/13115-deadpool-2016.html|86530|
            30|Deadpool (2016)|18|/13115-deadpool-2016.html|86531|
            31|Spider-Man 2099 (2015-)|13|/4940-spider-man-2099.html|25891|
            32|Spider-Man 2099 (2015-)|14|/4940-spider-man-2099.html|25890|
            33|Spider-Man 2099 (2015-)|15|/4940-spider-man-2099.html|25889|
            34|Spider-Man 2099 (2015-)|16|/4940-spider-man-2099.html|25888|
            35|Civil War II: Amazing Spider-Man|1|/11667-civil-war-ii-amazing-spider-man.html|77423|
            36|Civil War II: Amazing Spider-Man|2|/11667-civil-war-ii-amazing-spider-man.html|77424|
            37|Civil War II: Amazing Spider-Man|3|/11667-civil-war-ii-amazing-spider-man.html|77425|
            38|Civil War II: Amazing Spider-Man|4|/11667-civil-war-ii-amazing-spider-man.html|77426|
            39|Spider-Woman (2015-)|9|/4981-spider-woman.html|26105|
            40|All-New Wolverine (2016)|10|/37562-all-new-wolverine-2016.html|269368|
            41|All-New Wolverine (2016)|11|/37562-all-new-wolverine-2016.html|269371|
            42|All-New Wolverine (2016)|12|/37562-all-new-wolverine-2016.html|269374|
            43|Power Man and Iron Fist (2016)|6|/4249-power-man-and-iron-fist.html|21676|
            44|The Ultimates (2016-)|8|/29499-the-ultimates-2016.html|206579|
            45|Patsy Walker, A.K.A. Hellcat! (2016-)|8|/4167-patsy-walker,-aka-hellcat!.html|21309|
            46|A-Force (2016)|8|/7427-a-force-2016.html|43739|
            47|A-Force (2016)|9|/7427-a-force-2016.html|43740|
            48|A-Force (2016)|10|/7427-a-force-2016.html|43741|
            49|Scarlet Witch (2015-)|9|/4653-scarlet-witch.html|23875|
            50|Spider-Man (2016-2018)|6|/4935-spider-man.html|25851|
            51|Civil War II (2016-2017)|2|/1222-civil-war-ii.html|6547|
            52|Agents of S.H.I.E.L.D.|7|/7478-agents-of-shield.html|43947|
            53|Uncanny Inhumans (2015-)|11|/6331-uncanny-inhumans-2015.html|32850|
            54|Uncanny Inhumans (2015-)|12|/6331-uncanny-inhumans-2015.html|32849|
            55|Uncanny Inhumans (2015-)|13|/6331-uncanny-inhumans-2015.html|32848|
            56|Uncanny Inhumans (2015-)|14|/6331-uncanny-inhumans-2015.html|32847|
            57|Invincible Iron Man (2015)|12|/6977-invincible-iron-man-2015.html|39821|
            58|Invincible Iron Man (2015)|13|/6977-invincible-iron-man-2015.html|39822|
            59|Captain America: Sam Wilson|10|/11118-captain-america-sam-wilson.html|73556|
            60|Civil War II: Choosing Sides|2|/11668-civil-war-ii-choosing-sides.html|77428|
            61|Civil War II: X-Men|1|/11673-civil-war-ii-x-men.html|77448|
            62|Civil War II: X-Men|2|/11673-civil-war-ii-x-men.html|77449|
            63|Civil War II: X-Men|3|/11673-civil-war-ii-x-men.html|77450|
            64|Civil War II: X-Men|4|/11673-civil-war-ii-x-men.html|77451|
            65|Captain Marvel (2016)|7|/11195-captain-marvel-2016.html|74175|
            66|Ms. Marvel (2015-)|8|/3861-ms-marvel.html|19845|
            67|Ms. Marvel (2015-)|9|/3861-ms-marvel.html|19844|
            68|Ms. Marvel (2015-)|10|/3861-ms-marvel.html|19843|
            69|Captain America: Sam Wilson|11|/11118-captain-america-sam-wilson.html|73557|
            70|Captain America: Sam Wilson|12|/11118-captain-america-sam-wilson.html|73558|
            71|Captain America: Sam Wilson|13|/11118-captain-america-sam-wilson.html|73559|
            72|Civil War II: Kingpin|1|/11670-civil-war-ii-kingpin.html|77437|
            73|Civil War II: Kingpin|2|/11670-civil-war-ii-kingpin.html|77438|
            74|Civil War II: Kingpin|3|/11670-civil-war-ii-kingpin.html|77439|
            75|Civil War II: Kingpin|4|/11670-civil-war-ii-kingpin.html|77440|
            76|Civil War II: Gods of War|2|/11669-civil-war-ii-gods-of-war.html|77434|
            77|Civil War II: Gods of War|3|/11669-civil-war-ii-gods-of-war.html|77435|
            78|Civil War II: Gods of War|4|/11669-civil-war-ii-gods-of-war.html|77436|
            79|Captain America: Steve Rogers (2016-)|4|/1088-captain-america-steve-rogers.html|5872|
            80|Spider-Man (2016-2018)|7|/4935-spider-man.html|25850|
            81|Civil War II (2016-2017)|3|/1222-civil-war-ii.html|6546|
            82|Spider-Man (2016-2018)|8|/4935-spider-man.html|25849|
            83|Civil War II: Choosing Sides|3|/11668-civil-war-ii-choosing-sides.html|77429|
            84|Civil War II: Choosing Sides|4|/11668-civil-war-ii-choosing-sides.html|77430|
            85|Civil War II: Choosing Sides|5|/11668-civil-war-ii-choosing-sides.html|77431|
            86|Civil War II: Choosing Sides|6|/11668-civil-war-ii-choosing-sides.html|77432|
            87|Nova (2015)|8|/38445-nova-2015.html|272595|
            88|Nova (2015)|9|/38445-nova-2015.html|272596|
            89|Spider-Woman (2015-)|10|/4981-spider-woman.html|26104|
            90|Uncanny Avengers [II] (2015-)|13|/30449-uncanny-avengers-ii-2015.html|213850|
            91|Spider-Woman (2015-)|11|/4981-spider-woman.html|26103|
            92|Uncanny Avengers [II] (2015-)|14|/30449-uncanny-avengers-ii-2015.html|213851|
            93|All-New, All-Different Avengers|13|/7719-all-new-all-different-avengers.html|237252|
            94|All-New, All-Different Avengers|14|/7719-all-new-all-different-avengers.html|237253|
            95|All-New, All-Different Avengers|15|/7719-all-new-all-different-avengers.html|237254|
            96|Captain Marvel (2016)|8|/11195-captain-marvel-2016.html|74176|
            97|Ms. Marvel (2015-)|11|/3861-ms-marvel.html|19842|
            98|Power Man and Iron Fist (2016)|7|/4249-power-man-and-iron-fist.html|21675|
            99|Power Man and Iron Fist (2016)|8|/4249-power-man-and-iron-fist.html|21674|
            100|Power Man and Iron Fist (2016)|9|/4249-power-man-and-iron-fist.html|21673|
            101|Mockingbird (2016)|6|/21916-mockingbird-2016.html|151114|
            102|New Avengers (2015)|13|/22521-new-avengers-2015.html|155132|
            103|New Avengers (2015)|14|/22521-new-avengers-2015.html|155133|
            104|New Avengers (2015)|15|/22521-new-avengers-2015.html|155134|
            105|New Avengers (2015)|16|/22521-new-avengers-2015.html|155135|
            106|New Avengers (2015)|17|/22521-new-avengers-2015.html|155136|
            107|New Avengers (2015)|18|/22521-new-avengers-2015.html|155137|
            108|The Ultimates (2016-)|9|/29499-the-ultimates-2016.html|206580|
            109|Squadron Supreme (2015-)|9|/5004-squadron-supreme.html|26320|
            110|Squadron Supreme (2015-)|10|/5004-squadron-supreme.html|26319|
            111|Squadron Supreme (2015-)|11|/5004-squadron-supreme.html|26318|
            112|Squadron Supreme (2015-)|12|/5004-squadron-supreme.html|26317|
            113|Guardians of the Galaxy (2015-)|11|/2493-guardians-of-the-galaxy.html|12928|
            114|Rocket Raccoon & Groot (2016-)|8|/24438-rocket-raccoon-groot-2016.html|168603|
            115|Rocket Raccoon & Groot (2016-)|9|/24438-rocket-raccoon-groot-2016.html|168604|
            116|Rocket Raccoon & Groot (2016-)|10|/24438-rocket-raccoon-groot-2016.html|168605|
            117|Venom: Space Knight|11|/9360-venom-space-knight.html|60741|
            118|Venom: Space Knight|12|/9360-venom-space-knight.html|60742|
            119|Venom: Space Knight|13|/9360-venom-space-knight.html|60743|
            120|The Ultimates (2016-)|10|/29499-the-ultimates-2016.html|206581|
            121|The Ultimates (2016-)|11|/29499-the-ultimates-2016.html|206582|
            122|Civil War II (2016-2017)|4|/1222-civil-war-ii.html|6545|
            123|Captain Marvel (2016)|9|/11195-captain-marvel-2016.html|74177|
            124|Captain Marvel (2016)|10|/11195-captain-marvel-2016.html|74178|
            125|The Accused (2016-)|1|/27296-the-accused-2016.html|189733|
            126|The Fallen (2016-)|Full|/28018-the-fallen-2016.html|195088|
            127|The Totally Awesome Hulk (2016-)|9|/34464-the-totally-awesome-hulk-2016.html|248075|
            128|The Totally Awesome Hulk (2016-)|10|/34464-the-totally-awesome-hulk-2016.html|248076|
            129|The Totally Awesome Hulk (2016-)|11|/34464-the-totally-awesome-hulk-2016.html|248077|
            130|The Totally Awesome Hulk (2016-)|12|/34464-the-totally-awesome-hulk-2016.html|248078|
            131|Mockingbird (2016)|7|/21916-mockingbird-2016.html|151115|
            132|Mockingbird (2016)|8|/21916-mockingbird-2016.html|151116|
            133|Agents of S.H.I.E.L.D.|8|/7478-agents-of-shield.html|43948|
            134|Agents of S.H.I.E.L.D.|9|/7478-agents-of-shield.html|43949|
            135|Agents of S.H.I.E.L.D.|10|/7478-agents-of-shield.html|43950|
            136|Invincible Iron Man (2015)|14|/6977-invincible-iron-man-2015.html|39823|
            137|Civil War II (2016-2017)|5|/1222-civil-war-ii.html|6544|
            138|Civil War II (2016-2017)|6|/1222-civil-war-ii.html|6543|
            139|Guardians of the Galaxy (2015-)|12|/2493-guardians-of-the-galaxy.html|12927|
            140|Guardians of the Galaxy (2015-)|13|/2493-guardians-of-the-galaxy.html|12926|
            141|Captain America: Steve Rogers (2016-)|5|/1088-captain-america-steve-rogers.html|5871|
            142|Captain America: Steve Rogers (2016-)|6|/1088-captain-america-steve-rogers.html|5870|
            143|Thunderbolts (2016-)|5|/6199-thunderbolts-2016.html|32131|
            144|Spider-Man (2016-2018)|9|/4935-spider-man.html|25848|
            145|Civil War II (2016-2017)|7|/1222-civil-war-ii.html|6542|
            146|Civil War II (2016-2017)|8|/1222-civil-war-ii.html|6541|
            147|Spider-Man (2016-2018)|10|/4935-spider-man.html|25847|
            148|The Ultimates (2016-)|12|/29499-the-ultimates-2016.html|206583|
            149|Uncanny Avengers [II] (2015-)|15|/30449-uncanny-avengers-ii-2015.html|213852|
            150|Uncanny Avengers [II] (2015-)|16|/30449-uncanny-avengers-ii-2015.html|213853|
            151|Uncanny Avengers [II] (2015-)|17|/30449-uncanny-avengers-ii-2015.html|213854|
            152|Civil War II: The Oath|Full|/11671-civil-war-ii-the-oath.html|77441|
        """),
        InkShelf2SavedEventOrders.Event("dc-k-o", "DC K.O.", "DC", 40, setOf(1, 2, 11, 31, 38, 40), """
            1|Justice League: The Omega Act Special (2025)|1|/33834-justice-league-the-omega-act-special-2025.html|243839|
            2|DC K.O. (2025-)|1|/33849-dc-ko-2025.html|244237|
            3|DC K.O.: Green Lantern Galactic Slam (2026-)|1|/34036-dc-ko-green-lantern-galactic-slam-2026.html|245491|
            4|Superman (2023-)|31|/5345-superman.html|244388|
            5|DC K.O.: Knightfight (2025)|1|/33920-dc-ko-knightfight-2025.html|244578|
            6|DC K.O.: Knightfight (2025)|2|/33920-dc-ko-knightfight-2025.html|244977|
            7|Titans (2023-)|28|/6213-titans-2023.html|244302|
            8|Titans (2023-)|29|/6213-titans-2023.html|244762|
            9|The Flash (2023-)|26|/6968-the-flash-2023.html|244377|
            10|The Flash (2023-)|27|/6968-the-flash-2023.html|244850|
            11|DC K.O. (2025-)|2|/33849-dc-ko-2025.html|244848|
            12|Superman (2023-)|32|/5345-superman.html|244841|
            13|Justice League Unlimited (2024-)|12|/33179-justice-league-unlimited-2024.html|244390|
            14|Justice League Unlimited (2024-)|13|/33179-justice-league-unlimited-2024.html|244844|
            15|Justice League Unlimited (2024-)|14|/33179-justice-league-unlimited-2024.html|245244|
            16|Justice League Unlimited (2024-)|15|/33179-justice-league-unlimited-2024.html|245541|
            17|Justice League Unlimited (2024-)|16|/33179-justice-league-unlimited-2024.html|246129|
            18|DC K.O.: The Kids Are All Fight Special (2026)|1|/34041-dc-ko-the-kids-are-all-fight-special-2026.html|245543|
            19|Titans (2023-)|31|/6213-titans-2023.html|245488|
            20|Titans (2023-)|32|/6213-titans-2023.html|246058|
            21|DC K.O.: Superman vs. Captain Atom (2025-)|1|/33971-dc-ko-superman-vs-captain-atom-2025.html|244976|
            22|Aquaman (2025-)|12|/33257-aquaman-2025.html|245043|
            23|DC K.O.: Wonder Woman vs. Lobo (2025)|1|/33981-dc-ko-wonder-woman-vs-lobo-2025.html|245045|
            24|Titans (2023-)|30|/6213-titans-2023.html|245106|
            25|DC K.O.: Harley Quinn vs. Zatanna (2025-)|1|/33989-dc-ko-harley-quinn-vs-zatanna-2025.html|245109|
            26|The Flash (2023-)|28|/6968-the-flash-2023.html|245235|
            27|Superman (2023-)|33|/5345-superman.html|245233|
            28|DC K.O.: The Joker vs. Red Hood (2025-)|1|/34002-dc-ko-the-joker-vs-red-hood-2025.html|245239|
            29|The Flash (2023-)|29|/6968-the-flash-2023.html|245539|
            30|The Flash (2023-)|30|/6968-the-flash-2023.html|246122|
            31|DC K.O. (2025-)|3|/33849-dc-ko-2025.html|245412|
            32|DC K.O.: Boss Battle (2026-)|1|/34060-dc-ko-boss-battle-2026.html|245934|
            33|Aquaman (2025-)|13|/33257-aquaman-2025.html|245415|
            34|Aquaman (2025-)|14|/33257-aquaman-2025.html|246000|
            35|DC K.O.: Knightfight (2025)|3|/33920-dc-ko-knightfight-2025.html|245316|
            36|DC K.O.: Knightfight (2025)|4|/33920-dc-ko-knightfight-2025.html|245933|
            37|Superman (2023-)|34|/5345-superman.html|245540|
            38|DC K.O. (2025-)|4|/33849-dc-ko-2025.html|245995|
            39|Superman (2023-)|35|/5345-superman.html|246126|
            40|DC K.O. (2025-)|5|/33849-dc-ko-2025.html|246186|
        """),
        InkShelf2SavedEventOrders.Event("death-of-the-family", "Death of the Family", "DC", 30, setOf(4, 9, 16, 27, 28), """
            1|Batgirl (2011)|13|/9565-batgirl-2011.html|62272|
            2|Catwoman (2011)|13|/11328-catwoman-2011.html|234360|
            3|Catwoman (2011)|14|/11328-catwoman-2011.html|234361|
            4|Batman (2011-2016)|13|/13578-batman-2011.html|90101|
            5|Batman and Robin (2011)|13|/8870-batman-and-robin-2011.html|57605|
            6|Batman and Robin (2011)|14|/8870-batman-and-robin-2011.html|57606|
            7|Suicide Squad (2011-2014)|14|/26504-suicide-squad-2011-2014.html|183114|
            8|Suicide Squad (2011-2014)|15|/26504-suicide-squad-2011-2014.html|183115|
            9|Batman (2011-2016)|14|/13578-batman-2011.html|90102|
            10|Detective Comics (2011)|15|/13397-detective-comics-2011.html|89073|
            11|Batgirl (2011)|14|/9565-batgirl-2011.html|62273|
            12|Nightwing (2011-2014)|13|/22738-nightwing-2011-2014.html|156499|
            13|Nightwing (2011-2014)|14|/22738-nightwing-2011-2014.html|156500|
            14|Batgirl (2011)|15|/9565-batgirl-2011.html|62274|
            15|Batgirl (2011)|16|/9565-batgirl-2011.html|62275|
            16|Batman (2011-2016)|15|/13578-batman-2011.html|90103|
            17|Batman and Robin (2011)|15|/8870-batman-and-robin-2011.html|57607|
            18|Batman and Robin (2011)|16|/8870-batman-and-robin-2011.html|57608|
            19|Detective Comics (2011)|16|/13397-detective-comics-2011.html|89074|
            20|Detective Comics (2011)|17|/13397-detective-comics-2011.html|89075|
            21|Nightwing (2011-2014)|15|/22738-nightwing-2011-2014.html|156501|
            22|Nightwing (2011-2014)|16|/22738-nightwing-2011-2014.html|156502|
            23|Red Hood And The Outlaws (2011-2015)|15|/24024-red-hood-and-the-outlaws-2011-2015.html|165830|
            24|Teen Titans (2011)|15|/17721-teen-titans-2011.html|119736|
            25|Red Hood And The Outlaws (2011-2015)|16|/24024-red-hood-and-the-outlaws-2011-2015.html|165831|
            26|Teen Titans (2011)|16|/17721-teen-titans-2011.html|119737|
            27|Batman (2011-2016)|16|/13578-batman-2011.html|90104|
            28|Batman (2011-2016)|17|/13578-batman-2011.html|90105|
            29|Batman and Robin (2011)|17|/8870-batman-and-robin-2011.html|57609|
            30|Red Hood And The Outlaws (2011-2015)|17|/24024-red-hood-and-the-outlaws-2011-2015.html|165832|
        """),
        InkShelf2SavedEventOrders.Event("fear-itself", "Fear Itself", "Marvel", 124, setOf(5, 9, 39, 75, 76, 113, 116, 122, 123, 124), """
            1|The Mighty Thor (2011-2012)|7|/28631-the-mighty-thor-2011-2012.html|199332|
            2|Fear Itself: Book Of The Skull|Full|/14921-fear-itself-book-of-the-skull.html|100543|
            3|Fear Itself: Sin's Past|Full|/14927-fear-itself-sins-past.html|100556|
            4|Invincible Iron Man (2008)|503|/6976-invincible-iron-man-2008.html|39785|
            5|Fear Itself|1|/14919-fear-itself.html|100532|
            6|Journey into Mystery (2011)|622|/19307-journey-into-mystery-2011.html|132228|
            7|Journey into Mystery (2011)|626.1|/19307-journey-into-mystery-2011.html|132233|
            8|Fear Itself: The Home Front|1|/14931-fear-itself-the-home-front.html|100577|
            9|Fear Itself|2|/14919-fear-itself.html|100533|
            10|Thunderbolts (1997-2012)|158|/29797-thunderbolts-1997-2012.html|209065|
            11|Thunderbolts (1997-2012)|159|/29797-thunderbolts-1997-2012.html|209066|
            12|Invincible Iron Man (2008)|504|/6976-invincible-iron-man-2008.html|39786|
            13|Invincible Iron Man (2008)|505|/6976-invincible-iron-man-2008.html|39787|
            14|Alpha Flight (2011)|1|/7743-alpha-flight-2011.html|45985|
            15|Thunderbolts (1997-2012)|160|/29797-thunderbolts-1997-2012.html|209067|
            16|Thunderbolts (1997-2012)|161|/29797-thunderbolts-1997-2012.html|209068|
            17|Thunderbolts (1997-2012)|162|/29797-thunderbolts-1997-2012.html|209069|
            18|Fear Itself: The Home Front|2|/14931-fear-itself-the-home-front.html|100578|
            19|Fear Itself: The Home Front|3|/14931-fear-itself-the-home-front.html|100579|
            20|Fear Itself: The Home Front|4|/14931-fear-itself-the-home-front.html|100580|
            21|Fear Itself: The Deep|1|/14929-fear-itself-the-deep.html|100560|
            22|Fear Itself: The Deep|2|/14929-fear-itself-the-deep.html|100561|
            23|Fear Itself: The Deep|3|/14929-fear-itself-the-deep.html|100562|
            24|Journey into Mystery (2011)|623|/19307-journey-into-mystery-2011.html|132229|
            25|Fear Itself: Spider-Man|1|/14928-fear-itself-spider-man.html|100557|
            26|Fear Itself: Spider-Man|2|/14928-fear-itself-spider-man.html|100558|
            27|Fear Itself: Spider-Man|3|/14928-fear-itself-spider-man.html|100559|
            28|Avengers (2010)|13|/8576-avengers-2010.html|233884|
            29|Fear Itself: Youth In Revolt|1|/14937-fear-itself-youth-in-revolt.html|100601|
            30|Fear Itself: Fearsome Four|1|/14923-fear-itself-fearsome-four.html|100547|
            31|Fear Itself: Fearsome Four|2|/14923-fear-itself-fearsome-four.html|100548|
            32|Fear Itself: Fearsome Four|3|/14923-fear-itself-fearsome-four.html|100549|
            33|Fear Itself: Fearsome Four|4|/14923-fear-itself-fearsome-four.html|100550|
            34|Secret Avengers (2010-2013)|13|/24899-secret-avengers-2010-2013.html|171524|
            35|Iron Man 2.0|5|/17355-iron-man-20.html|117314|
            36|Iron Man 2.0|6|/17355-iron-man-20.html|117315|
            37|Iron Man 2.0|7|/17355-iron-man-20.html|117316|
            38|Fear Itself: The Monkey King|Full|/14932-fear-itself-the-monkey-king.html|100584|
            39|Fear Itself|3|/14919-fear-itself.html|100534|
            40|Fear Itself: The Worthy|1|/14933-fear-itself-the-worthy.html|100585|
            41|Fear Itself: Uncanny X-Force|1|/14934-fear-itself-uncanny-x-force.html|100593|
            42|Fear Itself: Uncanny X-Force|2|/14934-fear-itself-uncanny-x-force.html|100594|
            43|Fear Itself: Uncanny X-Force|3|/14934-fear-itself-uncanny-x-force.html|100595|
            44|Avengers Academy|15|/8584-avengers-academy.html|55307|
            45|Avengers Academy|16|/8584-avengers-academy.html|53910|
            46|Avengers Academy|17|/8584-avengers-academy.html|53911|
            47|Avengers Academy|18|/8584-avengers-academy.html|53912|
            48|Avengers Academy|19|/8584-avengers-academy.html|55311|
            49|Fear Itself: Deadpool|1|/14922-fear-itself-deadpool.html|100544|
            50|Fear Itself: Deadpool|2|/14922-fear-itself-deadpool.html|100545|
            51|Fear Itself: Deadpool|3|/14922-fear-itself-deadpool.html|100546|
            52|Fear Itself: Youth In Revolt|2|/14937-fear-itself-youth-in-revolt.html|100602|
            53|Fear Itself: Youth In Revolt|3|/14937-fear-itself-youth-in-revolt.html|100603|
            54|Fear Itself: FF|Full|/14925-fear-itself-ff.html|100552|
            55|Fear Itself: Wolverine|1|/14935-fear-itself-wolverine.html|100596|
            56|Fear Itself: Wolverine|2|/14935-fear-itself-wolverine.html|100597|
            57|Avengers (2010)|14|/8576-avengers-2010.html|233885|
            58|Avengers (2010)|15|/8576-avengers-2010.html|233886|
            59|Avengers (2010)|16|/8576-avengers-2010.html|233887|
            60|Hulk (2008)|37|/18354-hulk-2008.html|126972|
            61|Hulk (2008)|38|/18354-hulk-2008.html|126973|
            62|Fear Itself: Wolverine|3|/14935-fear-itself-wolverine.html|100598|
            63|Black Panther: The Man Without Fear|521|/10134-black-panther-the-man-without-fear.html|66432|
            64|Black Panther: The Man Without Fear|522|/10134-black-panther-the-man-without-fear.html|66433|
            65|Black Panther: The Man Without Fear|523|/10134-black-panther-the-man-without-fear.html|66434|
            66|Alpha Flight (2011)|2|/7743-alpha-flight-2011.html|45986|
            67|Alpha Flight (2011)|3|/7743-alpha-flight-2011.html|45987|
            68|Alpha Flight (2011)|4|/7743-alpha-flight-2011.html|45988|
            69|Heroes For Hire (2011)|9|/17186-heroes-for-hire-2011.html|115870|
            70|Heroes For Hire (2011)|10|/17186-heroes-for-hire-2011.html|115871|
            71|Heroes For Hire (2011)|11|/17186-heroes-for-hire-2011.html|115872|
            72|Fear Itself: Black Widow|Full|/14920-fear-itself-black-widow.html|100542|
            73|Journey into Mystery (2011)|624|/19307-journey-into-mystery-2011.html|132230|
            74|Journey into Mystery (2011)|625|/19307-journey-into-mystery-2011.html|132231|
            75|Fear Itself|4|/14919-fear-itself.html|100535|
            76|Fear Itself|5|/14919-fear-itself.html|100536|
            77|Herc|3|/17130-herc.html|115411|
            78|Herc|4|/17130-herc.html|115412|
            79|Herc|5|/17130-herc.html|115413|
            80|Herc|6|/17130-herc.html|115414|
            81|Ghost Rider (2011)|0.1|/15799-ghost-rider-2011.html|107132|
            82|Ghost Rider (2011)|1|/15799-ghost-rider-2011.html|107133|
            83|Ghost Rider (2011)|2|/15799-ghost-rider-2011.html|107134|
            84|Ghost Rider (2011)|3|/15799-ghost-rider-2011.html|107135|
            85|New Avengers (2010-2012)|14|/22519-new-avengers-2010.html|155064|
            86|New Avengers (2010-2012)|15|/22519-new-avengers-2010.html|155065|
            87|New Avengers (2010-2012)|16|/22519-new-avengers-2010.html|155066|
            88|Secret Avengers (2010-2013)|14|/24899-secret-avengers-2010-2013.html|171525|
            89|Secret Avengers (2010-2013)|15|/24899-secret-avengers-2010-2013.html|171526|
            90|Avengers (2010)|17|/8576-avengers-2010.html|233888|
            91|Fear Itself: Youth In Revolt|4|/14937-fear-itself-youth-in-revolt.html|100604|
            92|Fear Itself: Youth In Revolt|5|/14937-fear-itself-youth-in-revolt.html|100605|
            93|Invincible Iron Man (2008)|506|/6976-invincible-iron-man-2008.html|39788|
            94|Invincible Iron Man (2008)|507|/6976-invincible-iron-man-2008.html|39789|
            95|Invincible Iron Man (2008)|508|/6976-invincible-iron-man-2008.html|39790|
            96|Uncanny X-Men (1963)|540|/6967-uncanny-x-men-1963.html|39557|
            97|Uncanny X-Men (1963)|541|/6967-uncanny-x-men-1963.html|39558|
            98|Fear Itself: The Home Front|5|/14931-fear-itself-the-home-front.html|100581|
            99|Fear Itself: The Home Front|6|/14931-fear-itself-the-home-front.html|100582|
            100|Ghost Rider (2011)|4|/15799-ghost-rider-2011.html|107136|
            101|New Mutants (2009)|29|/22552-new-mutants-2009.html|155335|
            102|Journey into Mystery (2011)|626|/19307-journey-into-mystery-2011.html|132232|
            103|New Mutants (2009)|30|/22552-new-mutants-2009.html|155336|
            104|New Mutants (2009)|31|/22552-new-mutants-2009.html|155337|
            105|New Mutants (2009)|32|/22552-new-mutants-2009.html|155338|
            106|Uncanny X-Men (1963)|542|/6967-uncanny-x-men-1963.html|39559|
            107|Uncanny X-Men (1963)|543|/6967-uncanny-x-men-1963.html|39560|
            108|Journey into Mystery (2011)|627|/19307-journey-into-mystery-2011.html|132234|
            109|Fear Itself: The Deep|4|/14929-fear-itself-the-deep.html|100563|
            110|Fear Itself: Hulk vs. Dracula|1|/14926-fear-itself-hulk-vs-dracula.html|100553|
            111|Fear Itself: Hulk vs. Dracula|2|/14926-fear-itself-hulk-vs-dracula.html|100554|
            112|Fear Itself: Hulk vs. Dracula|3|/14926-fear-itself-hulk-vs-dracula.html|100555|
            113|Fear Itself|6|/14919-fear-itself.html|100537|
            114|Fear Itself: The Home Front|7|/14931-fear-itself-the-home-front.html|100583|
            115|Invincible Iron Man (2008)|509|/6976-invincible-iron-man-2008.html|39791|
            116|Fear Itself|7|/14919-fear-itself.html|100538|
            117|Journey into Mystery (2011)|628|/19307-journey-into-mystery-2011.html|132235|
            118|Journey into Mystery (2011)|629|/19307-journey-into-mystery-2011.html|132236|
            119|Journey into Mystery (2011)|630|/19307-journey-into-mystery-2011.html|132237|
            120|Avengers Academy|20|/8584-avengers-academy.html|55312|
            121|Fear Itself: Youth In Revolt|6|/14937-fear-itself-youth-in-revolt.html|100606|
            122|Fear Itself|7.1|/14919-fear-itself.html|100539|
            123|Fear Itself|7.2|/14919-fear-itself.html|100540|
            124|Fear Itself|7.3|/14919-fear-itself.html|100541|
        """),
        InkShelf2SavedEventOrders.Event("flashpoint", "Flashpoint", "DC", 66, setOf(1, 2, 3, 4, 5, 25, 34, 41, 58, 66), """
            1|The Flash (2010)|8|/8834-the-flash-2010.html|57357|
            2|The Flash (2010)|9|/8834-the-flash-2010.html|57358|
            3|The Flash (2010)|10|/8834-the-flash-2010.html|57359|
            4|The Flash (2010)|11|/8834-the-flash-2010.html|57360|
            5|The Flash (2010)|12|/8834-the-flash-2010.html|57361|
            6|Flashpoint: Reverse-Flash|Full|/8806-flashpoint-reverse-flash.html|236285|
            7|Flashpoint: Project Superman|1|/8805-flashpoint-project-superman.html|56711|
            8|Flashpoint: Project Superman|2|/8805-flashpoint-project-superman.html|56712|
            9|Flashpoint: Wonder Woman and the Furies|1|/8817-flashpoint-wonder-woman-and-the-furies.html|56733|
            10|Flashpoint: Green Arrow Industries|Full|/8800-flashpoint-green-arrow-industries.html|56700|
            11|Flashpoint: Wonder Woman and the Furies|2|/8817-flashpoint-wonder-woman-and-the-furies.html|56734|
            12|Flashpoint: Lois Lane and the Resistance|1|/8804-flashpoint-lois-lane-and-the-resistance.html|56708|
            13|Flashpoint: The Canterbury Cricket|Full|/8808-flashpoint-the-canterbury-cricket.html|56718|
            14|Flashpoint: Deadman and the Flying Graysons|1|/8796-flashpoint-deadman-and-the-flying-graysons.html|56691|
            15|Flashpoint: Deadman and the Flying Graysons|2|/8796-flashpoint-deadman-and-the-flying-graysons.html|56692|
            16|Flashpoint: Deadman and the Flying Graysons|3|/8796-flashpoint-deadman-and-the-flying-graysons.html|56693|
            17|Flashpoint: Grodd of War|Full|/8801-flashpoint-grodd-of-war.html|240800|
            18|Flashpoint: The Legion of Doom|1|/8809-flashpoint-the-legion-of-doom.html|56719|
            19|Flashpoint: The Legion of Doom|2|/8809-flashpoint-the-legion-of-doom.html|56720|
            20|Flashpoint: The Legion of Doom|3|/8809-flashpoint-the-legion-of-doom.html|56721|
            21|Flashpoint: Abin Sur - The Green Lantern|1|/8792-flashpoint-abin-sur-the-green-lantern.html|56681|
            22|Flashpoint: Hal Jordan|1|/8802-flashpoint-hal-jordan.html|56702|
            23|Flashpoint: Abin Sur - The Green Lantern|2|/8792-flashpoint-abin-sur-the-green-lantern.html|56682|
            24|Flashpoint: Hal Jordan|2|/8802-flashpoint-hal-jordan.html|56703|
            25|Flashpoint (2011)|1|/8790-flashpoint-2011.html|56674|
            26|Flashpoint: The Outsider|1|/8810-flashpoint-the-outsider.html|56722|
            27|Flashpoint: Secret Seven|1|/8807-flashpoint-secret-seven.html|56715|
            28|Flashpoint: Citizen Cold|1|/8795-flashpoint-citizen-cold.html|56688|
            29|Flashpoint: Secret Seven|2|/8807-flashpoint-secret-seven.html|56716|
            30|Flashpoint: Secret Seven|3|/8807-flashpoint-secret-seven.html|56717|
            31|Flashpoint: Citizen Cold|2|/8795-flashpoint-citizen-cold.html|56689|
            32|Flashpoint: Citizen Cold|3|/8795-flashpoint-citizen-cold.html|56690|
            33|Flashpoint: Deathstroke and the Curse of the Ravager|1|/8797-flashpoint-deathstroke-and-the-curse-of-the-ravager.html|56694|
            34|Flashpoint (2011)|2|/8790-flashpoint-2011.html|56675|
            35|Flashpoint: Deathstroke and the Curse of the Ravager|2|/8797-flashpoint-deathstroke-and-the-curse-of-the-ravager.html|56695|
            36|Flashpoint: The Outsider|2|/8810-flashpoint-the-outsider.html|56723|
            37|Flashpoint: Batman Knight of Vengeance (2011)|1|/8793-flashpoint-batman-knight-of-vengeance-2011.html|56684|
            38|Flashpoint: Batman Knight of Vengeance (2011)|2|/8793-flashpoint-batman-knight-of-vengeance-2011.html|56685|
            39|Flashpoint: Batman Knight of Vengeance (2011)|3|/8793-flashpoint-batman-knight-of-vengeance-2011.html|56686|
            40|Flashpoint: Deathstroke and the Curse of the Ravager|3|/8797-flashpoint-deathstroke-and-the-curse-of-the-ravager.html|56696|
            41|Flashpoint (2011)|3|/8790-flashpoint-2011.html|56676|
            42|Flashpoint: Lois Lane and the Resistance|2|/8804-flashpoint-lois-lane-and-the-resistance.html|56709|
            43|Booster Gold (2007)|44|/10624-booster-gold-2007.html|70025|
            44|Booster Gold (2007)|45|/10624-booster-gold-2007.html|70026|
            45|Booster Gold (2007)|46|/10624-booster-gold-2007.html|70027|
            46|Flashpoint: Frankenstein & The Creatures of the Unknown|1|/8799-flashpoint-frankenstein--the-creatures-of-the-unknown.html|61235|
            47|Flashpoint: Frankenstein & The Creatures of the Unknown|2|/8799-flashpoint-frankenstein--the-creatures-of-the-unknown.html|61236|
            48|Flashpoint: Frankenstein & The Creatures of the Unknown|3|/8799-flashpoint-frankenstein--the-creatures-of-the-unknown.html|61237|
            49|Flashpoint: The Outsider|3|/8810-flashpoint-the-outsider.html|56724|
            50|Flashpoint: The World of Flashpoint|1|/8811-flashpoint-the-world-of-flashpoint.html|56725|
            51|Flashpoint: The World of Flashpoint|2|/8811-flashpoint-the-world-of-flashpoint.html|56726|
            52|Flashpoint: Hal Jordan|3|/8802-flashpoint-hal-jordan.html|56704|
            53|Flashpoint: Emperor Aquaman|1|/8798-flashpoint-emperor-aquaman.html|56697|
            54|Flashpoint: Emperor Aquaman|2|/8798-flashpoint-emperor-aquaman.html|56698|
            55|Flashpoint: Wonder Woman and the Furies|3|/8817-flashpoint-wonder-woman-and-the-furies.html|56735|
            56|Flashpoint: Emperor Aquaman|3|/8798-flashpoint-emperor-aquaman.html|56699|
            57|Flashpoint: Abin Sur - The Green Lantern|3|/8792-flashpoint-abin-sur-the-green-lantern.html|56683|
            58|Flashpoint (2011)|4|/8790-flashpoint-2011.html|56677|
            59|Booster Gold (2007)|47|/10624-booster-gold-2007.html|70028|
            60|Flashpoint: The World of Flashpoint|3|/8811-flashpoint-the-world-of-flashpoint.html|56727|
            61|Flashpoint: Lois Lane and the Resistance|3|/8804-flashpoint-lois-lane-and-the-resistance.html|56710|
            62|Flashpoint: Project Superman|3|/8805-flashpoint-project-superman.html|56713|
            63|Flashpoint: Kid Flash Lost|1|/8803-flashpoint-kid-flash-lost.html|56705|
            64|Flashpoint: Kid Flash Lost|2|/8803-flashpoint-kid-flash-lost.html|56706|
            65|Flashpoint: Kid Flash Lost|3|/8803-flashpoint-kid-flash-lost.html|56707|
            66|Flashpoint (2011)|5|/8790-flashpoint-2011.html|56678|
        """),
        InkShelf2SavedEventOrders.Event("house-of-m", "House of M", "Marvel", 64, setOf(3, 4, 43, 51, 52, 60, 61, 63), """
            1|Excalibur (2004)|13|/14576-excalibur-2004.html|97539|
            2|Excalibur (2004)|14|/14576-excalibur-2004.html|97540|
            3|House of M (2005)|1|/18298-house-of-m-2005.html|126197|
            4|House of M (2005)|2|/18298-house-of-m-2005.html|126198|
            5|Civil War: House Of M|1|/11678-civil-war-house-of-m.html|77467|
            6|Civil War: House Of M|2|/11678-civil-war-house-of-m.html|77468|
            7|Civil War: House Of M|3|/11678-civil-war-house-of-m.html|77469|
            8|Civil War: House Of M|4|/11678-civil-war-house-of-m.html|77470|
            9|Civil War: House Of M|5|/11678-civil-war-house-of-m.html|77471|
            10|House of M: Masters of Evil|1|/18303-house-of-m-masters-of-evil.html|126229|
            11|House of M: Masters of Evil|2|/18303-house-of-m-masters-of-evil.html|126230|
            12|House of M: Masters of Evil|3|/18303-house-of-m-masters-of-evil.html|126231|
            13|House of M: Masters of Evil|4|/18303-house-of-m-masters-of-evil.html|126232|
            14|House of M: Avengers|1|/18302-house-of-m-avengers.html|126224|
            15|House of M: Avengers|2|/18302-house-of-m-avengers.html|126225|
            16|House of M: Avengers|3|/18302-house-of-m-avengers.html|126226|
            17|House of M: Avengers|4|/18302-house-of-m-avengers.html|126227|
            18|House of M: Avengers|5|/18302-house-of-m-avengers.html|126228|
            19|The Incredible Hulk (2000-2008)|83|/28273-the-incredible-hulk-2000-2008.html|197026|
            20|The Pulse (2004-2006)|Full|/28952-the-pulse-2004-2006.html|201684|The Pulse: House of M Special
            21|Fantastic Four: House of M|1|/14819-fantastic-four-house-of-m.html|99967|
            22|Fantastic Four: House of M|2|/14819-fantastic-four-house-of-m.html|99968|
            23|Fantastic Four: House of M|3|/14819-fantastic-four-house-of-m.html|99969|
            24|Spider-Man: House of M (2005)|1|/25992-spider-man-house-of-m-2005.html|178936|
            25|Spider-Man: House of M (2005)|2|/25992-spider-man-house-of-m-2005.html|178937|
            26|Spider-Man: House of M (2005)|3|/25992-spider-man-house-of-m-2005.html|178938|
            27|Spider-Man: House of M (2005)|4|/25992-spider-man-house-of-m-2005.html|178939|
            28|Spider-Man: House of M (2005)|5|/25992-spider-man-house-of-m-2005.html|178940|
            29|The Incredible Hulk (2000-2008)|84|/28273-the-incredible-hulk-2000-2008.html|197027|
            30|The Incredible Hulk (2000-2008)|85|/28273-the-incredible-hulk-2000-2008.html|197028|
            31|The Incredible Hulk (2000-2008)|86|/28273-the-incredible-hulk-2000-2008.html|197029|
            32|Iron Man: House of M|1|/17397-iron-man-house-of-m.html|117495|
            33|Iron Man: House of M|2|/17397-iron-man-house-of-m.html|117496|
            34|Iron Man: House of M|3|/17397-iron-man-house-of-m.html|117497|
            35|Mutopia X|1|/22231-mutopia-x.html|153016|
            36|Mutopia X|2|/22231-mutopia-x.html|153017|
            37|Mutopia X|3|/22231-mutopia-x.html|153018|
            38|New Thunderbolts|11|/22573-new-thunderbolts.html|155458|
            39|Uncanny X-Men (1963)|462|/6967-uncanny-x-men-1963.html|39478|
            40|Uncanny X-Men (1963)|463|/6967-uncanny-x-men-1963.html|39479|
            41|Uncanny X-Men (1963)|464|/6967-uncanny-x-men-1963.html|39480|
            42|Uncanny X-Men (1963)|465|/6967-uncanny-x-men-1963.html|39481|
            43|House of M (2005)|3|/18298-house-of-m-2005.html|126199|
            44|New X-Men (2004)|16|/22582-new-x-men-2004.html|155610|
            45|New X-Men (2004)|17|/22582-new-x-men-2004.html|155611|
            46|New X-Men (2004)|18|/22582-new-x-men-2004.html|155612|
            47|New X-Men (2004)|19|/22582-new-x-men-2004.html|155613|
            48|Wolverine (2003-2009)|33|/31569-wolverine-2003-2009.html|223494|
            49|Wolverine (2003-2009)|34|/31569-wolverine-2003-2009.html|223495|
            50|Wolverine (2003-2009)|35|/31569-wolverine-2003-2009.html|223496|
            51|House of M (2005)|4|/18298-house-of-m-2005.html|126200|
            52|House of M (2005)|5|/18298-house-of-m-2005.html|126201|
            53|The Pulse (2004-2006)|10|/28952-the-pulse-2004-2006.html|201694|
            54|Captain America (2004)|10|/11044-captain-america-2005.html|73179|
            55|Black Panther (2005)|7|/10114-black-panther-2005.html|66287|
            56|Exiles (2001)|69|/14609-exiles-2001.html|97841|
            57|Exiles (2001)|70|/14609-exiles-2001.html|97842|
            58|Exiles (2001)|71|/14609-exiles-2001.html|97843|
            59|Cable and Deadpool|17|/10951-cable-and-deadpool.html|72141|
            60|House of M (2005)|6|/18298-house-of-m-2005.html|126202|
            61|House of M (2005)|7|/18298-house-of-m-2005.html|126203|
            62|Mutopia X|4|/22231-mutopia-x.html|153019|
            63|House of M (2005)|8|/18298-house-of-m-2005.html|126204|
            64|Giant-Size Ms. Marvel|Full|/15922-giant-size-ms-marvel.html|107921|
        """),
        InkShelf2SavedEventOrders.Event("night-of-the-owls", "Night of the Owls", "DC", 15, setOf(), """
            1|Batman (2011-2016)|7|/13578-batman-2011.html|90095|
            2|Batman (2011-2016)|8|/13578-batman-2011.html|90096|
            3|Red Hood And The Outlaws (2011-2015)|8|/24024-red-hood-and-the-outlaws-2011-2015.html|165823|
            4|Batwing|9|/9657-batwing.html|63045|
            5|Batman and Robin (2011)|9|/8870-batman-and-robin-2011.html|57601|
            6|Batgirl (2011)|9|/9565-batgirl-2011.html|62268|
            7|Batman (2011-2016)|9|/13578-batman-2011.html|90097|
            8|Red Hood And The Outlaws (2011-2015)|9|/24024-red-hood-and-the-outlaws-2011-2015.html|165824|
            9|Nightwing (2011-2014)|8|/22738-nightwing-2011-2014.html|156494|
            10|Nightwing (2011-2014)|9|/22738-nightwing-2011-2014.html|156495|
            11|Detective Comics (2011)|9|/13397-detective-comics-2011.html|89067|
            12|Batman: The Dark Knight [II] (2011)|9|/9204-batman-the-dark-knight-ii-2011.html|59567|
            13|Birds of Prey (2011)|9|/9987-birds-of-prey-2011.html|65561|
            14|Batman (2011-2016)|1|/13578-batman-2011.html|90084|Annual 1
            15|Catwoman (2011)|9|/11328-catwoman-2011.html|234356|
        """),
        InkShelf2SavedEventOrders.Event("original-sin", "Original Sin", "Marvel", 67, setOf(1, 2, 4, 6, 35, 42, 43, 44, 46, 47, 48), """
            1|Original Sin (2014)|0|/23039-original-sin-2014.html|158455|
            2|Original Sin (2014)|1|/23039-original-sin-2014.html|158456|
            3|Mighty Avengers|10|/21723-mighty-avengers.html|149722|
            4|Original Sin (2014)|2|/23039-original-sin-2014.html|158457|
            5|Nova (2013-2015)|18|/22863-nova-2013-2015.html|157447|
            6|Original Sin (2014)|3.1|/23039-original-sin-2014.html|158459|
            7|Original Sin (2014)|3.2|/23039-original-sin-2014.html|158460|
            8|Original Sin (2014)|3.3|/23039-original-sin-2014.html|158461|
            9|Original Sin (2014)|3.4|/23039-original-sin-2014.html|158462|
            10|Fantastic Four (2014)|6|/14776-fantastic-four-2014.html|99695|
            11|Fantastic Four (2014)|7|/14776-fantastic-four-2014.html|99696|
            12|Fantastic Four (2014)|8|/14776-fantastic-four-2014.html|99697|
            13|Original Sin: Secret Avengers (Infinite Comic) (2014)|1|/23040-original-sin-secret-avengers-infinite-comic-2014.html|158473|
            14|Original Sin: Secret Avengers (Infinite Comic) (2014)|2|/23040-original-sin-secret-avengers-infinite-comic-2014.html|158474|
            15|Original Sin (2014)|5.1|/23039-original-sin-2014.html|158465|
            16|Original Sin (2014)|5.2|/23039-original-sin-2014.html|158466|
            17|Original Sin (2014)|5.3|/23039-original-sin-2014.html|158467|
            18|Original Sin (2014)|5.4|/23039-original-sin-2014.html|158468|
            19|Original Sin (2014)|5.5|/23039-original-sin-2014.html|158469|
            20|The Amazing Spider-Man (2014-2015)|4|/13581-the-amazing-spider-man-2014.html|90161|
            21|The Amazing Spider-Man (2014-2015)|5|/13581-the-amazing-spider-man-2014.html|90162|
            22|The Amazing Spider-Man (2014-2015)|6|/13581-the-amazing-spider-man-2014.html|90163|
            23|Mighty Avengers|11|/21723-mighty-avengers.html|149723|
            24|Mighty Avengers|12|/21723-mighty-avengers.html|149724|
            25|Mighty Avengers|13|/21723-mighty-avengers.html|149725|
            26|Mighty Avengers|14|/21723-mighty-avengers.html|149726|
            27|Daredevil (2014)|6|/12559-daredevil-2014.html|83485|
            28|Daredevil (2014)|7|/12559-daredevil-2014.html|83486|
            29|Deadpool (2013)|29|/13114-deadpool-2013.html|86495|
            30|Deadpool (2013)|30|/13114-deadpool-2013.html|86496|
            31|Deadpool (2013)|31|/13114-deadpool-2013.html|86497|
            32|Deadpool (2013)|32|/13114-deadpool-2013.html|86498|
            33|Deadpool (2013)|33|/13114-deadpool-2013.html|86499|
            34|Deadpool (2013)|34|/13114-deadpool-2013.html|86500|
            35|Original Sin (2014)|3|/23039-original-sin-2014.html|158458|
            36|Original Sins (2014)|1|/23041-original-sins-2014.html|158475|
            37|Original Sins (2014)|2|/23041-original-sins-2014.html|158476|
            38|Original Sins (2014)|3|/23041-original-sins-2014.html|158477|
            39|Original Sins (2014)|4|/23041-original-sins-2014.html|158478|
            40|Nova (2013-2015)|19|/22863-nova-2013-2015.html|157448|
            41|Nova (2013-2015)|20|/22863-nova-2013-2015.html|157450|
            42|Original Sin (2014)|4|/23039-original-sin-2014.html|158463|
            43|Original Sin (2014)|5|/23039-original-sin-2014.html|158464|
            44|Original Sin (2014)|6|/23039-original-sin-2014.html|158470|
            45|Original Sins (2014)|5|/23041-original-sins-2014.html|158479|
            46|Original Sin (2014)|7|/23039-original-sin-2014.html|158471|
            47|Original Sin (2014)|8|/23039-original-sin-2014.html|158472|
            48|Original Sin (2014)|1|/23039-original-sin-2014.html|158446|Annual 1
            49|All-New Invaders|6|/7707-all-new-invaders.html|45311|
            50|All-New Invaders|7|/7707-all-new-invaders.html|45312|
            51|Avengers (2013)|29|/8577-avengers-2013.html|243195|
            52|Avengers (2013)|30|/8577-avengers-2013.html|243196|
            53|Avengers (2013)|31|/8577-avengers-2013.html|243197|
            54|Avengers (2013)|32|/8577-avengers-2013.html|243198|
            55|Avengers (2013)|33|/8577-avengers-2013.html|243199|
            56|Avengers (2013)|34|/8577-avengers-2013.html|243200|
            57|Guardians of the Galaxy (2013)|18|/16521-guardians-of-the-galaxy-2013.html|111719|
            58|Guardians of the Galaxy (2013)|19|/16521-guardians-of-the-galaxy-2013.html|111720|
            59|Guardians of the Galaxy (2013)|20|/16521-guardians-of-the-galaxy-2013.html|111721|
            60|Uncanny X-Men (2013-2015)|23|/30459-uncanny-x-men-2013-2015.html|239281|
            61|Uncanny X-Men (2013-2015)|24|/30459-uncanny-x-men-2013-2015.html|239282|
            62|Uncanny X-Men (2013-2015)|25|/30459-uncanny-x-men-2013-2015.html|239283|
            63|Operation S.I.N.|1|||
            64|Operation S.I.N.|2|||
            65|Operation S.I.N.|3|||
            66|Operation S.I.N.|4|||
            67|Operation S.I.N.|5|||
        """),
        InkShelf2SavedEventOrders.Event("spider-verse", "Spider-Verse", "Marvel", 32, setOf(), """
            1|Superior Spider-Man (2013-2014)|32|/26635-superior-spider-man-2013-2014.html|184576|
            2|Superior Spider-Man (2013-2014)|33|/26635-superior-spider-man-2013-2014.html|184577|
            3|The Amazing Spider-Man (2014-2015)|7|/13581-the-amazing-spider-man-2014.html|90164|
            4|The Amazing Spider-Man (2014-2015)|8|/13581-the-amazing-spider-man-2014.html|90165|
            5|Edge of Spider-Verse (2014)|1|/14196-edge-of-spider-verse-2014.html|95259|
            6|Edge of Spider-Verse (2014)|2|/14196-edge-of-spider-verse-2014.html|95260|
            7|Edge of Spider-Verse (2014)|3|/14196-edge-of-spider-verse-2014.html|95261|
            8|Edge of Spider-Verse (2014)|4|/14196-edge-of-spider-verse-2014.html|95262|
            9|Edge of Spider-Verse (2014)|5|/14196-edge-of-spider-verse-2014.html|95263|
            10|Spider-Man 2099 (2014-2015)|5|/25887-spider-man-2099-2014-2015.html|178540|
            11|Spider-Verse Team-Up (2015)|1|/26053-spider-verse-team-up-2015.html|179187|
            12|Spider-Verse (2015)|1|/26051-spider-verse-2015.html|179180|
            13|Spider-Verse (2015)|2|/26051-spider-verse-2015.html|179181|
            14|The Amazing Spider-Man (2014-2015)|9|/13581-the-amazing-spider-man-2014.html|90166|
            15|The Amazing Spider-Man (2014-2015)|10|/13581-the-amazing-spider-man-2014.html|90167|
            16|Spider-Man 2099 (2014-2015)|6|/25887-spider-man-2099-2014-2015.html|178541|
            17|Scarlet Spiders (2015)|1|/24794-scarlet-spiders-2015.html|170747|
            18|Scarlet Spiders (2015)|2|/24794-scarlet-spiders-2015.html|170748|
            19|The Amazing Spider-Man (2014-2015)|11|/13581-the-amazing-spider-man-2014.html|90168|
            20|Spider-Verse Team-Up (2015)|2|/26053-spider-verse-team-up-2015.html|179188|
            21|Spider-Woman (2015-)|1|/4981-spider-woman.html|26113|
            22|Spider-Woman (2015-)|2|/4981-spider-woman.html|26112|
            23|The Amazing Spider-Man (2014-2015)|12|/13581-the-amazing-spider-man-2014.html|90169|
            24|Spider-Man 2099 (2014-2015)|7|/25887-spider-man-2099-2014-2015.html|178542|
            25|Scarlet Spiders (2015)|3|/24794-scarlet-spiders-2015.html|170749|
            26|Spider-Woman (2015-)|3|/4981-spider-woman.html|26111|
            27|The Amazing Spider-Man (2014-2015)|13|/13581-the-amazing-spider-man-2014.html|90170|
            28|Spider-Verse Team-Up (2015)|3|/26053-spider-verse-team-up-2015.html|179189|
            29|Spider-Man 2099 (2014-2015)|8|/25887-spider-man-2099-2014-2015.html|178543|
            30|The Amazing Spider-Man (2014-2015)|14|/13581-the-amazing-spider-man-2014.html|90171|
            31|The Amazing Spider-Man (2014-2015)|15|/13581-the-amazing-spider-man-2014.html|90172|
            32|Spider-Woman (2015-)|4|/4981-spider-woman.html|26110|
        """),
        InkShelf2SavedEventOrders.Event("world-war-hulk", "World War Hulk", "Marvel", 52, setOf(1, 2, 3, 20, 28, 33, 36), """
            1|The Incredible Hulk (2000-2008)|106|/28273-the-incredible-hulk-2000-2008.html|197049|
            2|World War Hulk Prologue: World Breaker (2007-)|Full|/31769-world-war-hulk-prologue-world-breaker-2007.html|225128|
            3|World War Hulk (2007-2008)|1|/31767-world-war-hulk-2007-2008.html|225118|
            4|Iron Man (2005)|19|/17352-iron-man-2005.html|117236|
            5|Heroes For Hire (2006)|11|/17185-heroes-for-hire-2006.html|115853|
            6|Heroes For Hire (2006)|12|/17185-heroes-for-hire-2006.html|115854|
            7|Heroes For Hire (2006)|13|/17185-heroes-for-hire-2006.html|115855|
            8|The Irredeemable Ant-Man (2006-2007)|10|/28318-the-irredeemable-ant-man-2006-2007.html|197393|
            9|Ghost Rider (2006)|12|/15798-ghost-rider-2006.html|107108|
            10|Ghost Rider (2006)|13|/15798-ghost-rider-2006.html|107109|
            11|World War Hulk: Front Line (2007)|1|/31771-world-war-hulk-front-line-2007.html|225130|
            12|World War Hulk: Gamma Corps (2007-2008)|1|/31772-world-war-hulk-gamma-corps-2007-2008.html|225136|
            13|World War Hulk: Gamma Corps (2007-2008)|2|/31772-world-war-hulk-gamma-corps-2007-2008.html|225137|
            14|World War Hulk: Gamma Corps (2007-2008)|3|/31772-world-war-hulk-gamma-corps-2007-2008.html|225138|
            15|World War Hulk: Gamma Corps (2007-2008)|4|/31772-world-war-hulk-gamma-corps-2007-2008.html|225139|
            16|Iron Man (2005)|20|/17352-iron-man-2005.html|117237|
            17|World War Hulk: X-Men (2007)|1|/31774-world-war-hulk-x-men-2007.html|225141|
            18|World War Hulk: X-Men (2007)|2|/31774-world-war-hulk-x-men-2007.html|225142|
            19|World War Hulk: X-Men (2007)|3|/31774-world-war-hulk-x-men-2007.html|225143|
            20|World War Hulk (2007-2008)|2|/31767-world-war-hulk-2007-2008.html|225119|
            21|The Incredible Hulk (2000-2008)|107|/28273-the-incredible-hulk-2000-2008.html|197050|
            22|The Incredible Hulk (2000-2008)|108|/28273-the-incredible-hulk-2000-2008.html|197051|
            23|World War Hulk: Front Line (2007)|2|/31771-world-war-hulk-front-line-2007.html|225131|
            24|Punisher War Journal (2007-2009)|12|/23757-punisher-war-journal-2007-2009.html|164144|
            25|Avengers: The Initiative|4|/8689-avengers-the-initiative.html|55893|
            26|Avengers: The Initiative|5|/8689-avengers-the-initiative.html|55894|
            27|World War Hulk: Front Line (2007)|3|/31771-world-war-hulk-front-line-2007.html|225132|
            28|World War Hulk (2007-2008)|3|/31767-world-war-hulk-2007-2008.html|225120|
            29|The Incredible Hulk (2000-2008)|109|/28273-the-incredible-hulk-2000-2008.html|197052|
            30|The Incredible Hulk (2000-2008)|110|/28273-the-incredible-hulk-2000-2008.html|197053|
            31|Heroes For Hire (2006)|14|/17185-heroes-for-hire-2006.html|115856|
            32|Heroes For Hire (2006)|15|/17185-heroes-for-hire-2006.html|115857|
            33|World War Hulk (2007-2008)|4|/31767-world-war-hulk-2007-2008.html|225121|
            34|World War Hulk: Front Line (2007)|4|/31771-world-war-hulk-front-line-2007.html|225133|
            35|World War Hulk: Front Line (2007)|5|/31771-world-war-hulk-front-line-2007.html|225134|
            36|World War Hulk (2007-2008)|5|/31767-world-war-hulk-2007-2008.html|225122|
            37|The Incredible Hulk (2000-2008)|111|/28273-the-incredible-hulk-2000-2008.html|197054|
            38|World War Hulk: Front Line (2007)|6|/31771-world-war-hulk-front-line-2007.html|225135|
            39|What If? Planet Hulk (2007-)|Full|/31291-what-if-planet-hulk-2007.html|221375|
            40|World War Hulk: Aftersmash (2008-)|Full|/31770-world-war-hulk-aftersmash-2008.html|225129|
            41|The Incredible Hulk (2000-2008)|112|/28273-the-incredible-hulk-2000-2008.html|197055|
            42|Incredible Hercules|113|/18641-incredible-hercules.html|128248|
            43|Incredible Hercules|114|/18641-incredible-hercules.html|128249|
            44|Incredible Hercules|115|/18641-incredible-hercules.html|128250|
            45|WWH Aftersmash: Damage Control (2008)|1|/31839-wwh-aftersmash-damage-control-2008.html|225785|
            46|WWH Aftersmash: Damage Control (2008)|2|/31839-wwh-aftersmash-damage-control-2008.html|225786|
            47|WWH Aftersmash: Damage Control (2008)|3|/31839-wwh-aftersmash-damage-control-2008.html|225787|
            48|World War Hulk Aftersmash: Warbound, Vol. 1 (2013-)|1|/31768-world-war-hulk-aftersmash-warbound-vol-1-2013.html|225123|
            49|World War Hulk Aftersmash: Warbound, Vol. 1 (2013-)|2|/31768-world-war-hulk-aftersmash-warbound-vol-1-2013.html|225124|
            50|World War Hulk Aftersmash: Warbound, Vol. 1 (2013-)|3|/31768-world-war-hulk-aftersmash-warbound-vol-1-2013.html|225125|
            51|World War Hulk Aftersmash: Warbound, Vol. 1 (2013-)|4|/31768-world-war-hulk-aftersmash-warbound-vol-1-2013.html|225126|
            52|World War Hulk Aftersmash: Warbound, Vol. 1 (2013-)|5|/31768-world-war-hulk-aftersmash-warbound-vol-1-2013.html|225127|
        """),
        InkShelf2SavedEventOrders.Event("x-men-original-sin", "X-Men: Original Sin", "Marvel", 6, setOf(), """
            1|Wolverine: Origins (2006-2010)|28|/31634-wolverine-origins-2006-2010.html|223848|
            2|X-Men: Original Sin|Full|/17658-x-men-original-sin.html|120133|
            3|X-Men Legacy (2008)|217|/17498-x-men-legacy-2008.html|120759|
            4|Wolverine: Origins (2006-2010)|29|/31634-wolverine-origins-2006-2010.html|223849|
            5|X-Men Legacy (2008)|218|/17498-x-men-legacy-2008.html|120760|
            6|Wolverine: Origins (2006-2010)|30|/31634-wolverine-origins-2006-2010.html|223850|
        """),
    )
}

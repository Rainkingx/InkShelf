package eu.kanade.tachiyomi.ui.inkshelf.shell

import org.junit.Assert.*
import org.junit.Test

class InkShelf2EventImportSessionTest {
    private val slug = "blood-hunt"

    private fun row(
        position: Int,
        issue: Int = position,
    ) = """
        <li><a href="/event/blood-hunt/$position">${position}Blood Hunt (2024) Issue #$issue</a>
        <a href="/12345-blood-hunt.html">↗</a></li>
    """

    private fun first(
        rows: String = row(1),
        next: String = "<a href='/event/blood-hunt/list/2/'>Next</a>",
    ) = """
        <title>Blood Hunt Reading Order</title><h1>Blood Hunt</h1><p>2 issues</p>
        <h2>Full chronology</h2><ol>$rows</ol>$next
        <h2>Start here: the short path</h2><ol><li>Blood Hunt #1</li><li>Blood Hunt #2</li></ol>
    """

    private fun rejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    @Test fun multiPageImportCarriesEssentialAndRetainsSourcePaths() {
        val session = InkShelf2EventImportSession(slug)
        session.add(first(), session.url(1))
        assertEquals(2, session.nextPage)
        session.add("<ol>${row(2)}</ol>", session.url(2))
        val rows = session.validated(2, 2)
        assertEquals(listOf(1, 2), rows.map { it.position })
        assertTrue(rows.all { it.isEssential })
        assertEquals("/12345-blood-hunt.html", rows.last().batcaveSeriesPath)
        assertNull(session.nextPage)
    }

    @Test fun partialGuideCannotBeSaved() {
        val session = InkShelf2EventImportSession(slug)
        session.add(first(), session.url(1))
        rejected { session.validated(2, 2) }
    }

    @Test fun declaredCompleteCountCannotBeOverridden() {
        val session = InkShelf2EventImportSession(slug)
        session.add(first(row(1), ""), session.url(1))
        rejected { session.validated(1, 1) }
    }

    @Test fun mismatchedEssentialCountCannotBeSaved() {
        val session = InkShelf2EventImportSession(slug)
        session.add(first(row(1) + row(2), ""), session.url(1))
        rejected { session.validated(2, 1) }
    }

    @Test fun missingPositionCannotBeSaved() {
        val session = InkShelf2EventImportSession(slug)
        session.add(first(row(1) + row(3, 2), ""), session.url(1))
        rejected { session.validated(2, 2) }
    }

    @Test fun duplicatePositionsRejectedBeforeParserDeduplication() {
        val session = InkShelf2EventImportSession(slug)
        rejected { session.add(first(row(1) + row(1), ""), session.url(1)) }
        assertEquals(0, session.importedCount)
    }

    @Test fun repeatedPagesAreRejectedWithoutLosingPriorMetadata() {
        val session = InkShelf2EventImportSession(slug)
        session.add(first(), session.url(1))
        rejected { session.add("<ol>${row(1)}</ol>", session.url(2)) }
        assertEquals(1, session.importedCount)
        assertEquals(2, session.nextPage)
    }

    @Test fun wrongEventCanonicalRejected() {
        val session = InkShelf2EventImportSession(slug)
        rejected { session.add("<link rel='canonical' href='/event/civil-war-2006/'>" + first(), session.url(1)) }
    }

    @Test fun challengeAndOversizedPagesRejected() {
        val session = InkShelf2EventImportSession(slug)
        rejected { session.add("<title>Just a moment</title>" + first(), session.url(1)) }
        rejected { session.add("x".repeat(InkShelf2EventImportSession.MAX_HTML + 1), session.url(1)) }
    }

    @Test fun noShortPathIsNotSilentlyAnEmptyEssentialGuide() {
        val session = InkShelf2EventImportSession(slug)
        rejected { session.add("<ol>${row(1)}</ol>", session.url(1)) }
    }

    @Test fun unsupportedPaginationRejected() {
        val session = InkShelf2EventImportSession(slug)
        rejected { session.add(first(next = "<a href='/event/blood-hunt/list/31/'>Next</a>"), session.url(1)) }
    }

    @Test fun urlPolicyRejectsReaderLinksCredentialsQueriesAndForeignHosts() {
        listOf(
            "http://batcave.biz/event/blood-hunt/",
            "https://batcave.biz.evil.test/event/blood-hunt/",
            "https://user@batcave.biz/event/blood-hunt/",
            "https://batcave.biz:444/event/blood-hunt/",
            "https://batcave.biz/event/blood-hunt/?token=x",
            "https://batcave.biz/event/blood-hunt/1",
            "file:///event/blood-hunt/",
            "https://batcave.biz/event/civil-war-2006/",
        ).forEach {
            assertNull(it, InkShelf2EventImportSession.pageNumber(it, slug))
        }
        assertEquals(1, InkShelf2EventImportSession.pageNumber("https://batcave.biz/event/blood-hunt/", slug))
    }

    @Test fun cancellationDropsTemporarySessionData() {
        val session = InkShelf2EventImportSession(slug)
        session.add(first(), session.url(1))
        session.clear()
        assertEquals(0, session.importedCount)
        assertEquals(0, session.essentialCount)
        assertNull(session.declaredTotal)
        assertEquals(1, session.nextPage)
    }

    @Test fun htmlAndMhtDecodeWithoutLoadingAttachments() {
        val html = first(row(1) + row(2), "")
        assertEquals(html, InkShelf2SavedEventPage.decode(html.toByteArray()))
        val encoded =
            java.util.Base64
                .getEncoder()
                .encodeToString(html.toByteArray())
        val archive =
            "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"part\"\r\n\r\n" +
                "--part\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n$encoded\r\n" +
                "--part\r\nContent-Type: image/png\r\n\r\nnot-an-image\r\n--part--"
        assertEquals(html, InkShelf2SavedEventPage.decode(archive.toByteArray()))
    }

    @Test fun quotedPrintableMhtDecoded() {
        val archive =
            "MIME-Version: 1.0\nContent-Type: multipart/related; boundary=p\n\n" +
                "--p\nContent-Type: text/html; charset=UTF-8\nContent-Transfer-Encoding: quoted-printable\n\n" +
                "<h1 title=3D\"x\">Blood =\nHunt</h1>\n--p--"
        assertEquals("<h1 title=\"x\">Blood Hunt</h1>", InkShelf2SavedEventPage.decode(archive.toByteArray()))
    }
}

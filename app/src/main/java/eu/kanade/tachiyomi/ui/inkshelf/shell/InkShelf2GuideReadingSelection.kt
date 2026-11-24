package eu.kanade.tachiyomi.ui.inkshelf.shell

/** Pure reading-path selection shared by Metron and BatCave event pages. */
internal object InkShelf2GuideReadingSelection {
    fun issuesForMode(issues: List<InkShelf2GuideIssue>, essential: Boolean): List<InkShelf2GuideIssue> {
        val inOrder = issues.sortedBy { it.position }.distinctBy { it.position }
        if (!essential) return inOrder
        val core = inOrder.filter { it.isEssential }
        // A missing essential path should never lead to an empty, unusable guide.
        return core.ifEmpty { inOrder }
    }

    fun next(
        issues: List<InkShelf2GuideIssue>,
        readIds: Set<String>,
        lastOpened: Int? = null,
        partialIds: Set<Int> = emptySet(),
    ): InkShelf2GuideIssue? {
        val readable = issues.sortedBy { it.position }.filterNot { it.sourceUnavailable }
        val unread = readable.filter { it.id.toString() !in readIds }
        return unread.firstOrNull { it.id == lastOpened && it.id in partialIds }
            ?: unread.firstOrNull { it.id in partialIds }
            ?: unread.firstOrNull()
            ?: readable.firstOrNull()
    }

    /** Follow the active event order, never the current source series order. */
    fun after(issues: List<InkShelf2GuideIssue>, currentId: Int, readIds: Set<String>): InkShelf2GuideIssue? {
        val position = issues.firstOrNull { it.id == currentId }?.position ?: return null
        return issues.sortedBy { it.position }.firstOrNull {
            it.position > position && !it.sourceUnavailable && it.id.toString() !in readIds
        }
    }

    fun finished(issues: List<InkShelf2GuideIssue>, readIds: Set<String>): Boolean {
        val readable = issues.filterNot { it.sourceUnavailable }
        return readable.isNotEmpty() && readable.all { it.id.toString() in readIds }
    }

    fun hasUnread(issues: List<InkShelf2GuideIssue>, readIds: Set<String>): Boolean =
        issues.any { !it.sourceUnavailable && it.id.toString() !in readIds }
}

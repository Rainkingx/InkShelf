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

    fun next(issues: List<InkShelf2GuideIssue>, readIds: Set<String>): InkShelf2GuideIssue? =
        issues.firstOrNull { it.id.toString() !in readIds } ?: issues.firstOrNull()
}

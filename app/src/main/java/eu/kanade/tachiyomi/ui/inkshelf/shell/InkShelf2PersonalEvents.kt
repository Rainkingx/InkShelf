package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** Local event overlays. A position may change; its issue ID must never change. */
internal object InkShelf2PersonalEvents {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("inkshelf2_personal_events_v1", Context.MODE_PRIVATE)
    private fun array(value: String?) = runCatching { JSONArray(value ?: "[]") }.getOrElse { JSONArray() }
    private fun objects(value: JSONArray) = (0 until value.length()).mapNotNull(value::optJSONObject)

    fun custom(context: Context): List<InkShelf2Guide> = objects(array(prefs(context).getString("events", null))).map {
        InkShelf2Guide(it.getInt("id"), it.getString("name"), "CUSTOM", "You", "Personal reading order",
            0.0, "Custom", description = it.optString("description"))
    }
    fun catalogue(context: Context) = InkShelf2BatCaveGuides.verified() + custom(context)

    @Synchronized
    fun create(context: Context, name: String, description: String): InkShelf2Guide {
        require(name.isNotBlank() && name.length <= 100 && description.length <= 1500)
        val p = prefs(context)
        val used = catalogue(context).map { it.id }.toSet()
        var id = p.getInt("next_event", Int.MIN_VALUE)
        while (id in used) id++
        require(id < -2_000_000_001)
        val events = array(p.getString("events", null))
        events.put(JSONObject().put("id", id).put("name", name.trim()).put("description", description.trim()))
        check(p.edit().putString("events", events.toString()).putInt("next_event", id + 1).commit())
        return custom(context).first { it.id == id }
    }

    fun order(context: Context, guide: InkShelf2Guide): List<InkShelf2GuideIssue> {
        val raw = prefs(context).getString("order_" + guide.id, null)
        if (raw == null) return guide.batcaveSlug?.let { InkShelf2BundledEventOrders.get(it, 1)?.issues }.orEmpty()
        return objects(array(raw)).map { o ->
            InkShelf2GuideIssue(o.getInt("id"), o.getInt("position"), o.getString("series"), null,
                o.getString("number"), o.getString("role"), o.optInt("year").takeIf { it > 0 },
                batcaveSeriesPath = o.optString("path").takeIf { it.isNotBlank() },
                batcaveIssuePath = o.optString("issuePath").takeIf { it.isNotBlank() },
                issueLabel = o.optString("label").takeIf { it.isNotBlank() },
                sourceUnavailable = o.optBoolean("unavailable"),
                explicitSource = o.optString("source").toLongOrNull(),
                explicitSeriesUrl = o.optString("seriesUrl").takeIf { it.isNotBlank() },
                explicitChapterUrl = o.optString("chapterUrl").takeIf { it.isNotBlank() })
        }
    }

    private fun saveOrder(context: Context, id: Int, issues: List<InkShelf2GuideIssue>) {
        val data = JSONArray()
        issues.forEachIndexed { index, issue ->
            data.put(JSONObject().put("id", issue.id).put("position", index + 1).put("series", issue.series)
                .put("number", issue.number).put("role", issue.role).put("year", issue.yearBegan ?: 0)
                .put("path", issue.batcaveSeriesPath.orEmpty()).put("issuePath", issue.batcaveIssuePath.orEmpty())
                .put("label", issue.issueLabel.orEmpty()).put("unavailable", issue.sourceUnavailable)
                .put("source", issue.explicitSource?.toString().orEmpty())
                .put("seriesUrl", issue.explicitSeriesUrl.orEmpty()).put("chapterUrl", issue.explicitChapterUrl.orEmpty()))
        }
        check(prefs(context).edit().putString("order_" + id, data.toString()).commit())
    }

    @Synchronized
    fun add(context: Context, guide: InkShelf2Guide, manga: Manga, chapter: Chapter, position: Int, replaceId: Int?, essential: Boolean, replaceIds: Set<Int> = emptySet()) {
        val mangaId = requireNotNull(manga.id)
        val chapterId = requireNotNull(chapter.id)
        require(chapter.manga_id == mangaId && manga.url.isNotBlank() && chapter.url.isNotBlank())
        val order = order(context, guide).toMutableList()
        val original = replaceId?.let { id -> order.firstOrNull { it.id == id && it.sourceUnavailable } ?: error("Missing slot changed") }
        val collected = order.filter { it.id in replaceIds }
        require(collected.size == replaceIds.size && (original == null || collected.isEmpty()))
        require(order.none { it.explicitSource == manga.source && it.explicitSeriesUrl == manga.url && it.explicitChapterUrl == chapter.url })
        val p = prefs(context)
        val next = maxOf(1_000_000, p.getInt("next_issue_" + guide.id, 1_000_000), (order.maxOfOrNull { it.id } ?: 0) + 1)
        val issue = InkShelf2GuideIssue(
            id = original?.id ?: next, position = position, series = manga.title, volume = null,
            number = "", role = if (essential) "Core Issue" else "Optional tie-in",
            yearBegan = InkShelf2GuideAutoMatch.year(manga.title),
            issueLabel = chapter.name, sourceUnavailable = false,
            explicitSource = manga.source, explicitSeriesUrl = manga.url, explicitChapterUrl = chapter.url,
        )
        if (original != null) order[order.indexOf(original)] = issue else {
            val insertion = if (collected.isNotEmpty()) order.indexOf(collected.first()) else position - 1
            require(insertion in 0..order.size)
            if (collected.isNotEmpty()) order.removeAll { it.id in replaceIds }
            order.add(insertion.coerceAtMost(order.size), issue)
            check(p.edit().putInt("next_issue_" + guide.id, next + 1).commit())
        }
        InkShelf2GuidesData.rememberChapter(context, guide.id, issue.id, mangaId, chapterId)
        saveOrder(context, guide.id, order)
    }

    @Synchronized
    fun edit(context: Context, guide: InkShelf2Guide, issueId: Int, move: Int, remove: Boolean = false, toggleRole: Boolean = false) {
        val order = order(context, guide).toMutableList()
        val index = order.indexOfFirst { it.id == issueId }
        if (index < 0) return
        val issue = order[index]
        if (remove) {
            order.removeAt(index)
        } else if (toggleRole) {
            order[index] = issue.copy(role = if (issue.isEssential) "Optional tie-in" else "Core Issue")
        } else {
            val target = (index + move).coerceIn(0, order.lastIndex)
            order.removeAt(index)
            order.add(target, issue)
        }
        saveOrder(context, guide.id, order)
    }

    @Synchronized
    fun removeIssues(context: Context, guide: InkShelf2Guide, ids: Set<Int>) {
        saveOrder(context, guide.id, order(context, guide).filterNot { it.id in ids })
    }

    @Synchronized
    fun restoreOriginal(context: Context, guide: InkShelf2Guide) {
        require(guide.batcaveSlug != null)
        val original = InkShelf2BundledEventOrders.get(guide.batcaveSlug, 1)?.issues.orEmpty()
        val restoredIds = original.map { it.id }.toSet()
        val replacedIds = (order(context, guide).filter { it.id in restoredIds && it.explicitSource != null }.map { it.id } +
            original.filter { it.sourceUnavailable && InkShelf2GuidesData.matchedChapter(context, guide.id, it.id) != null }.map { it.id }).toSet()
        InkShelf2GuidesData.clearReplacementMarks(context, guide.id, replacedIds)
        check(prefs(context).edit().remove("order_" + guide.id).commit())
    }

    fun explicitMatches(issue: InkShelf2GuideIssue, manga: Manga, chapter: Chapter): Boolean =
        issue.explicitSource == manga.source && issue.explicitSeriesUrl == manga.url &&
            issue.explicitChapterUrl == chapter.url && chapter.manga_id == manga.id

    fun cover(context: Context, id: Int): String? {
        val name = prefs(context).getString("cover_" + id, null) ?: return null
        if (!name.matches(Regex("event_-?[0-9]+_[0-9]+[.]img"))) return null
        val file = File(context.filesDir, "event_covers/" + name)
        return file.takeIf { it.isFile && it.length() > 0L }?.let { Uri.fromFile(it).toString() }
    }

    @Synchronized
    fun setCover(context: Context, id: Int, input: InputStream) {
        require(catalogue(context).any { it.id == id })
        val dir = File(context.filesDir, "event_covers").apply { mkdirs() }
        val p = prefs(context)
        val key = "cover_" + id
        val old = p.getString(key, null)
        val name = "event_" + id + "_" + System.nanoTime().let { if (it < 0) -(it + 1) else it } + ".img"
        val target = File(dir, name)
        val file = AtomicFile(target)
        val out = file.startWrite()
        try {
            input.copyTo(out)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        if (!p.edit().putString(key, name).commit()) { target.delete(); error("Could not save event cover") }
        if (old != null && old.matches(Regex("event_-?[0-9]+_[0-9]+[.]img")) && old != name) File(dir, old).delete()
    }
}

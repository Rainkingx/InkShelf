package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One atomic compact record per event. HTML and browser credentials never enter this store. */
internal object InkShelf2EventMetadataStore {
    private const val PREFS = "inkshelf2_event_metadata_v1"
    fun read(context: Context, slug: String): List<InkShelf2GuideIssue>? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(slug, null)
            ?: return null
        require(raw.length <= 2_000_000)
        val record = JSONObject(raw)
        require(record.getInt("version") == 1 && record.getString("slug") == slug)
        val entries = record.getJSONArray("issues")
        require(entries.length() in 1..10000 && entries.length() == record.getInt("complete"))
        val rows = (0 until entries.length()).map { index ->
            val row = entries.getJSONObject(index)
            InkShelf2GuideIssue(
                id = row.getInt("position"), position = row.getInt("position"),
                series = row.getString("series"), volume = null, number = row.getString("number"),
                role = if (row.getBoolean("essential")) "Core Issue" else "Optional tie-in",
                yearBegan = row.optInt("year").takeIf { it > 0 },
                batcaveSeriesPath = row.optString("seriesPath").takeIf { it.isNotBlank() },
                batcaveIssuePath = row.optString("issuePath").takeIf { it.isNotBlank() },
            )
        }
        require(rows.map { it.position } == (1..rows.size).toList())
        require(rows.count { it.isEssential } == record.getInt("essential"))
        rows
    }.getOrNull()

    fun save(context: Context, session: InkShelf2EventImportSession, complete: Int, essential: Int) {
        require(InkShelf2BundledEventOrders.get(session.slug, 1) == null) { "Bundled guides cannot be replaced." }
        val rows = session.validated(complete, essential)
        val record = JSONObject().apply {
            put("version", 1); put("slug", session.slug); put("source", session.url(1))
            put("importedAt", System.currentTimeMillis()); put("complete", complete); put("essential", essential)
            put("issues", JSONArray().apply {
                rows.forEach { row -> put(JSONObject().apply {
                    put("position", row.position); put("series", row.series); put("number", row.number)
                    put("essential", row.isEssential); put("year", row.yearBegan)
                    put("seriesPath", row.batcaveSeriesPath); put("issuePath", row.batcaveIssuePath)
                }) }
            })
        }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(session.slug, record.toString()).commit()) { "Could not save metadata. Please retry." }
    }
}

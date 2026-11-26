package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.manga.InkShelfMetronCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Local release-date cache. Never sends library titles to the API. */
internal object InkShelf2ReleaseRadar {
    data class Issue(val id: Int, val title: String, val year: Int, val number: String, val date: LocalDate, val coverUrl: String? = null)
    data class Calendar(val issues: List<Issue>, val partial: Boolean, val fetched: Long)
    private val lock = Mutex()
    private val yearTag = Regex("""\s*[\(\[]((?:19|20)\d{2})(?:\s*[-–—][^\)\]]*)?[\)\]]""")
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("inkshelf_release_radar_v1", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("release_alerts", false)
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("release_alerts", value).apply()
        InkShelf2ReleaseRadarWorker.schedule(context)
    }
    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun configured(context: Context): Boolean = InkShelfMetronCovers.hasCalendarToken(context)
    private fun normalize(s: String) = s.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
    fun matches(issue: Issue, mangaTitle: String, strict: Boolean = false): Boolean {
        val year = yearTag.find(mangaTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (normalize(mangaTitle.replace(yearTag, " ")) != normalize(issue.title)) return false
        return if (strict) year != null && year == issue.year else year == null || year == issue.year
    }
    private fun parse(body: JSONArray): List<Issue> = buildList {
        for (i in 0 until body.length()) {
            val row = body.optJSONObject(i) ?: continue
            val series = row.optJSONObject("series") ?: continue
            val date = runCatching { LocalDate.parse(row.optString("store_date")) }.getOrNull() ?: continue
            val title = series.optString("name").ifBlank { series.optString("series") }
            val id = row.optInt("id")
            if (title.isBlank() || id <= 0) continue
            val cover = row.optString("image").takeIf { it.startsWith("https://") }
            add(Issue(id, title, series.optInt("year_began"), row.optString("number"), date, cover))
        }
    }.distinctBy { it.id }.sortedWith(compareBy<Issue> { it.date }.thenBy { it.title }.thenBy { it.number })

    fun cached(context: Context): Calendar? = runCatching {
        val p = prefs(context)
        val raw = p.getString("calendar", null) ?: return null
        val body = JSONObject(raw)
        Calendar(parse(body.getJSONArray("issues")), body.optBoolean("partial"), body.optLong("fetched"))
    }.getOrNull()

    suspend fun load(context: Context, force: Boolean = false): Calendar = withContext(Dispatchers.IO) {
        lock.withLock {
            val today = LocalDate.now()
            val old = cached(context)
            if (!force && old != null && System.currentTimeMillis() - old.fetched in 0..TimeUnit.HOURS.toMillis(8)) return@withLock old
            val (rows, partial) = InkShelfMetronCovers.fetchReleaseCalendar(context, today.minusDays(1).toString(), today.plusDays(7).toString())
            val body = JSONArray()
            rows.forEach { body.put(it) }
            val time = System.currentTimeMillis()
            prefs(context).edit().putString("calendar", JSONObject().put("issues", body)
                .put("partial", partial).put("fetched", time).toString()).apply()
            Calendar(parse(body), partial, time)
        }
    }

    /** Strict title AND edition matching for phone alerts; no guessing by title alone. */
    suspend fun notifyReleases(context: Context, calendar: Calendar) {
        if (!enabled(context) || !canNotify(context)) return
        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannel(Notifications.CHANNEL_RELEASE_RADAR)
        if (channel?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return
        val p = prefs(context)
        val today = LocalDate.now()
        val titles = InkShelf2Engine(context).loadLibrary().map { it.manga.title }
        val old = p.getStringSet("notified_ids", emptySet()).orEmpty()
        val fresh = calendar.issues.filter { it.date in today.minusDays(1)..today && it.id.toString() !in old &&
            titles.any { title -> matches(it, title, strict = true) } }
        if (fresh.isEmpty()) return
        val hide = Injekt.get<PreferencesHelper>().hideNotificationContent()
        val intent = Intent(context, InkShelf2Activity::class.java).setAction(InkShelf2Activity.ACTION_SHOW_RELEASE_RADAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 821, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val detail = if (hide) "Check Release Radar for details." else fresh.joinToString("\n") { it.title + " #" + it.number }
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_RELEASE_RADAR)
            .setSmallIcon(R.drawable.ic_tachij2k_notification)
            .setContentTitle(if (fresh.size == 1) "Comic release · Release Radar" else "${fresh.size} comic releases · Release Radar")
            .setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(Notifications.ID_RELEASE_RADAR, notification)
        p.edit().putStringSet("notified_ids", (old + fresh.map { it.id.toString() }).toList().takeLast(500).toSet()).apply()
    }
}

class InkShelf2ReleaseRadarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!InkShelf2ReleaseRadar.enabled(context) || !InkShelf2ReleaseRadar.configured(context) ||
            !InkShelf2ReleaseRadar.canNotify(context)) return Result.success()
        return try {
            val calendar = InkShelf2ReleaseRadar.load(context)
            InkShelf2ReleaseRadar.notifyReleases(context, calendar)
            Result.success()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            Result.retry()
        }
    }
    companion object {
        private const val NAME = "InkShelfReleaseRadar"
        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            if (!InkShelf2ReleaseRadar.enabled(context)) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<InkShelf2ReleaseRadarWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build()).build()
            manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

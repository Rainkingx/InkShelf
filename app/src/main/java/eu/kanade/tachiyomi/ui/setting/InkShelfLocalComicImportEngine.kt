package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import eu.kanade.tachiyomi.source.LocalSource
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Shared original InkShelf local comic importer. Both the classic settings
 * picker and the InkShelf 2.0 picker use these exact metadata/deduplication
 * routines and the existing J2K Local Source registrar.
 */
internal object InkShelfLocalComicImportEngine {
    private val LOCAL_COMIC_IMPORT_TYPES = setOf("zip", "cbz", "rar", "cbr", "epub")

    data class Result(
        val imported: Int,
        val ready: Int,
        val skipped: Int,
        val failed: Int,
    )

    fun importComics(context: Context, uris: List<Uri>): Result {
        val root = LocalSource.getImportDirectory(context)
            ?: throw IllegalStateException("Local comic storage directory is unavailable")
        val result = performLocalComicImport(context, root, uris.distinct())
        val registered = InkShelfLocalLibraryRegistrar.register(
            context,
            result.seriesNames,
            staleSeriesNames = result.staleSeriesNames,
        )
        return Result(
            imported = result.imported,
            ready = registered.ready,
            skipped = result.skipped,
            failed = result.failed + registered.failed,
        )
    }

    private fun performLocalComicImport(
        context: Context,
        importRoot: File,
        uris: List<Uri>,
    ): LocalImportResult {
        val candidates = mutableListOf<LocalImportCandidate>()
        var skipped = 0

        uris.forEach { uri ->
            val displayName = getDocumentDisplayName(context, uri)?.trim().orEmpty()
            val extension = displayName.substringAfterLast('.', "").lowercase()

            if (displayName.isBlank() || extension !in LOCAL_COMIC_IMPORT_TYPES) {
                skipped++
                return@forEach
            }

            val baseName = displayName.substringBeforeLast('.', displayName).trim()
            val guessedSeries = guessSeriesName(baseName)
            val comicInfo = readComicInfo(context, uri, extension)

            candidates +=
                LocalImportCandidate(
                    uri = uri,
                    displayName = displayName,
                    baseName = baseName,
                    guessedSeries = guessedSeries,
                    comicInfo = comicInfo,
                )
        }

        val guessedSeriesCounts =
            candidates
                .groupingBy { it.guessedSeries.lowercase() }
                .eachCount()

        var imported = 0
        var failed = 0
        val importedSeries = linkedSetOf<String>()
        val staleSeries = linkedSetOf<String>()

        candidates.forEach { candidate ->
            val repeatedGuess =
                guessedSeriesCounts[candidate.guessedSeries.lowercase()].orEmptyCount() > 1

            val rawSeriesName = chooseSeriesName(candidate, repeatedGuess)
            val seriesName = sanitizeLocalSeriesName(rawSeriesName)
            val seriesDirectory = File(importRoot, seriesName)

            try {
                if (!seriesDirectory.exists() && !seriesDirectory.mkdirs()) {
                    failed++
                    return@forEach
                }

                val sourceHash = sha256(context, candidate.uri)
                val sourceSize = getDocumentSize(context, candidate.uri)
                val preferredFileName = buildImportedIssueFileName(candidate)

                val existing =
                    findIdenticalImportedFiles(
                        importRoot = importRoot,
                        targetSeriesDirectory = seriesDirectory,
                        originalDisplayName = candidate.displayName,
                        sourceHash = sourceHash,
                        sourceSize = sourceSize,
                    )

                if (existing.isNotEmpty()) {
                    val migration =
                        consolidateIdenticalImports(
                            identicalFiles = existing,
                            targetSeriesDirectory = seriesDirectory,
                            preferredFileName = preferredFileName,
                        )
                    staleSeries += migration.removedSeriesNames
                } else {
                    val destination = uniqueDestination(seriesDirectory, preferredFileName)
                    val input = context.contentResolver.openInputStream(candidate.uri)
                        ?: throw IllegalStateException("Unable to open selected comic")

                    input.use { source ->
                        destination.outputStream().buffered().use { output ->
                            source.copyTo(output)
                        }
                    }
                }

                writeLocalInfoJson(seriesDirectory, seriesName, candidate.comicInfo)
                imported++
                importedSeries += seriesName
            } catch (_: Exception) {
                failed++
            }
        }

        return LocalImportResult(
            imported = imported,
            skipped = skipped,
            failed = failed,
            seriesNames = importedSeries,
            staleSeriesNames = staleSeries,
        )
    }

    private fun getDocumentDisplayName(
        context: Context,
        uri: Uri,
    ): String? {
        return try {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
                ?: uri.lastPathSegment?.substringAfterLast('/')
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        }
    }

    private fun getDocumentSize(
        context: Context,
        uri: Uri,
    ): Long? {
        return try {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getLong(index)
                    } else {
                        null
                    }
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun readComicInfo(
        context: Context,
        uri: Uri,
        extension: String,
    ): ComicInfoMetadata? {
        if (extension != "zip" && extension != "cbz") return null

        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                ZipInputStream(source.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val entryName = entry.name.substringAfterLast('/')

                        if (!entry.isDirectory && entryName.equals("ComicInfo.xml", ignoreCase = true)) {
                            return parseComicInfo(zip)
                        }

                        zip.closeEntry()
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseComicInfo(input: InputStream): ComicInfoMetadata {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val values = linkedMapOf<String, String>()
        val wantedTags =
            setOf(
                "series",
                "title",
                "number",
                "summary",
                "writer",
                "penciller",
                "inker",
                "colorist",
                "letterer",
                "coverartist",
                "editor",
                "publisher",
                "genre",
                "tags",
                "year",
                "month",
                "day",
                "languageiso",
            )

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                val tag = parser.name.lowercase()
                if (tag in wantedTags) {
                    val value = parser.nextText().trim()
                    if (value.isNotBlank()) values[tag] = value
                }
            }
            eventType = parser.next()
        }

        return ComicInfoMetadata(
            series = values["series"],
            title = values["title"],
            number = values["number"],
            summary = values["summary"],
            writer = values["writer"],
            penciller = values["penciller"],
            inker = values["inker"],
            colorist = values["colorist"],
            letterer = values["letterer"],
            coverArtist = values["coverartist"],
            editor = values["editor"],
            publisher = values["publisher"],
            genre = values["genre"],
            tags = values["tags"],
            year = values["year"],
            month = values["month"],
            day = values["day"],
            languageIso = values["languageiso"],
        )
    }

    private fun chooseSeriesName(
        candidate: LocalImportCandidate,
        repeatedGuess: Boolean,
    ): String {
        val metadataSeries = candidate.comicInfo?.series?.trim().orEmpty()
        val metadataTitle = candidate.comicInfo?.title?.trim().orEmpty()

        return when {
            metadataSeries.isNotBlank() && !isSuspiciousSeriesName(metadataSeries) -> metadataSeries
            metadataSeries.isNotBlank() && isSuspiciousSeriesName(metadataSeries) && looksLikeSeriesTitle(metadataTitle) -> metadataTitle
            isSuspiciousSeriesName(candidate.baseName) && looksLikeSeriesTitle(metadataTitle) -> metadataTitle
            repeatedGuess -> candidate.guessedSeries
            else -> candidate.baseName
        }
    }

    private fun isSuspiciousSeriesName(value: String): Boolean {
        val normalized = value.trim().lowercase()
        if (normalized.isBlank()) return true
        return normalized.startsWith("#") ||
            normalized.matches(Regex("""(?:annual|special|issue|chapter)\b.*""")) ||
            normalized.matches(Regex("""(?:19|20)\d{2}""")) ||
            normalized.matches(Regex("""\d+(?:\.\d+)?"""))
    }

    private fun looksLikeSeriesTitle(value: String): Boolean =
        value.trim().length >= 3 && !isSuspiciousSeriesName(value)

    private fun buildImportedIssueFileName(candidate: LocalImportCandidate): String {
        val extension = candidate.displayName.substringAfterLast('.', "")
        if (extension.isBlank()) return candidate.displayName

        val metadata = candidate.comicInfo ?: return candidate.displayName
        val number = metadata.number?.trim().orEmpty()
        val title = metadata.title?.trim().orEmpty()
        if (number.isBlank()) return candidate.displayName

        val safeNumber = sanitizeLocalFilePart(number)
        val safeTitle = sanitizeLocalFilePart(title)
        val stem =
            when {
                safeTitle.isBlank() -> safeNumber
                safeTitle.equals(safeNumber, ignoreCase = true) -> safeNumber
                else -> "$safeNumber - $safeTitle"
            }

        return "${stem.take(150)}.$extension"
    }

    private fun sanitizeLocalFilePart(value: String): String =
        value
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun writeLocalInfoJson(
        seriesDirectory: File,
        seriesName: String,
        metadata: ComicInfoMetadata?,
    ) {
        if (metadata == null) return

        val json = JSONObject()
        json.put("title", seriesName)

        metadata.writer
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { json.put("author", it) }

        val artists =
            listOf(
                metadata.penciller,
                metadata.inker,
                metadata.colorist,
                metadata.letterer,
                metadata.coverArtist,
            ).flatMap(::splitComicInfoPeople)
                .distinctBy { it.lowercase() }
        if (artists.isNotEmpty()) {
            json.put("artist", artists.joinToString(", "))
        }

        val descriptionParts = mutableListOf<String>()
        metadata.summary?.trim()?.takeIf { it.isNotBlank() }?.let(descriptionParts::add)

        val facts = mutableListOf<String>()
        metadata.publisher?.trim()?.takeIf { it.isNotBlank() }?.let { facts += "Publisher: $it" }
        comicInfoPublishedDate(metadata)?.let { facts += "Published: $it" }
        metadata.number?.trim()?.takeIf { it.isNotBlank() }?.let { number ->
            val issueTitle = metadata.title?.trim().orEmpty()
            facts += if (issueTitle.isNotBlank() && !issueTitle.equals(seriesName, ignoreCase = true)) {
                "Issue: $number Ã¢â‚¬â€ $issueTitle"
            } else {
                "Issue: $number"
            }
        }
        if (facts.isNotEmpty()) descriptionParts += facts.joinToString(" Ã¢â‚¬Â¢ ")
        if (descriptionParts.isNotEmpty()) {
            json.put("description", descriptionParts.joinToString("\n\n"))
        }

        val genres =
            (splitComicInfoList(metadata.genre) + splitComicInfoList(metadata.tags))
                .distinctBy { it.lowercase() }
        if (genres.isNotEmpty()) {
            json.put("genre", org.json.JSONArray(genres))
        }

        metadata.languageIso
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { json.put("lang", it) }

        File(seriesDirectory, "info.json").writeText(json.toString(2))
    }

    private fun splitComicInfoPeople(value: String?): List<String> = splitComicInfoList(value)

    private fun splitComicInfoList(value: String?): List<String> =
        value
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun comicInfoPublishedDate(metadata: ComicInfoMetadata): String? {
        val year = metadata.year?.trim()?.takeIf { it.isNotBlank() && it != "0" } ?: return null
        val month = metadata.month?.trim()?.takeIf { it.isNotBlank() && it != "0" }
        val day = metadata.day?.trim()?.takeIf { it.isNotBlank() && it != "0" }

        return buildString {
            append(year)
            month?.let { append("-").append(it.padStart(2, '0')) }
            day?.let { append("-").append(it.padStart(2, '0')) }
        }
    }

    private fun sha256(
        context: Context,
        uri: Uri,
    ): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source -> digestStream(source) }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(file: File): String? {
        return try {
            file.inputStream().use { source -> digestStream(source) }
        } catch (_: Exception) {
            null
        }
    }

    private fun digestStream(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun findIdenticalImportedFiles(
        importRoot: File,
        targetSeriesDirectory: File,
        originalDisplayName: String,
        sourceHash: String?,
        sourceSize: Long?,
    ): List<File> {
        if (sourceHash == null) return emptyList()

        return importRoot
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .flatMap { directory -> directory.listFiles().orEmpty().asSequence() }
            .filter { file ->
                file.isFile &&
                    file.extension.lowercase() in LOCAL_COMIC_IMPORT_TYPES &&
                    (file.parentFile == targetSeriesDirectory || matchesPriorImportedName(file.name, originalDisplayName)) &&
                    (sourceSize == null || file.length() == sourceSize)
            }.filter { file -> sha256(file) == sourceHash }
            .toList()
    }

    private fun matchesPriorImportedName(
        fileName: String,
        originalDisplayName: String,
    ): Boolean {
        if (fileName.equals(originalDisplayName, ignoreCase = true)) return true

        val extension = originalDisplayName.substringAfterLast('.', "")
        val baseName =
            if (extension.isBlank()) originalDisplayName else originalDisplayName.dropLast(extension.length + 1)
        val pattern =
            if (extension.isBlank()) {
                Regex("^${Regex.escape(baseName)} \\(\\d+\\)$", RegexOption.IGNORE_CASE)
            } else {
                Regex("^${Regex.escape(baseName)} \\(\\d+\\)\\.${Regex.escape(extension)}$", RegexOption.IGNORE_CASE)
            }
        return pattern.matches(fileName)
    }

    private fun consolidateIdenticalImports(
        identicalFiles: List<File>,
        targetSeriesDirectory: File,
        preferredFileName: String,
    ): ExistingImportMigration {
        val removedSeriesNames = linkedSetOf<String>()
        val preferredExisting = identicalFiles.firstOrNull { it.parentFile == targetSeriesDirectory }
        var canonical = preferredExisting ?: identicalFiles.first()

        if (canonical.parentFile != targetSeriesDirectory || !canonical.name.equals(preferredFileName, ignoreCase = true)) {
            val destination =
                File(targetSeriesDirectory, preferredFileName).let { preferred ->
                    when {
                        !preferred.exists() -> preferred
                        sha256(preferred) == sha256(canonical) -> preferred
                        else -> uniqueDestination(targetSeriesDirectory, preferredFileName)
                    }
                }

            if (destination != canonical) {
                if (destination.exists() && sha256(destination) == sha256(canonical)) {
                    canonical.delete()
                } else if (!canonical.renameTo(destination)) {
                    canonical.copyTo(destination, overwrite = false)
                    canonical.delete()
                }
                val oldParent = canonical.parentFile
                canonical = destination
                if (oldParent != null && oldParent != targetSeriesDirectory && cleanupMigratedSeriesDirectory(oldParent)) {
                    removedSeriesNames += oldParent.name
                }
            }
        }

        identicalFiles
            .filter { it.absolutePath != canonical.absolutePath }
            .forEach { duplicate ->
                val parent = duplicate.parentFile
                if (duplicate.exists()) duplicate.delete()
                if (parent != null && parent != targetSeriesDirectory && cleanupMigratedSeriesDirectory(parent)) {
                    removedSeriesNames += parent.name
                }
            }

        return ExistingImportMigration(removedSeriesNames)
    }

    private fun cleanupMigratedSeriesDirectory(directory: File): Boolean {
        if (!directory.exists() || !directory.isDirectory) return false

        val hasComicContent =
            directory
                .listFiles()
                .orEmpty()
                .any { child ->
                    child.isDirectory || child.extension.lowercase() in LOCAL_COMIC_IMPORT_TYPES
                }
        if (hasComicContent) return false

        directory.listFiles().orEmpty().forEach { child ->
            val disposable =
                child.name.equals("info.json", ignoreCase = true) ||
                    child.nameWithoutExtension.equals("cover", ignoreCase = true)
            if (disposable) child.delete()
        }

        return directory.listFiles().orEmpty().isEmpty() && directory.delete()
    }
    private fun guessSeriesName(baseName: String): String {
        val cleaned =
            baseName
                .replace(Regex("""[._]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        val withoutTrailingYear =
            cleaned.replace(
                Regex("""\s*[\[(](?:19|20)\d{2}[\])]\s*$""", RegexOption.IGNORE_CASE),
                "",
            )

        val withoutIssueNumber =
            withoutTrailingYear.replace(
                Regex("""\s*(?:[-ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â]\s*)?(?:#|issue\s*)?\d{1,4}(?:\.\d+)?\s*$""", RegexOption.IGNORE_CASE),
                "",
            ).trim()

        return withoutIssueNumber.ifBlank { cleaned }
    }

    private fun sanitizeLocalSeriesName(name: String): String {
        return name
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(120)
            .ifBlank { "Imported Comic" }
    }

    private fun uniqueDestination(
        directory: File,
        originalName: String,
    ): File {
        var candidate = File(directory, originalName)
        if (!candidate.exists()) return candidate

        val extension = originalName.substringAfterLast('.', "")
        val baseName =
            if (extension.isBlank()) {
                originalName
            } else {
                originalName.dropLast(extension.length + 1)
            }

        var suffix = 2
        while (candidate.exists()) {
            val fileName =
                if (extension.isBlank()) {
                    "$baseName ($suffix)"
                } else {
                    "$baseName ($suffix).$extension"
                }

            candidate = File(directory, fileName)
            suffix++
        }

        return candidate
    }

    private fun Int?.orEmptyCount(): Int = this ?: 0

    private data class LocalImportCandidate(
        val uri: Uri,
        val displayName: String,
        val baseName: String,
        val guessedSeries: String,
        val comicInfo: ComicInfoMetadata?,
    )

    private data class ComicInfoMetadata(
        val series: String? = null,
        val title: String? = null,
        val number: String? = null,
        val summary: String? = null,
        val writer: String? = null,
        val penciller: String? = null,
        val inker: String? = null,
        val colorist: String? = null,
        val letterer: String? = null,
        val coverArtist: String? = null,
        val editor: String? = null,
        val publisher: String? = null,
        val genre: String? = null,
        val tags: String? = null,
        val year: String? = null,
        val month: String? = null,
        val day: String? = null,
        val languageIso: String? = null,
    )

    private data class ExistingImportMigration(
        val removedSeriesNames: Set<String>,
    )

    private data class LocalImportResult(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val seriesNames: Set<String>,
        val staleSeriesNames: Set<String>,
    )

}

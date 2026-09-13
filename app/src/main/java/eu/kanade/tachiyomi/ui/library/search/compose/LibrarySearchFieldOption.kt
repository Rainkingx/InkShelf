package eu.kanade.tachiyomi.ui.library.search.compose

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.search.ComparisonField
import eu.kanade.tachiyomi.ui.library.search.MangaField

/**
 * A search field/type surfaced in the search filter sheet's list.
 *
 * @property token the query prefix to insert (e.g. `desc`, `added`)
 * @property isComparison whether this field takes a comparator (`>`, `<`, `=`, ...)
 * @property opensDatePicker whether this field should prompt for a date
 * @property moreLabelRes label for the button that inserts this field with a `>` comparator
 * @property lessLabelRes label for the button that inserts this field with a `<` comparator
 * @property valueOptions if provided, shows a dropdown of these options when option is selected
 */
data class LibrarySearchFieldOption(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val token: String,
    val isComparison: Boolean = false,
    val opensDatePicker: Boolean = false,
    @StringRes val moreLabelRes: Int? = null,
    @StringRes val lessLabelRes: Int? = null,
    val valueOptions: List<String>? = null,
)

// The second alias declared on a field is its shorthand (e.g. GENRE's are "genre", "tag");
// fields without one just have a single alias.
private val MangaField.shorthand: String
    get() = aliases.getOrElse(1) { aliases[0] }

private val ComparisonField.shorthand: String
    get() = aliases.getOrElse(1) { aliases[0] }

/**
 * The fields/types offered in the search filter sheet.
 *
 * @param showScore whether to include the Score field - only meaningful if the user is logged
 * into at least one tracker (see [eu.kanade.tachiyomi.ui.library.LibraryPresenter.isLoggedIntoTracking]),
 * since an untracked library has no scores to filter on.
 */
@Composable
fun rememberLibrarySearchFieldOptions(
    seriesTypeOptions: List<String> = emptyList(),
    showScore: Boolean = false,
): List<LibrarySearchFieldOption> {
    val sourceIcon = ImageVector.vectorResource(id = R.drawable.ic_browse_outline_24dp)
    return remember(seriesTypeOptions, showScore) {
        listOfNotNull(
            LibrarySearchFieldOption(
                R.string.title,
                Icons.Outlined.Title,
                MangaField.TITLE.shorthand,
            ),
            LibrarySearchFieldOption(
                R.string.author,
                Icons.Outlined.PersonOutline,
                MangaField.AUTHOR.shorthand,
            ),
            LibrarySearchFieldOption(
                R.string.artist,
                Icons.Outlined.Brush,
                MangaField.ARTIST.shorthand,
            ),
            LibrarySearchFieldOption(
                R.string.description,
                Icons.Outlined.Description,
                MangaField.DESCRIPTION.shorthand,
            ),
            LibrarySearchFieldOption(R.string.tag, Icons.Outlined.Style, MangaField.GENRE.shorthand),
            LibrarySearchFieldOption(R.string.source, sourceIcon, MangaField.SOURCE.shorthand),
            LibrarySearchFieldOption(
                R.string.language,
                Icons.Outlined.Translate,
                MangaField.LANGUAGE.shorthand,
            ),
            LibrarySearchFieldOption(
                R.string.series_type,
                Icons.Outlined.Category,
                MangaField.SERIES_TYPE.shorthand,
                valueOptions = seriesTypeOptions,
            ),
            LibrarySearchFieldOption(
                R.string.date_added,
                Icons.Outlined.FavoriteBorder,
                ComparisonField.DATE_ADDED.shorthand,
                isComparison = true,
                opensDatePicker = true,
                moreLabelRes = R.string.search_after,
                lessLabelRes = R.string.search_before,
            ),
            LibrarySearchFieldOption(
                R.string.unread_chapters,
                Icons.Outlined.VisibilityOff,
                ComparisonField.UNREAD.shorthand,
                isComparison = true,
                moreLabelRes = R.string.search_more_than,
                lessLabelRes = R.string.search_fewer_than,
            ),
            LibrarySearchFieldOption(
                R.string.chapters_read,
                Icons.Outlined.Visibility,
                ComparisonField.READ.shorthand,
                isComparison = true,
                moreLabelRes = R.string.search_more_than,
                lessLabelRes = R.string.search_fewer_than,
            ),
            LibrarySearchFieldOption(
                R.string.total_chapters,
                Icons.Outlined.FormatListNumbered,
                ComparisonField.TOTAL.shorthand,
                isComparison = true,
                moreLabelRes = R.string.search_more_than,
                lessLabelRes = R.string.search_fewer_than,
            ),
            if (showScore) {
                LibrarySearchFieldOption(
                    R.string.score,
                    Icons.Outlined.Star,
                    ComparisonField.SCORE.shorthand,
                    isComparison = true,
                    moreLabelRes = R.string.search_more_than,
                    lessLabelRes = R.string.search_less_than,
                )
            } else {
                null
            },
        )
    }
}

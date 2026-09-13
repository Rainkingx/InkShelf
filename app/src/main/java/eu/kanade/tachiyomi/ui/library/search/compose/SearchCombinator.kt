package eu.kanade.tachiyomi.ui.library.search.compose

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.graphics.vector.ImageVector
import eu.kanade.tachiyomi.R

/**
 * Whether a field picked from [LibrarySearchFieldOptions] should require a match ([AND]) or
 * require its absence ([NOT]) - see [eu.kanade.tachiyomi.ui.library.LibraryController.insertSearchTokenText].
 */
enum class SearchCombinator(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    AND(R.string.search_operator_and, Icons.Outlined.Add),
    NOT(R.string.search_operator_not, Icons.Outlined.Close),
}

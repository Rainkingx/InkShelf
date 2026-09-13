package eu.kanade.tachiyomi.appwidget.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import eu.kanade.tachiyomi.R

fun GlanceModifier.appWidgetBackgroundRadius(): GlanceModifier = this.cornerRadius(R.dimen.appwidget_background_radius)

fun GlanceModifier.appWidgetInnerRadius(): GlanceModifier = this.cornerRadius(R.dimen.appwidget_inner_radius)

@Composable
fun stringResource(
    @StringRes id: Int,
): String = LocalContext.current.getString(id)

/**
 * Calculates row-column count.
 *
 * Row
 * Numerator: Container height - container vertical padding
 * Denominator: Cover height + cover vertical padding
 *
 * Column
 * Numerator: Container width - container horizontal padding
 * Denominator: Cover width + cover horizontal padding
 *
 * @return pair of row and column count
 */
fun DpSize.calculateRowAndColumnCount(): Pair<Int, Int> {
    // Hack: Size provided by Glance manager is not reliable so take at least 1 row and 1 column
    // Set max to 10 children each direction because of Glance limitation
    val rowCount = (height.value / 95).toInt().coerceIn(1, 10)
    val columnCount = (width.value / 64).toInt().coerceIn(1, 10)
    return capCellCount(rowCount, columnCount)
}

// Max Cover limit to not overload the widget's memory budget
private const val MaxCellCount = 24

private fun capCellCount(
    rowCount: Int,
    columnCount: Int,
): Pair<Int, Int> {
    var rows = rowCount
    var columns = columnCount
    while (rows * columns > MaxCellCount && (rows > 1 || columns > 1)) {
        if (rows >= columns) rows-- else columns--
    }
    return Pair(rows, columns)
}

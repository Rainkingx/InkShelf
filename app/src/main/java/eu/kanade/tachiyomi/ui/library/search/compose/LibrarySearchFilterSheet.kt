package eu.kanade.tachiyomi.ui.library.search.compose

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.compose.AppComposeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Content for the library search filter sheet: an include/exclude [SearchCombinator] plus a
 * separate "optional" toggle that together determine how the next picked field should be joined
 * to the existing search text, and a list of the fields that can be searched on. Comparison
 * fields ([LibrarySearchFieldOption.isComparison]) get an extra pair of shortcut buttons to
 * insert them with `>`/`<` instead of the default `=`.
 *
 * @param minDateAddedMillis earliest date (UTC epoch millis) selectable in the "date added" date
 * picker - typically the library's oldest [eu.kanade.tachiyomi.data.database.models.Manga.date_added].
 * Null leaves the lower bound unrestricted. The upper bound is always today.
 * @param seriesTypeOptions the distinct series-type labels actually present in the library - see
 * [rememberLibrarySearchFieldOptions]. Empty hides the series type field entirely.
 * @param showScore whether to show the Score field - see [rememberLibrarySearchFieldOptions].
 * @param onFieldSelected called with the currently selected [SearchCombinator] (include/exclude
 * polarity), whether the join is optional (an OR alternative rather than a required match), the
 * tapped [LibrarySearchFieldOption], the separator to join it to a value with (`:` for plain
 * fields, `=`/`>`/`<` for comparison fields), and a value to pair it with - non-null only when a
 * date was picked for a [LibrarySearchFieldOption.opensDatePicker] field, or a value was picked
 * from a [LibrarySearchFieldOption.valueOptions] dropdown; null otherwise, leaving the value for
 * the caller to fill in. Left as a no-op default for now - inserting the token into the search box
 * is wired up by the caller.
 * @param onDateRangeSelected called instead of [onFieldSelected] when a "Date range" bound is
 * picked, with the two dates the range should insert as one `field>=start, field<=end` group. The
 * caller is expected to wrap the pair in parentheses so it stays a single unit regardless of
 * surrounding optional/exclude logic - see [eu.kanade.tachiyomi.ui.library.LibraryController.insertSearchDateRangeToken].
 */
@Composable
fun LibrarySearchFilterSheetContent(
    minDateAddedMillis: Long? = null,
    showOptional: Boolean = true,
    seriesTypeOptions: List<String> = emptyList(),
    showScore: Boolean = false,
    onFieldSelected: (SearchCombinator, Boolean, LibrarySearchFieldOption, String, String?) -> Unit = { _, _, _, _, _ -> },
    onDateRangeSelected: (SearchCombinator, Boolean, LibrarySearchFieldOption, String, String) -> Unit = { _, _, _, _, _ -> },
) {
    var combinator by remember { mutableStateOf(SearchCombinator.AND) }
    var optionalChecked by remember { mutableStateOf(false) }
    var pendingDateField by remember { mutableStateOf<LibrarySearchFieldOption?>(null) }
    var pendingDateSeparator by remember { mutableStateOf("=") }
    var pendingDateRangeField by remember { mutableStateOf<LibrarySearchFieldOption?>(null) }
    val haptic = LocalHapticFeedback.current
    val tooltipState = rememberTooltipState()

    // Optional stops making sense once there's nothing to combine with - fall back to a required
    // match rather than leaving a hidden option checked.
    LaunchedEffect(showOptional) {
        if (!showOptional && optionalChecked) {
            optionalChecked = false
        }
    }

    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun selectField(
        field: LibrarySearchFieldOption,
        separator: String,
    ) {
        if (field.opensDatePicker) {
            pendingDateField = field
            pendingDateSeparator = separator
        } else {
            onFieldSelected(combinator, optionalChecked, field, separator, null)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Text(
            text = stringResource(if (optionalChecked) R.string.library_optional_search_filters else R.string.library_search_filters),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 4.dp).padding(top = 8.dp, bottom = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showOptional) {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip { Text(stringResource(R.string.search_toggle_optional)) }
                    },
                    state = tooltipState,
                ) {
                    OutlinedIconToggleButton(
                        checked = optionalChecked,
                        onCheckedChange = { optionalChecked = it },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        colors =
                            IconButtonDefaults.outlinedIconToggleButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        border =
                            if (optionalChecked) {
                                null
                            } else {
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                )
                            },
                        // Set to properly align with other fields
                        modifier = Modifier.offset(x = (-4).dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.CallSplit,
                            contentDescription = stringResource(R.string.search_toggle_optional),
                        )
                    }
                }
            }
            SearchCombinatorSelector(
                combinator = combinator,
                onCombinatorChange = { combinator = it },
                modifier =
                    Modifier
                        .fillMaxWidth(),
            )
        }

        val (containerColor, labelColor) = rememberLibraryChipColors()
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy((-4).dp),
        ) {
            rememberLibrarySearchFieldOptions(seriesTypeOptions = seriesTypeOptions, showScore = showScore).forEach { field ->
                val valueOptions = field.valueOptions
                when {
                    field.isComparison ->
                        LibrarySearchComparisonChip(
                            field = field,
                            containerColor = containerColor,
                            labelColor = labelColor,
                            onSelect = { separator -> selectField(field, separator) },
                            onSelectDateRange = { pendingDateRangeField = field },
                        )
                    // Non-null valueOptions means this field is dropdown-driven. "Manga" alone
                    // (or manga + one other type) isn't a meaningful choice - only worth showing
                    // once there are more than 1 distinct types to actually pick between.
                    valueOptions != null -> {
                        if (valueOptions.size > 1) {
                            LibrarySearchValueDropdownChip(
                                field = field,
                                containerColor = containerColor,
                                labelColor = labelColor,
                                options = valueOptions,
                                onSelectValue = { value -> onFieldSelected(combinator, optionalChecked, field, ":", value) },
                            )
                        }
                    }
                    else ->
                        LibrarySearchFieldChip(
                            field = field,
                            containerColor = containerColor,
                            labelColor = labelColor,
                            onClick = { selectField(field, ":") },
                        )
                }
            }
        }
    }

    val dateField = pendingDateField
    if (dateField != null) {
        val bounds = rememberDateBounds(minDateAddedMillis)
        val datePickerState = rememberDatePickerState(yearRange = bounds.yearRange, selectableDates = bounds.selectableDates)
        DatePickerDialog(
            onDismissRequest = { pendingDateField = null },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onFieldSelected(combinator, optionalChecked, dateField, pendingDateSeparator, date.toString())
                        }
                        pendingDateField = null
                    },
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDateField = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val dateRangeField = pendingDateRangeField
    if (dateRangeField != null) {
        val bounds = rememberDateBounds(minDateAddedMillis)
        val dateRangePickerState = rememberDateRangePickerState(yearRange = bounds.yearRange, selectableDates = bounds.selectableDates)
        DatePickerDialog(
            onDismissRequest = { pendingDateRangeField = null },
            confirmButton = {
                TextButton(
                    enabled =
                        dateRangePickerState.selectedStartDateMillis != null &&
                            dateRangePickerState.selectedEndDateMillis != null,
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            val endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            onDateRangeSelected(combinator, optionalChecked, dateRangeField, startDate.toString(), endDate.toString())
                        }
                        pendingDateRangeField = null
                    },
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDateRangeField = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DateRangePicker(state = dateRangePickerState)
        }
    }
}

private class DateBounds(
    val yearRange: IntRange,
    val selectableDates: SelectableDates,
)

/**
 * Bounds shared by the single-date and date-range pickers: selectable from [minDateAddedMillis]
 * (typically the library's oldest date_added) through today.
 */
@Composable
private fun rememberDateBounds(minDateAddedMillis: Long?): DateBounds {
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val minDate =
        remember(minDateAddedMillis) {
            minDateAddedMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        }
    return remember(today, minDate) {
        DateBounds(
            yearRange = (minDate?.year ?: DatePickerDefaults.YearRange.first)..today.year,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        return !date.isAfter(today) && (minDate == null || !date.isBefore(minDate))
                    }

                    override fun isSelectableYear(year: Int): Boolean = year <= today.year && (minDate == null || year >= minDate.year)
                },
        )
    }
}

/** How much of [SearchCombinatorSelector]'s label text fits without overflowing the available width. */
private enum class CombinatorLabelMode { All, SelectedOnly, None }

private enum class CombinatorSlot { MeasureAll, MeasureSelectedOnly, Content }

/**
 * A connected [ToggleButton] group for picking [SearchCombinator] - Material3's pill-shaped
 * single-choice group style, per [androidx.compose.material3.samples.SingleSelectConnectedButtonGroupSample].
 *
 * Whether labels are shown depends on the *actual measured width* of the row's text.
 * [SubcomposeLayout] measures the widest candidate row against the incoming constraints first; if
 * it doesn't fit, it tries showing a label on just the selected button; if that doesn't fit either,
 * it falls back to icon-only. At each width, the checked button always shows at least the icon and
 * label; unchecked buttons show whichever single element (label at the widest, icon otherwise)
 * that width allows - see [CombinatorRow].
 */
@Composable
private fun SearchCombinatorSelector(
    combinator: SearchCombinator,
    onCombinatorChange: (SearchCombinator) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = SearchCombinator.entries

    SubcomposeLayout(modifier = modifier) { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)

        fun measuredWidth(
            slot: CombinatorSlot,
            labelMode: CombinatorLabelMode,
        ): Int =
            subcompose(slot) {
                CombinatorRow(entries, combinator, onCombinatorChange, labelMode)
            }.first().measure(looseConstraints).width

        val labelMode =
            when {
                measuredWidth(CombinatorSlot.MeasureAll, CombinatorLabelMode.All) <= constraints.maxWidth -> CombinatorLabelMode.All
                measuredWidth(CombinatorSlot.MeasureSelectedOnly, CombinatorLabelMode.SelectedOnly) <=
                    constraints.maxWidth -> CombinatorLabelMode.SelectedOnly
                else -> CombinatorLabelMode.None
            }

        val placeable =
            subcompose(CombinatorSlot.Content) {
                CombinatorRow(entries, combinator, onCombinatorChange, labelMode)
            }.first().measure(constraints)

        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CombinatorRow(
    entries: List<SearchCombinator>,
    combinator: SearchCombinator,
    onCombinatorChange: (SearchCombinator) -> Unit,
    labelMode: CombinatorLabelMode,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        entries.forEachIndexed { index, option ->
            val checked = combinator == option
            val containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            val contentColor = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onBackground
            val showLabel = labelMode == CombinatorLabelMode.All || (labelMode == CombinatorLabelMode.SelectedOnly && checked)
            // At the widest, unchecked buttons drop the icon in favor of the label instead of
            // showing both - the checked button keeps its icon so selection stays recognizable.
            val showIcon = labelMode != CombinatorLabelMode.All || checked

            ToggleButton(
                checked = checked,
                onCheckedChange = { onCombinatorChange(option) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                // Matches the app's XML connected-button-group style (ThemeOverlay.Tachiyomi.Button.Outlined):
                // transparent + outlined when unchecked, filled with no outline when checked.
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                        checkedContainerColor = containerColor,
                        checkedContentColor = contentColor,
                    ),
                border = if (checked) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier =
                    Modifier
                        .semantics { role = Role.RadioButton },
            ) {
                if (showIcon) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                    )
                }
                if (showIcon && showLabel) {
                    Spacer(Modifier.width(2.dp))
                }
                if (showLabel) {
                    Text(stringResource(option.labelRes))
                }
            }
        }
    }
}

/**
 * A comparison field (unread/read/total/date added), rendered as a [LibrarySearchFieldChip] with
 * a dropdown-indicator trailing icon. Tapping it opens a [DropdownMenu] with the field's
 * more/fewer labels (e.g. "After"/"Before") plus "Exactly", instead of inserting directly.
 * Date fields ([LibrarySearchFieldOption.opensDatePicker]) also get a "Date range" item, which
 * inserts both bounds (`added>start, added<end`) via [onSelectDateRange] instead of a single value.
 */
@Composable
private fun LibrarySearchComparisonChip(
    field: LibrarySearchFieldOption,
    containerColor: Color,
    labelColor: Color,
    onSelect: (separator: String) -> Unit,
    onSelectDateRange: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        LibrarySearchFieldChip(
            field = field,
            containerColor = containerColor,
            labelColor = labelColor,
            onClick = { expanded = true },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.moreLabelRes?.let { moreRes ->
                DropdownMenuItem(
                    text = { Text(stringResource(moreRes)) },
                    onClick = {
                        expanded = false
                        onSelect(">")
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_exactly)) },
                onClick = {
                    expanded = false
                    onSelect("=")
                },
            )
            field.lessLabelRes?.let { lessRes ->
                DropdownMenuItem(
                    text = { Text(stringResource(lessRes)) },
                    onClick = {
                        expanded = false
                        onSelect("<")
                    },
                )
            }
            if (field.opensDatePicker) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_between)) },
                    onClick = {
                        expanded = false
                        onSelectDateRange()
                    },
                )
            }
        }
    }
}

/**
 * A field with a fixed, known set of values, rendered as a [LibrarySearchFieldChip] with a
 * dropdown-indicator trailing icon. Tapping it opens a [DropdownMenu] listing [options].
 * Selecting one reports the complete value via [onSelectValue].
 */
@Composable
private fun LibrarySearchValueDropdownChip(
    field: LibrarySearchFieldOption,
    containerColor: Color,
    labelColor: Color,
    options: List<String>,
    onSelectValue: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        LibrarySearchFieldChip(
            field = field,
            containerColor = containerColor,
            labelColor = labelColor,
            onClick = { expanded = true },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        expanded = false
                        onSelectValue(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibrarySearchFieldChip(
    field: LibrarySearchFieldOption,
    containerColor: Color,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(field.labelRes), style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(field.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = trailingIcon,
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = containerColor,
                labelColor = labelColor,
                leadingIconContentColor = labelColor,
                trailingIconContentColor = labelColor,
            ),
        elevation = AssistChipDefaults.assistChipElevation(elevation = 2.dp),
        border = null,
        modifier = modifier,
    )
}

/**
 * Blends [MaterialTheme.colorScheme.primary]'s hue against the theme background's saturation and
 * a light/dark-tuned lightness, the same HSL approach MangaHeaderHolder.setGenreTags uses for
 * genre chips - rather than a flat alpha overlay, this keeps hue-appropriate contrast in both
 * light and dark themes.
 *
 * @return container color to label/icon color.
 */
@Composable
private fun rememberLibraryChipColors(): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    return remember(background, accent, dark) {
        val bgHsl = FloatArray(3)
        val accentHsl = FloatArray(3)
        ColorUtils.colorToHSL(background.toArgb(), bgHsl)
        ColorUtils.colorToHSL(accent.toArgb(), accentHsl)

        val containerColor =
            Color(
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        accentHsl[0],
                        bgHsl[1],
                        when {
                            dark -> 0.225f
                            else -> 0.85f
                        },
                    ),
                ),
            )

        val labelColor =
            Color(
                ColorUtils.HSLToColor(
                    floatArrayOf(accentHsl[0], accentHsl[1], if (dark) 0.945f else 0.175f),
                ),
            )

        containerColor to labelColor
    }
}

@Preview(name = "Default theme", showBackground = true)
@Composable
private fun LibrarySearchFilterSheetContentPreview() {
    AppComposeTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            LibrarySearchFilterSheetContent(seriesTypeOptions = listOf("Manga", "Manhua"))
        }
    }
}

@Preview(name = "Default theme (Night)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LibrarySearchFilterSheetContentNightPreview() {
    AppComposeTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            LibrarySearchFilterSheetContent(showOptional = false)
        }
    }
}

@Preview(name = "Tokyo Night theme", showBackground = true)
@Composable
private fun LibrarySearchFilterSheetContentTokyoNightPreview() {
    val context = LocalContext.current
    val themedContext = remember(context) { ContextThemeWrapper(context, R.style.Theme_Tachiyomi_TokyoNight) }
    CompositionLocalProvider(LocalContext provides themedContext) {
        AppComposeTheme {
            Surface(color = MaterialTheme.colorScheme.surface) {
                LibrarySearchFilterSheetContent()
            }
        }
    }
}

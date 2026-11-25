package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga

@Composable
internal fun InkShelf2CreateEventDialog(onDismiss: () -> Unit, onCreated: (InkShelf2Guide) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("Event name") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(1500) }, label = { Text("Description (optional)") }, maxLines = 4)
                Text("Add issues by long-pressing them in comic details. Set the artwork from the reader.")
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            try { onCreated(InkShelf2PersonalEvents.create(context, name, description)) }
            catch (_: Exception) { Toast.makeText(context, "Could not create event", Toast.LENGTH_LONG).show() }
        }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun InkShelf2AddToEventDialog(manga: Manga, chapter: Chapter, onDismiss: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf(InkShelf2PersonalEvents.catalogue(context)) }
    var selected by remember { mutableStateOf<InkShelf2Guide?>(null) }
    var create by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf("") }
    var essential by remember { mutableStateOf(true) }
    var replacement by remember { mutableStateOf<Int?>(null) }
    var collectedMode by remember { mutableStateOf(false) }
    var collectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val guide = selected
    val order = remember(guide) { guide?.let { InkShelf2PersonalEvents.order(context, it) }.orEmpty() }
    if (create) {
        InkShelf2CreateEventDialog(onDismiss = { create = false }, onCreated = {
            events = InkShelf2PersonalEvents.catalogue(context)
            selected = it
            position = "1"
            replacement = null
            create = false
        })
    } else AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (guide == null) "Add to Event" else guide.name) },
        text = {
            if (guide == null) {
                Column {
                    TextButton(onClick = { create = true }) { Text("+ Create Event") }
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(events, key = { it.id }) { event ->
                            TextButton(onClick = {
                                selected = event
                                position = (InkShelf2PersonalEvents.order(context, event).size + 1).toString()
                                replacement = null
                                collectedMode = false
                                collectedIds = emptySet()
                                essential = true
                            }) { Text(event.name) }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(manga.title + " · " + chapter.name)
                    if (order.isNotEmpty()) TextButton(onClick = {
                        collectedMode = !collectedMode
                        collectedIds = emptySet()
                        replacement = null
                    }) { Text(if (collectedMode) "Insert or replace a missing issue instead" else "Replace multiple issues with this collection") }
                    if (collectedMode) {
                        Text("Select every issue included in this collected edition. It will be inserted at the first selected position.")
                        order.forEach { entry ->
                            Row {
                                Checkbox(checked = entry.id in collectedIds, onCheckedChange = { checked ->
                                    collectedIds = if (checked) collectedIds + entry.id else collectedIds - entry.id
                                    essential = order.any { it.id in collectedIds && it.isEssential }
                                })
                                Text("${entry.position}. ${entry.displayName}", Modifier.padding(top = 12.dp))
                            }
                        }
                    } else {
                    Text("Insert before the selected position, or at the end.")
                    OutlinedTextField(position, { position = it.filter(Char::isDigit).take(5); replacement = null },
                        label = { Text("Position (1–" + (order.size + 1) + ")") }, singleLine = true,
                        enabled = replacement == null)
                    val pos = position.toIntOrNull()
                    Text(if (replacement != null) "Replacing missing step " + order.firstOrNull { it.id == replacement }?.position
                        else if (pos == order.size + 1) "Append to end" else order.getOrNull((pos ?: 0) - 1)?.let { "Before: " + it.displayName }.orEmpty())
                    if (replacement != null) TextButton(onClick = { replacement = null }) { Text("Insert instead") }
                    order.filter { it.sourceUnavailable }.forEach { missing ->
                        TextButton(onClick = {
                            replacement = missing.id
                            position = missing.position.toString()
                            essential = missing.isEssential
                        }) { Text((if (replacement == missing.id) "✓ " else "") + "Replace " + missing.position + ": " + missing.displayName) }
                    }
                    }
                    Row {
                        Checkbox(checked = essential, onCheckedChange = { essential = it })
                        Text(if (essential) "Essential" else "Tie-in", modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        },
        confirmButton = { if (guide != null) TextButton(
            enabled = if (collectedMode) collectedIds.isNotEmpty()
                else replacement != null || (position.toIntOrNull()?.let { it in 1..(order.size + 1) } == true),
            onClick = {
                try {
                    InkShelf2PersonalEvents.add(context, guide, manga, chapter, position.toIntOrNull() ?: 1,
                        replacement, essential, if (collectedMode) collectedIds else emptySet())
                    Toast.makeText(context, "Issue added to " + guide.name, Toast.LENGTH_SHORT).show()
                    onAdded()
                } catch (_: Exception) { Toast.makeText(context, "Could not add issue (it may already be included)", Toast.LENGTH_LONG).show() }
            }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { if (guide != null) selected = null else onDismiss() }) { Text(if (guide != null) "Back" else "Cancel") } })
}

@Composable
internal fun InkShelf2EditEventDialog(guide: InkShelf2Guide, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var order by remember(guide.id) { mutableStateOf(InkShelf2PersonalEvents.order(context, guide)) }
    var selectedIds by remember(guide.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    if (confirmRemove || confirmRestore) {
        AlertDialog(onDismissRequest = { confirmRemove = false; confirmRestore = false },
            title = { Text(if (confirmRestore) "Restore original reading order?" else "Remove selected issues?") },
            text = { Text("Only the event reading order changes. Comics, downloads and actual chapter progress are kept.") },
            confirmButton = { TextButton(onClick = {
                try {
                    if (confirmRestore) InkShelf2PersonalEvents.restoreOriginal(context, guide)
                    else InkShelf2PersonalEvents.removeIssues(context, guide, selectedIds)
                    order = InkShelf2PersonalEvents.order(context, guide)
                    selectedIds = emptySet()
                    onChanged()
                } catch (_: Exception) { Toast.makeText(context, "Could not update order", Toast.LENGTH_LONG).show() }
                confirmRemove = false; confirmRestore = false
            }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false; confirmRestore = false }) { Text("Cancel") } })
        return
    }
    fun edit(issue: InkShelf2GuideIssue, move: Int = 0, remove: Boolean = false, toggle: Boolean = false) {
        try {
            InkShelf2PersonalEvents.edit(context, guide, issue.id, move, remove, toggle)
            order = InkShelf2PersonalEvents.order(context, guide)
            onChanged()
        } catch (_: Exception) { Toast.makeText(context, "Could not edit order", Toast.LENGTH_LONG).show() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Reading Order") },
        text = {
            Column {
                if (guide.batcaveSlug != null) TextButton(onClick = { confirmRestore = true }) { Text("Restore original order") }
                if (selectedIds.isNotEmpty()) TextButton(onClick = { confirmRemove = true }) { Text("Remove selected (${selectedIds.size})") }
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                items(order, key = { it.id }) { issue ->
                    Column {
                        Row {
                            Checkbox(checked = issue.id in selectedIds, onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + issue.id else selectedIds - issue.id
                            })
                            Text(issue.position.toString() + ". " + issue.displayName, Modifier.padding(top = 12.dp))
                        }
                        Row {
                            TextButton(enabled = issue.position > 1, onClick = { edit(issue, -1) }) { Text("↑") }
                            TextButton(enabled = issue.position < order.size, onClick = { edit(issue, 1) }) { Text("↓") }
                            TextButton(onClick = { edit(issue, toggle = true) }) { Text(if (issue.isEssential) "Essential" else "Tie-in") }
                            TextButton(onClick = { selectedIds = setOf(issue.id); confirmRemove = true }) { Text("Remove") }
                        }
                        HorizontalDivider()
                    }
                }
            }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

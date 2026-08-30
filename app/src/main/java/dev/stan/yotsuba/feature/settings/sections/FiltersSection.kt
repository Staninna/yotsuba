package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SwipeToDeleteRow
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.filter.FilterMatcher
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.FilterField
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.insertFilter
import dev.stan.yotsuba.domain.model.removeFilter
import dev.stan.yotsuba.domain.model.setFilterEnabled
import dev.stan.yotsuba.domain.model.upsertFilter
import dev.stan.yotsuba.feature.settings.labelRes
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * The user's content filters: one row each, swipe to delete (with undo), tap to edit, and an
 * add button at the bottom. Edits go through [update] so the catalog picks them up live.
 */
@Composable
fun FiltersSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
    snackbar: SnackbarHostState,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.filters_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    // Only the id survives rotation; the Filter itself is looked up (or, for an unsaved draft,
    // rebuilt empty) so the dialog can stay open across configuration changes.
    /** null = closed; a fresh id = adding; an existing id = editing. */
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    if (settings.filters.isEmpty()) {
        Text(
            stringResource(R.string.filters_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
        )
    }
    settings.filters.forEachIndexed { index, filter ->
        // Keyed by id so a deleted row's swipe state does not leak onto its successor.
        key(filter.id) {
            SwipeToDeleteRow(onDelete = {
                update { it.removeFilter(filter.id) }
                scope.launch {
                    snackbar.showUndo(deletedMessage, undoLabel) { update { it.insertFilter(index, filter) } }
                }
            }) {
                FilterRow(
                    filter = filter,
                    onClick = { editingId = filter.id },
                    onToggle = { on -> update { it.setFilterEnabled(filter.id, on) } },
                )
            }
        }
    }
    TextButton(
        onClick = { editingId = UUID.randomUUID().toString() },
        modifier = Modifier.padding(horizontal = spacing.md),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(spacing.sm))
        Text(stringResource(R.string.filters_add))
    }

    editingId?.let { id ->
        val existing = settings.filters.firstOrNull { it.id == id }
        FilterDialog(
            initial = existing ?: Filter(id = id, pattern = ""),
            isNew = existing == null,
            onDismiss = { editingId = null },
            onSave = { saved ->
                update { it.upsertFilter(saved) }
                editingId = null
            },
        )
    }
}

@Composable
private fun FilterRow(filter: Filter, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val spacing = LocalSpacing.current
    // Surface so the red swipe background stays hidden until the row actually moves.
    Surface {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    filter.pattern,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = buildList {
                    add(stringResource(filter.field.labelRes))
                    add(stringResource(filter.action.labelRes))
                    if (filter.isRegex) add(stringResource(R.string.filters_regex))
                    add(
                        if (filter.boards.isEmpty()) stringResource(R.string.filters_all_boards)
                        else filter.boards.joinToString(" ") { "/$it/" },
                    )
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val error = remember(filter.pattern, filter.isRegex) { filter.error() }
                if (error != null) {
                    Text(
                        stringResource(R.string.filters_invalid_regex),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(checked = filter.enabled, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    initial: Filter,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Filter) -> Unit,
) {
    val spacing = LocalSpacing.current
    // Saveable so a rotation mid-edit keeps what was typed; enums ride along as Serializable.
    var pattern by rememberSaveable { mutableStateOf(initial.pattern) }
    var isRegex by rememberSaveable { mutableStateOf(initial.isRegex) }
    var field by rememberSaveable { mutableStateOf(initial.field) }
    var boards by rememberSaveable { mutableStateOf(initial.boards.joinToString(", ")) }
    var action by rememberSaveable { mutableStateOf(initial.action) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var sample by rememberSaveable { mutableStateOf("") }

    val draft = initial.copy(
        pattern = pattern,
        isRegex = isRegex,
        field = field,
        boards = boards.split(',', ' ', '/').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        action = action,
        enabled = enabled,
    )
    val regexError = remember(pattern, isRegex) { draft.error() }
    val canSave = pattern.isNotBlank() && regexError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.filters_add else R.string.filters_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.filters_pattern)) },
                    isError = regexError != null,
                    supportingText = regexError?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    title = stringResource(R.string.filters_regex),
                    summary = stringResource(R.string.filters_regex_summary),
                    checked = isRegex,
                    onToggle = { isRegex = it },
                )
                EnumDropdown(
                    label = stringResource(R.string.filters_field),
                    value = field,
                    values = FilterField.entries,
                    labelOf = { stringResource(it.labelRes) },
                    onSelect = { field = it },
                )
                OutlinedTextField(
                    value = boards,
                    onValueChange = { boards = it },
                    label = { Text(stringResource(R.string.filters_boards)) },
                    placeholder = { Text(stringResource(R.string.filters_boards_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                )
                Row(
                    modifier = Modifier.padding(top = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    FilterAction.entries.forEach { a ->
                        FilterChip(
                            selected = action == a,
                            onClick = { action = a },
                            label = { Text(stringResource(a.labelRes)) },
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.filters_enabled),
                    summary = null,
                    checked = enabled,
                    onToggle = { enabled = it },
                )
                Spacer(Modifier.height(spacing.sm))
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = { Text(stringResource(R.string.filters_test)) },
                    supportingText = {
                        if (sample.isNotEmpty() && pattern.isNotEmpty()) {
                            val hit = FilterMatcher.test(pattern, isRegex, sample)
                            Text(
                                stringResource(if (hit) R.string.filters_test_match else R.string.filters_test_no_match),
                                color = if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(draft) }, enabled = canSave) {
                Text(stringResource(R.string.filters_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    value: T,
    values: List<T>,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val spacing = LocalSpacing.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(vertical = spacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(labelOf(value), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { v ->
                DropdownMenuItem(text = { Text(labelOf(v)) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

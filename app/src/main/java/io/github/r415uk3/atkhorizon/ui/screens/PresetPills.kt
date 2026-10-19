package io.github.r415uk3.atkhorizon.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.data.Preset
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.glass.PillShape
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

/** Localized name resource for a built-in preset's [key] (its `Preset.name`); null for custom names. */
@StringRes
internal fun builtInNameRes(key: String): Int? = when (key) {
    "preset_default" -> R.string.preset_default
    "preset_custom1" -> R.string.preset_custom1
    "preset_cs2" -> R.string.preset_cs2
    "preset_rnb" -> R.string.preset_rnb
    "preset_valorant" -> R.string.preset_valorant
    "preset_hiphop" -> R.string.preset_hiphop
    "preset_rock" -> R.string.preset_rock
    "preset_delta" -> R.string.preset_delta
    "preset_apex" -> R.string.preset_apex
    else -> null
}

@Composable
private fun presetLabel(p: Preset): String =
    if (p.builtIn) builtInNameRes(p.name)?.let { stringResource(it) } ?: p.name else p.name

/**
 * Horizontal row of preset pills: "Custom" (filled, only while the curve matches no preset), the
 * presets (active one filled), and "+" to save the current curve. Long-press on a custom preset
 * opens rename/delete. Nothing reacts while [enabled] is false.
 */
@Composable
fun PresetPills(
    presets: List<Preset>,
    activePresetId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onSave: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var saving by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activePresetId == null) Pill(stringResource(R.string.preset_custom_curve), on = true, enabled = false, onClick = {})
        presets.forEach { p ->
            key(p.id) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    Pill(
                        presetLabel(p),
                        on = p.id == activePresetId,
                        enabled = enabled,
                        onClick = { onSelect(p.id) },
                        onLongClick = if (p.builtIn) null else ({ menu = true }),
                    )
                    DropdownMenu(expanded = menu && enabled, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.preset_rename)) },
                            onClick = { menu = false; renaming = p.id },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.preset_delete)) },
                            onClick = { menu = false; deleting = p.id },
                        )
                    }
                }
            }
        }
        val saveLabel = stringResource(R.string.preset_save_as)
        Pill(
            "+", on = false, enabled = enabled, onClick = { saving = true },
            modifier = Modifier.semantics { contentDescription = saveLabel },
        )
    }

    if (saving && enabled) {
        NameDialog(
            title = stringResource(R.string.preset_name),
            initial = "",
            onConfirm = { saving = false; onSave(it) },
            onDismiss = { saving = false },
        )
    }
    presets.firstOrNull { it.id == renaming && !it.builtIn }?.let { p ->
        if (!enabled) return@let
        NameDialog(
            title = stringResource(R.string.preset_rename),
            initial = p.name,
            onConfirm = { renaming = null; onRename(p.id, it) },
            onDismiss = { renaming = null },
        )
    }
    presets.firstOrNull { it.id == deleting && !it.builtIn }?.let { p ->
        if (!enabled) return@let
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.preset_delete_confirm, p.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDelete(p.id) }) { Text(stringResource(R.string.preset_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Pill(
    text: String,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val c = LocalHorizonColors.current
    val content = @Composable {
        Text(
            text,
            color = if (on) c.onAccent else c.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
    if (on) {
        Box(modifier.clip(PillShape).background(c.accent)) { content() }
    } else {
        GlassSurface(modifier, shape = PillShape) { content() }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                singleLine = true,
                label = { Text(stringResource(R.string.preset_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_NAME = 40

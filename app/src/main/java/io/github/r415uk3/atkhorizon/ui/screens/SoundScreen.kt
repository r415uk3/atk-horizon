package io.github.r415uk3.atkhorizon.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.r415uk3.atkhorizon.data.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.ui.ConnState
import io.github.r415uk3.atkhorizon.ui.HorizonViewModel
import io.github.r415uk3.atkhorizon.ui.UiState
import io.github.r415uk3.atkhorizon.ui.eq.EqCurve
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

/**
 * Preset pills over the "Equalizer" card. Editing works only when the device is Ready; while
 * Loading the curve is shown read-only. Tapping a knob calls [onEditBand] (the shell shows the
 * band sheet).
 */
@Composable
fun SoundScreen(state: UiState, vm: HorizonViewModel, onEditBand: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalHorizonColors.current
    val ready = state.conn == ConnState.Ready
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.import_default_name)
    val addLabel = stringResource(R.string.preset_add)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) { readImportFile(context.contentResolver, uri) }
            when (read) {
                is ImportResult.Failed -> vm.importFailed(read.error)
                is ImportResult.Ok -> vm.importPreset(read.preset.name.ifBlank { defaultName }, read.preset.bands)
            }
        }
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetPills(
            presets = state.presets,
            activePresetId = state.activePresetId,
            enabled = ready,
            onSelect = vm::selectPreset,
            onSave = vm::saveAsPreset,
            addLabel = addLabel,
            onImport = { picker.launch(IMPORT_MIME_TYPES) },
            onRename = vm::renamePreset,
            onDelete = vm::deletePreset,
        )
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.eq_title),
                        color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    if (state.busy || state.conn == ConnState.Loading) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = c.muted, strokeWidth = 2.dp)
                    }
                }
                EqCurve(
                    bands = state.bands,
                    enabled = ready,
                    onDrag = vm::onBandDrag,
                    onDragEnd = vm::onBandDragEnd,
                    onTap = onEditBand,
                )
            }
        }
    }
}

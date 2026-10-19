package io.github.r415uk3.atkhorizon.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import io.github.r415uk3.atkhorizon.R
import io.github.r415uk3.atkhorizon.ui.ConnState
import io.github.r415uk3.atkhorizon.ui.glass.GlassSurface
import io.github.r415uk3.atkhorizon.ui.glass.PillShape
import io.github.r415uk3.atkhorizon.ui.theme.LocalHorizonColors

/** What a non-ready connection state shows: title (with optional format arg), body, action. */
internal data class StatusContent(
    @StringRes val title: Int,
    val titleArg: String? = null,
    @StringRes val body: Int,
    @StringRes val action: Int? = null,
)

/** Null for Ready/Loading, which show the main screens instead. */
internal fun statusContent(conn: ConnState): StatusContent? = when (conn) {
    ConnState.NoDevice -> StatusContent(R.string.status_no_device_title, body = R.string.status_no_device_body)
    is ConnState.Unsupported -> StatusContent(
        R.string.status_unsupported_title, "%04X".format(conn.pid), R.string.status_unsupported_body,
    )
    ConnState.NeedsPermission -> StatusContent(
        R.string.status_needs_permission_title, body = R.string.status_needs_permission_body,
        action = R.string.status_needs_permission_action,
    )
    ConnState.PermissionDenied -> StatusContent(
        R.string.status_denied_title, body = R.string.status_denied_body, action = R.string.status_denied_action,
    )
    ConnState.Disconnected -> StatusContent(R.string.status_disconnected_title, body = R.string.status_disconnected_body)
    ConnState.Loading, ConnState.Ready -> null
}

/** A centred glass card for a connection state that has no device to edit. */
@Composable
fun StatusScreen(conn: ConnState, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val content = statusContent(conn) ?: return
    val c = LocalHorizonColors.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (content.titleArg != null) stringResource(content.title, content.titleArg) else stringResource(content.title),
                    color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(stringResource(content.body), color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                content.action?.let { action ->
                    Text(
                        stringResource(action),
                        color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(PillShape)
                            .background(c.accent)
                            .clickable(role = Role.Button, onClick = onAction)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

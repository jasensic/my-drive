package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.ShareGrant
import com.jasensic.mydrive.domain.SharePermission
import com.jasensic.mydrive.domain.UserProfile
import com.jasensic.mydrive.domain.wireValue

@Composable
private fun SheetAction(icon: ImageVector, title: String, divider: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    GroupedRow(
        title = title,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        onClick = onClick,
        divider = divider,
        destructive = destructive,
    )
}

/** What you can do with the selected items, as a list of actions rather than a pile of buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryActionSheet(
    count: Int,
    canRename: Boolean,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onShareWithAccount: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { SheetGrabber(Modifier.padding(vertical = 10.dp)) },
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (count == 1) "1 item" else "$count items",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            val queue = listOfNotNull(
                onPlayNext?.let { Triple(Icons.Outlined.QueuePlayNext, "Play next", it) },
                onAddToQueue?.let { Triple(Icons.Outlined.PlaylistAdd, "Add to queue", it) },
            )
            if (queue.isNotEmpty()) {
                GroupedSection {
                    queue.forEachIndexed { index, (icon, title, action) ->
                        SheetAction(icon, title, divider = index != queue.lastIndex, onClick = action)
                    }
                }
            }
            GroupedSection {
                if (canRename) SheetAction(Icons.Outlined.Edit, "Rename", onClick = onRename)
                SheetAction(Icons.Outlined.DriveFileMove, "Move to album", onClick = onMove)
                SheetAction(Icons.Outlined.Share, "Share with another app", onClick = onShare)
                SheetAction(Icons.Outlined.PersonAdd, "Share with account", divider = false, onClick = onShareWithAccount)
            }
            GroupedSection {
                SheetAction(Icons.Outlined.Delete, "Remove from this device", divider = false, destructive = true, onClick = onDelete)
            }
        }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    confirmLabel: String = "Save",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .imePadding(),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) { inner() }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.bodyLarge) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveAlbumDialog(
    albums: List<Album>,
    onMove: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextPromptDialog(
            title = "New album",
            initial = "",
            confirmLabel = "Create",
            onConfirm = onCreate,
            onDismiss = { creating = false },
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { SheetGrabber(Modifier.padding(vertical = 10.dp)) },
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Move to album", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            GroupedSection {
                SheetAction(Icons.Outlined.FolderOff, "No album", divider = albums.isNotEmpty()) { onMove(null) }
                albums.forEachIndexed { index, album ->
                    SheetAction(Icons.Outlined.Folder, album.name, divider = index != albums.lastIndex) { onMove(album.id) }
                }
            }
            GroupedSection {
                SheetAction(Icons.Outlined.CreateNewFolder, "New album…", divider = false) { creating = true }
            }
        }
    }
}

@Composable
fun ShareWithUserDialog(
    title: String,
    users: List<UserProfile>,
    grants: List<ShareGrant>,
    canManage: Boolean,
    onShare: (String, SharePermission) -> Unit,
    onRevoke: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var granteeId by remember { mutableStateOf(users.firstOrNull()?.id.orEmpty()) }
    var write by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.navigationBarsPadding()) {
                Text(
                    if (canManage) "People you share with can browse this on the server." else "Shared with you.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (canManage && users.isNotEmpty()) {
                    users.forEach { user ->
                        TextButton(onClick = { granteeId = user.id }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (user.id == granteeId) "✓ ${user.username}" else user.username)
                        }
                    }
                    TextButton(onClick = { write = !write }) {
                        Text(if (write) "Permission: write" else "Permission: read")
                    }
                } else if (canManage) {
                    Text("No other accounts on this server yet.")
                }
                if (grants.isNotEmpty()) {
                    Text("Already shared", style = MaterialTheme.typography.titleSmall)
                    grants.forEach { grant ->
                        TextButton(
                            onClick = { if (canManage) onRevoke(grant.id) },
                            enabled = canManage,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${grant.granteeUsername} · ${grant.permission.wireValue()}${if (canManage) " · Unshare" else ""}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canManage) {
                TextButton(
                    onClick = {
                        onShare(granteeId, if (write) SharePermission.WRITE else SharePermission.READ)
                    },
                    enabled = granteeId.isNotBlank(),
                ) { Text("Share") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

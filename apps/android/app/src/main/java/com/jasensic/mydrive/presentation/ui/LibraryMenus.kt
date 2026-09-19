package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.Album

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryActionSheet(
    count: Int,
    canRename: Boolean,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Text(
                if (count == 1) "1 item" else "$count items",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (canRename) {
                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) { Text("Rename") }
            }
            TextButton(onClick = onMove, modifier = Modifier.fillMaxWidth()) { Text("Move / album") }
            TextButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Share") }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
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
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .imePadding(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to album") },
        text = {
            Column(
                Modifier.navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onMove(null) }) { Text("No album") }
                albums.forEach { album ->
                    TextButton(onClick = { onMove(album.id) }) { Text(album.name) }
                }
                TextButton(onClick = { creating = true }) { Text("Create album") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

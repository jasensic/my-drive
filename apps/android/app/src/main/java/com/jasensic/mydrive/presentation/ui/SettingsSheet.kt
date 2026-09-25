package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.presentation.UiState

@Composable
fun SettingsPage(
    state: UiState,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onSync: () -> Unit,
    onUpdate: () -> Unit,
    onConnection: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Server ${state.serverLabel}", style = MaterialTheme.typography.bodyMedium)
        if (state.username.isNotBlank()) {
            Text("Signed in as ${state.username}", style = MaterialTheme.typography.bodySmall)
        }
        Text("App ${state.appVersion} · last sync ${state.lastSync ?: "never"}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Sync runs in the background on this Wi-Fi. You can also start it now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = themeMode == ThemeMode.SYSTEM, onClick = { onTheme(ThemeMode.SYSTEM) }, label = { Text("System") })
            FilterChip(selected = themeMode == ThemeMode.LIGHT, onClick = { onTheme(ThemeMode.LIGHT) }, label = { Text("Light") })
            FilterChip(selected = themeMode == ThemeMode.DARK, onClick = { onTheme(ThemeMode.DARK) }, label = { Text("Dark") })
        }
        state.availableUpdate?.let { release ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Update available: ${release.versionName}", style = MaterialTheme.typography.titleSmall)
                    if (release.changelog.isNotBlank()) {
                        Text(release.changelog, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onUpdate, enabled = !state.isBusy) { Text("Install update") }
                }
            }
        }
        SyncStatusBar(state)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSync, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
            Text("Sync now")
        }
        OutlinedButton(onClick = onConnection, modifier = Modifier.fillMaxWidth()) {
            Text("Connection and sign-in")
        }
        if (state.loggedIn) {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

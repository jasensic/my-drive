package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.presentation.UiState

@Composable
fun ConnectScreen(
    state: UiState,
    onSignIn: (String, String, String) -> Unit,
    onScanLan: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("my-drive", style = MaterialTheme.typography.displaySmall)
        Text(
            "On-premise media on your Wi-Fi. Sign in once, then the phone finds the server by itself.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Server: ${state.serverLabel}", style = MaterialTheme.typography.bodyMedium)
                Text("Last sync: ${state.lastSync ?: "never"}", style = MaterialTheme.typography.bodySmall)
                Text("App ${state.appVersion}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    host,
                    { host = it },
                    label = { Text("Host (optional, e.g. 192.168.1.10:8080)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (state.loggedIn) {
                    Text(
                        "You are already signed in. Keep the phone on the same Wi-Fi: the app scans `_mydrive._tcp` and syncs without asking for a password again.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { onScanLan(host) }, enabled = state.progress == null, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan LAN and sync")
                    }
                } else {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = { onSignIn(user, pass, host) },
                        enabled = state.progress == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign in and download")
                    }
                }
            }
        }
        if (state.files.isNotEmpty()) {
            TextButton(onClick = onOpenLibrary) { Text("Open library (${state.files.size} files)") }
        }
        state.progress?.let {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

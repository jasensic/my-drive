package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.presentation.UiState

/** The content of the settings sheet: grouped sections, one decision per row. */
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
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        ErrorBanner(state.error)

        state.availableUpdate?.let { release ->
            GroupedSection(
                title = "Update",
                footer = release.changelog.takeIf { it.isNotBlank() },
            ) {
                GroupedRow(
                    title = "Version ${release.versionName} is ready",
                    subtitle = "Downloads from your server",
                    divider = false,
                )
                GroupedRow(
                    title = "Install update",
                    onClick = onUpdate,
                    enabled = !state.isBusy,
                    divider = false,
                )
            }
        }

        GroupedSection(title = "Sync", footer = "Sync runs in the background whenever this phone joins your Wi-Fi.") {
            GroupedRow(
                title = "Sync now",
                onClick = onSync,
                enabled = !state.isBusy,
                trailing = {
                    if (state.isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primary)
                },
            )
            GroupedRow(
                title = "Last sync",
                trailing = { ValueText(formatLocalInstant(state.lastSync).ifBlank { "Never" }) },
                divider = false,
            )
        }

        GroupedSection(title = "Appearance") {
            GroupedRow(
                title = "Theme",
                divider = false,
                trailing = {
                    SegmentedControl(
                        options = listOf("Auto", "Light", "Dark"),
                        selectedIndex = themeMode.ordinal,
                        onSelect = { onTheme(ThemeMode.entries[it]) },
                        modifier = Modifier.padding(start = 12.dp).fillMaxWidth(0.62f),
                    )
                },
            )
        }

        GroupedSection(title = "Account") {
            GroupedRow(title = "Server", trailing = { ValueText(state.serverLabel) })
            if (state.username.isNotBlank()) {
                GroupedRow(title = "Signed in as", trailing = { ValueText(state.username) })
            }
            GroupedRow(title = "App version", trailing = { ValueText(state.appVersion) }, divider = false)
        }

        GroupedSection {
            GroupedRow(title = "Connection and sign-in", onClick = onConnection, divider = state.loggedIn)
            if (state.loggedIn) {
                GroupedRow(title = "Sign out", onClick = onSignOut, destructive = true, divider = false)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ValueText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 12.dp),
    )
}

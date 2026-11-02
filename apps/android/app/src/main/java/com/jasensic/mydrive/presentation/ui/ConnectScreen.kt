package com.jasensic.mydrive.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.presentation.UiState
import com.jasensic.mydrive.presentation.theme.Motion
import com.jasensic.mydrive.presentation.theme.pressScale
import com.jasensic.mydrive.presentation.theme.rememberHaptics

@Composable
fun ConnectScreen(
    state: UiState,
    onSignIn: (String, String, String) -> Unit,
    onSetup: (String, String, String) -> Unit,
    onScanLan: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(84.dp)
                .shadow(14.dp, RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.85f), colors.primary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("my-drive", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = state.loggedIn to state.setupRequired,
            transitionSpec = { fadeIn(tween(Motion.Medium)) togetherWith fadeOut(tween(Motion.Fast)) },
            label = "connectSubtitle",
        ) { (loggedIn, setupRequired) ->
            Text(
                when {
                    loggedIn -> "Signed in${state.username.takeIf { it.isNotBlank() }?.let { " as $it" }.orEmpty()}"
                    setupRequired -> "This server needs its first account."
                    else -> "Your files, on your Wi-Fi. Sign in once, then the phone finds the server by itself."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
        Spacer(Modifier.height(28.dp))

        if (state.loggedIn) {
            PrimaryButton("Scan network and sync", enabled = !state.isBusy, busy = state.isBusy) {
                haptics.confirm()
                onScanLan(host)
            }
        } else {
            GroupedSection {
                FieldRow("Username", user, { user = it }, placeholder = "Required", divider = true)
                FieldRow("Password", pass, { pass = it }, password = true, placeholder = "Required", divider = showServer)
                AnimatedVisibility(showServer, enter = expandVertically(Motion.spring()) + fadeIn(), exit = shrinkVertically(tween(Motion.Medium)) + fadeOut()) {
                    FieldRow("Server", host, { host = it }, placeholder = "Automatic", divider = false, keyboard = KeyboardType.Uri)
                }
            }
            Spacer(Modifier.height(20.dp))
            val canSubmit = !state.isBusy && user.isNotBlank() && pass.isNotBlank()
            PrimaryButton(
                label = if (state.setupRequired) "Create admin account" else "Sign in",
                enabled = canSubmit,
                busy = state.isBusy,
            ) {
                haptics.confirm()
                if (state.setupRequired) onSetup(user, pass, host) else onSignIn(user, pass, host)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (showServer) "Hide server address" else "Server address",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showServer = !showServer }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (!state.setupRequired) {
                Text(
                    "No account yet? Ask the person who set up this drive to add you in the portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
                )
            }
        }

        val status = state.statusMessage ?: state.progress
        AnimatedVisibility(state.isBusy && status != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                status.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
        ErrorBanner(state.error, Modifier.padding(top = 8.dp))
        if (state.files.isNotEmpty()) {
            Text(
                "Open library (${state.files.size} files)",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenLibrary)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** Label on the left, borderless input on the right: the iOS sign-in form. */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    placeholder: String = "",
    divider: Boolean,
    keyboard: KeyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (divider) {
                    drawLine(
                        colors.outlineVariant,
                        Offset(16.dp.toPx(), size.height - 0.5f),
                        Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f,
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(92.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}

/** The one filled button on the screen. Shrinks when pressed and shows a spinner while working. */
@Composable
private fun PrimaryButton(label: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        if (enabled) colors.primary else colors.surfaceVariant,
        tween(Motion.Medium),
        label = "primaryButton",
    )
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(source, 0.97f)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = if (enabled) Color.White else colors.onSurfaceVariant)
        } else {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else colors.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

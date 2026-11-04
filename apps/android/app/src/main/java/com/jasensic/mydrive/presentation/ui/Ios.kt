package com.jasensic.mydrive.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasensic.mydrive.domain.SyncPhase
import com.jasensic.mydrive.presentation.HubTab
import com.jasensic.mydrive.presentation.UiState
import com.jasensic.mydrive.presentation.theme.Motion
import com.jasensic.mydrive.presentation.theme.pressScale
import com.jasensic.mydrive.presentation.theme.rememberHaptics

/** A row that darkens while pressed (no ripple), the way iOS lists respond. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rowClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberHaptics()
    val background by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        tween(120),
        label = "rowPress",
    )
    background(background).combinedClickable(
        interactionSource = source,
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick?.let { action ->
            {
                haptics.lift()
                action()
            }
        },
    )
}

/** Filled, borderless search field like the iOS search bar. */
@Composable
fun IosSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    inner()
                }
                AnimatedVisibility(query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.onSurfaceVariant.copy(alpha = 0.5f))
                            .rowClickable({ onQueryChange("") }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = colors.surface, modifier = Modifier.size(14.dp))
                    }
                }
            }
        },
    )
}

/** Two or three exclusive choices with a thumb that slides to the selection. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: List<Boolean> = options.map { true },
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surfaceVariant)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val thumbOffset by animateDpAsState(segment * selectedIndex, Motion.spring(), label = "segmentThumb")
        Box(
            Modifier
                .offset(x = thumbOffset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(colors.surface),
        )
        Row(Modifier.fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val active = index == selectedIndex
                val color by animateColorAsState(
                    when {
                        !enabled[index] -> colors.onSurfaceVariant.copy(alpha = 0.4f)
                        active -> colors.onSurface
                        else -> colors.onSurfaceVariant
                    },
                    tween(Motion.Fast),
                    label = "segmentText",
                )
                Box(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .noRippleClickable(enabled = enabled[index] && !active) {
                            haptics.tick()
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    source: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    clickable(
        interactionSource = source ?: remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/** Capsule used for album filters. */
@Composable
fun IosChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val background by animateColorAsState(if (selected) colors.primary else colors.surfaceVariant, tween(Motion.Fast), label = "chipBg")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, tween(Motion.Fast), label = "chipFg")
    Box(
        modifier
            .pressScale(source)
            .height(32.dp)
            .clip(CircleShape)
            .background(background)
            .noRippleClickable(source = source, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = content, maxLines = 1)
    }
}

/** Rounded container that groups related rows on the page background. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

/** One line in a [GroupedSection]: label on the left, value or control on the right, hairline below. */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    divider: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        destructive -> colors.error
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        onClick != null -> colors.primary
        else -> colors.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.rowClickable(onClick) else Modifier)
            .drawBehind {
                if (divider) {
                    val inset = (if (leading != null) 56.dp else 16.dp).toPx()
                    drawLine(
                        colors.outlineVariant,
                        Offset(inset, size.height - 0.5f),
                        Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f,
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
            }
        }
        if (trailing != null) trailing()
    }
}

/** A small rounded square holding an icon, as in iOS Settings and Files. */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** Sync progress as a small capsule, so syncing never takes over the screen. */
@Composable
fun SyncPill(state: UiState, modifier: Modifier = Modifier) {
    val progress = state.syncProgress
    val showing = progress.isActive || progress.phase == SyncPhase.COMPLETED
    AnimatedVisibility(
        visible = showing,
        modifier = modifier,
        enter = fadeIn(tween(Motion.Fast)) + scaleIn(Motion.spring(), initialScale = 0.8f),
        exit = fadeOut(tween(Motion.Medium)) + scaleOut(tween(Motion.Medium), targetScale = 0.9f),
    ) {
        val done = progress.phase == SyncPhase.COMPLETED
        val label = when {
            done -> "Done"
            progress.phase == SyncPhase.DOWNLOADING && progress.totalFiles > 0 ->
                "${progress.completedFiles.coerceAtMost(progress.totalFiles)}/${progress.totalFiles}"
            else -> "Syncing"
        }
        Row(
            Modifier
                .height(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            } else if (progress.phase == SyncPhase.DOWNLOADING && progress.totalFiles > 0) {
                val fraction by animateFloatAsState(progress.fraction, tween(Motion.Medium), label = "syncFraction")
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            } else {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
        }
    }
}

/** An error that slides in under the header and can be dismissed by tapping it. */
@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        modifier = modifier,
        enter = expandVertically(Motion.spring()) + fadeIn(),
        exit = shrinkVertically(tween(Motion.Medium)) + fadeOut(),
    ) {
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .then(if (onDismiss != null) Modifier.rowClickable(onDismiss) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Three bars that dance while a track plays and rest when it is paused. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val heights = listOf(0.35f to 420, 0.8f to 520, 0.55f to 360).mapIndexed { index, (peak, millis) ->
        val animated by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = peak,
            animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar$index",
        )
        val level by animateFloatAsState(if (playing) animated else 0.2f, tween(Motion.Medium), label = "barLevel$index")
        level
    }
    Row(
        modifier.size(width = 16.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { level ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp * level.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}

/** The three sections of the app. Settings lives in a sheet behind the gear. */
@Composable
fun TabBar(selected: HubTab, onSelect: (HubTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.94f)),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.outlineVariant))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
        ) {
            TabItem("Music", selected == HubTab.MUSIC, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, Modifier.weight(1f)) {
                haptics.tick()
                onSelect(HubTab.MUSIC)
            }
            TabItem("Photos", selected == HubTab.PHOTOS, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, Modifier.weight(1f)) {
                haptics.tick()
                onSelect(HubTab.PHOTOS)
            }
            TabItem("Files", selected == HubTab.FILES, Icons.Filled.Folder, Icons.Outlined.Folder, Modifier.weight(1f)) {
                haptics.tick()
                onSelect(HubTab.FILES)
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    filled: ImageVector,
    outlined: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val color by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, tween(Motion.Fast), label = "tabColor")
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, Motion.spring(), label = "tabScale")
    Column(
        modifier
            .fillMaxHeight()
            .pressScale(source, 0.92f)
            .noRippleClickable(source = source, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) filled else outlined,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(26.dp)
                .scale(scale),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** The grabber on top of sheets and the Now Playing cover. */
@Composable
fun SheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(36.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
    )
}

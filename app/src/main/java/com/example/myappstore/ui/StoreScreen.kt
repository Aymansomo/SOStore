package com.example.myappstore.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.myappstore.R
import com.example.myappstore.domain.VersionUtils
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    uiState: StoreUiState,
    onRefresh: () -> Unit,
    onAction: (String) -> Unit,
    onToggleChangelog: (String) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            )
    ) {
        AmbientGlow()

        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "header") {
                        StoreHeader(uiState = uiState, onRefresh = onRefresh)
                    }

                    uiState.banner?.let { banner ->
                        item(key = "banner") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(250)) + expandVertically(tween(250))
                            ) {
                                StatusBanner(
                                    banner = banner,
                                    onRetry = onRefresh,
                                    onDismiss = onDismissBanner
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = uiState.apps,
                        key = { _, appState -> appState.app.id }
                    ) { index, appState ->
                        RevealItem(index = index) {
                            AppCard(
                                state = appState,
                                onAction = { onAction(appState.app.id) },
                                onToggleChangelog = { onToggleChangelog(appState.app.id) }
                            )
                        }
                    }

                    item(key = "footer") {
                        Text(
                            text = "Releases are read from GitHub. Pull down to check again.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Two soft, slowly drifting colour blobs painted behind the content. */
@Composable
private fun AmbientGlow() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        GlowBlob(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer {
                    translationX = -120f + drift * 90f
                    translationY = -60f + drift * 60f
                    alpha = 0.16f
                }
        )
        GlowBlob(
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(300.dp)
                .graphicsLayer {
                    translationX = 120f - drift * 80f
                    translationY = 260f - drift * 40f
                    alpha = 0.13f
                }
        )
    }
}

@Composable
private fun GlowBlob(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(color, Color.Transparent)
            )
        )
    )
}

@Composable
private fun StoreHeader(uiState: StoreUiState, onRefresh: () -> Unit) {
    val updates = uiState.apps.count { it.action == StoreAction.UPDATE }
    val installed = uiState.apps.count { it.installedVersion != null }

    val spinTransition = rememberInfiniteTransition(label = "spin")
    val angle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_store_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My App Store",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Your apps, direct from GitHub Releases",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !uiState.isRefreshing,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(if (uiState.isRefreshing) angle else 0f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderChip(
                label = "${uiState.apps.size} apps",
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            )
            HeaderChip(
                label = if (updates > 0) "$updates update${if (updates == 1) "" else "s"}" else "All up to date",
                container = if (updates > 0) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                content = if (updates > 0) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            HeaderChip(
                label = "$installed installed",
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HeaderChip(label: String, container: Color, content: Color) {
    val animatedContainer by animateColorAsState(container, tween(500), label = "chipContainer")
    val animatedContent by animateColorAsState(content, tween(500), label = "chipContent")

    Surface(
        color = animatedContainer,
        contentColor = animatedContent,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

/** Fades and lifts a list item into place, staggered by its index. */
@Composable
private fun RevealItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "revealAlpha"
    )
    val offset by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(durationMillis = 380),
        label = "revealOffset"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offset
        }
    ) {
        content()
    }
}

@Composable
private fun AppCard(
    state: AppUiState,
    onAction: () -> Unit,
    onToggleChangelog: () -> Unit
) {
    val context = LocalContext.current
    val installedIcon = remember(state.app.packageName, state.installedVersion) {
        runCatching { context.packageManager.getApplicationIcon(state.app.packageName) }.getOrNull()
    }
    val iconBitmap = remember(installedIcon) {
        installedIcon?.let { drawable ->
            runCatching { drawable.toBitmap(width = 128, height = 128) }
                .getOrNull()
                ?.asImageBitmap()
        }
    }

    val hasUpdate = state.action == StoreAction.UPDATE
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderColor by animateColorAsState(
        targetValue = if (hasUpdate) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = pulseAlpha)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = tween(600),
        label = "borderColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(bitmap = iconBitmap, busy = state.phase != InstallPhase.IDLE)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VersionBadge(state)
                        if (hasUpdate) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = pulseAlpha)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = metaText(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            state.releaseError?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.changelog != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onToggleChangelog,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Text(
                        text = if (state.changelogExpanded) "Hide what's new" else "What's new",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = if (state.changelogExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = state.changelogExpanded,
                    enter = fadeIn(tween(220)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.changelog.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.phase != InstallPhase.IDLE,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    ProgressBar(progress = state.progress)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.statusMessage ?: "Working…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.progress?.let { percent ->
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                ActionButton(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun ActionButton(state: AppUiState, onAction: () -> Unit) {
    val container by animateColorAsState(
        targetValue = when (state.action) {
            StoreAction.INSTALL -> MaterialTheme.colorScheme.primary
            StoreAction.UPDATE -> MaterialTheme.colorScheme.tertiary
            StoreAction.OPEN -> MaterialTheme.colorScheme.secondaryContainer
            StoreAction.WORKING -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(400),
        label = "buttonContainer"
    )
    val content by animateColorAsState(
        targetValue = when (state.action) {
            StoreAction.INSTALL -> MaterialTheme.colorScheme.onPrimary
            StoreAction.UPDATE -> MaterialTheme.colorScheme.onTertiary
            StoreAction.OPEN -> MaterialTheme.colorScheme.onSecondaryContainer
            StoreAction.WORKING -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(400),
        label = "buttonContent"
    )

    Button(
        onClick = onAction,
        enabled = state.isEnabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        modifier = Modifier.widthIn(min = 118.dp)
    ) {
        if (state.action == StoreAction.OPEN) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }

        AnimatedContent(
            targetState = buttonLabel(state),
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically { it / 2 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically { -it / 2 })
            },
            label = "buttonLabel"
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

/** Rounded progress track with a gradient fill and a moving light sweep. */
@Composable
private fun ProgressBar(progress: Int?) {
    val animated by animateFloatAsState(
        targetValue = (progress ?: 0) / 100f,
        animationSpec = tween(durationMillis = 320),
        label = "progressFill"
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val sheen = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
    ) {
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(primary, tertiary)))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.5f), tertiary)))
            )
        }

        Spacer(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val band = size.width * 0.32f
                    val x = -band + (size.width + band) * shimmer
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, sheen, Color.Transparent),
                            start = Offset(x, 0f),
                            end = Offset(x + band, size.height)
                        )
                    )
                }
        )
    }
}

@Composable
private fun AppIcon(bitmap: ImageBitmap?, busy: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (busy) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_app_placeholder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun VersionBadge(state: AppUiState) {
    val installed = state.installedVersion
    val latest = state.latestVersion

    val label: String
    val container: Color
    val content: Color

    when {
        installed == null && latest != null -> {
            label = "Latest v$latest"
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }

        installed == null -> {
            label = if (state.isLoadingRelease) "Checking GitHub…" else "Not installed"
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }

        latest == null -> {
            label = "Installed v$installed"
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }

        VersionUtils.isNewer(latest, installed) -> {
            label = "v$installed  →  v$latest"
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }

        else -> {
            label = "v$installed · Up to date"
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }

    val animatedContainer by animateColorAsState(container, tween(350), label = "badgeContainer")
    val animatedContent by animateColorAsState(content, tween(350), label = "badgeContent")

    Surface(
        color = animatedContainer,
        contentColor = animatedContent,
        shape = RoundedCornerShape(50)
    ) {
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "badgeLabel"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusBanner(
    banner: Banner,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val container = if (banner.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (banner.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (banner.isError) "Something went wrong" else "Done",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(text = banner.message, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (banner.isError) {
                    TextButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.textButtonColors(contentColor = content)
                    ) {
                        Text("Retry")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = content)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

private fun buttonLabel(state: AppUiState): String = when (state.phase) {
    InstallPhase.DOWNLOADING -> state.progress?.let { "Downloading $it%" } ?: "Downloading…"
    InstallPhase.INSTALLING -> "Installing…"
    InstallPhase.IDLE -> state.action.label
}

private fun metaText(state: AppUiState): String {
    val parts = mutableListOf("${state.app.githubOwner}/${state.app.githubRepo}")
    if (state.apkSizeBytes > 0L) {
        parts += "APK ${formatBytes(state.apkSizeBytes)}"
    }
    formatPublishedDate(state.publishedAt)?.let { parts += it }
    return parts.joinToString("  ·  ")
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

private fun formatPublishedDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        Instant.parse(raw)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }.getOrNull()
}

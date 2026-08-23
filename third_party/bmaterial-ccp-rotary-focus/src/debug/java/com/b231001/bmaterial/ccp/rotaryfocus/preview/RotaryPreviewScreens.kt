package com.b231001.bmaterial.ccp.rotaryfocus.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.DirectManipulationConfig
import com.b231001.bmaterial.ccp.rotaryfocus.FocusArea
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaOrientation
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItem
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRenderState
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.LazyFocusScrollBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.LocalIsInTouchMode
import com.b231001.bmaterial.ccp.rotaryfocus.rememberLazyListFocusHandler

@Composable
internal fun PreviewHomeScreen(onOpenList: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.4f) }
    val volumeDirectMode = remember {
        DirectManipulationConfig(
            onRotary = { event ->
                volume = (volume + event.detents * 0.05f).coerceIn(0f, 1f)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewModeHeader(
            title = "Screen 1 · nested FocusItem layout",
            subtitle = "Open Screen 2, open a native rotary dialog, or test Slider Direct Mode."
        )
        FocusArea(
            id = PreviewTargets.homeArea,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            firstFocusAt = PreviewTargets.homeNext,
            focusOrder = listOf(
                PreviewTargets.homeNext,
                PreviewTargets.homeDialog,
                PreviewTargets.homeVolume
            ),
            wrapAround = true
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "FocusItems below are declared inside extracted composables and nested " +
                        "Column/Row nodes.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PreviewFocusButton(
                            id = PreviewTargets.homeNext,
                            label = "Navigate to Screen 2",
                            supportingText = "Center click uses Navigation Compose",
                            onClick = onOpenList
                        )
                        Column(Modifier.padding(start = 28.dp)) {
                            PreviewFocusButton(
                                id = PreviewTargets.homeDialog,
                                label = "Open dialog / popup",
                                supportingText = "Default dialog item receives real View focus",
                                onClick = { showDialog = true }
                            )
                        }
                    }
                    PreviewVolumeItem(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 320.dp),
                        volume = volume,
                        onVolumeChanged = { volume = it },
                        directManipulation = volumeDirectMode
                    )
                }
            }
        }
    }

    if (showDialog) {
        PreviewRotaryDialog(owner = "home", onDismiss = { showDialog = false })
    }
}

@Composable
internal fun PreviewOrderedListScreen(
    onOpenLazyList: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var showDialog by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf("No ordered button clicked yet") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewModeHeader(
            title = "Screen 2 · explicit focus order + auto scroll",
            subtitle = "Physical order is 1, 2, 3. Rotary order is 1 → 3 → 2 → 1."
        )
        Text(lastAction, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FocusArea(
                id = PreviewTargets.orderedActionsArea,
                modifier = Modifier
                    .width(250.dp)
                    .fillMaxHeight(),
                layout = FocusAreaLayout(itemSpacing = 12.dp),
                firstFocusAt = PreviewTargets.orderedBack,
                focusOrder = listOf(
                    PreviewTargets.orderedBack,
                    PreviewTargets.orderedDialog
                ),
                wrapAround = true,
                nextFocusArea = PreviewTargets.orderedListArea
            ) {
                PreviewFocusButton(
                    id = PreviewTargets.orderedBack,
                    label = "Back to Screen 1",
                    supportingText = "Same result as the system Back key",
                    onClick = onBack
                )
                PreviewFocusButton(
                    id = PreviewTargets.orderedDialog,
                    label = "Open Screen 2 dialog",
                    supportingText = "Closing restores this exact item",
                    onClick = { showDialog = true }
                )
            }

            FocusArea(
                id = PreviewTargets.orderedListArea,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                firstFocusAt = PreviewTargets.orderedOne,
                focusOrder = listOf(
                    PreviewTargets.orderedOne,
                    PreviewTargets.orderedThree,
                    PreviewTargets.orderedTwo
                ),
                wrapAround = true,
                previousFocusArea = PreviewTargets.orderedActionsArea
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "All three Views exist. Large spacing forces bring-into-view when " +
                            "rotary order jumps across the physical layout.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    PreviewFocusButton(
                        id = PreviewTargets.orderedOne,
                        label = "Button 1",
                        supportingText = "Next rotary target: Button 3",
                        onClick = { lastAction = "Button 1 clicked" }
                    )
                    Spacer(Modifier.height(170.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PreviewFocusButton(
                            id = PreviewTargets.orderedTwo,
                            label = "Button 2",
                            supportingText = "Next rotary target: Button 1",
                            onClick = { lastAction = "Button 2 clicked" }
                        )
                        Spacer(Modifier.height(110.dp))
                        PreviewFocusButton(
                            id = PreviewTargets.orderedThree,
                            label = "Button 3 · open Screen 3",
                            supportingText = "Next rotary target: Button 2",
                            onClick = onOpenLazyList
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        PreviewRotaryDialog(owner = "ordered", onDismiss = { showDialog = false })
    }
}

@Composable
internal fun PreviewLazyListScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val itemIds = PreviewTargets.lazyItemIds
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val revealHandler = rememberLazyListFocusHandler(
        state = listState,
        itemIds = itemIds,
        scrollBehavior = LazyFocusScrollBehavior.Animated
    )
    var selectedIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PreviewModeHeader(
            title = "Screen 3 · LazyColumn materialization",
            subtitle = "Offscreen items have no View until the bridge scrolls, composes, then " +
                "requests Android View focus. Selected index: $selectedIndex"
        )
        FocusArea(
            id = PreviewTargets.lazyHeaderArea,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            layout = FocusAreaLayout(
                orientation = FocusAreaOrientation.Horizontal,
                itemSpacing = 12.dp
            ),
            firstFocusAt = PreviewTargets.lazyBack,
            focusOrder = listOf(PreviewTargets.lazyBack),
            nextFocusArea = PreviewTargets.lazyListArea
        ) {
            PreviewFocusButton(
                id = PreviewTargets.lazyBack,
                modifier = Modifier.width(280.dp),
                label = "Back to Screen 2",
                supportingText = "Restores Button 3 on Screen 2",
                onClick = onBack
            )
        }
        FocusArea(
            id = PreviewTargets.lazyListArea,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            firstFocusAt = itemIds.first(),
            focusOrder = itemIds,
            onFocusItemUnavailable = revealHandler,
            wrapAround = true,
            previousFocusArea = PreviewTargets.lazyHeaderArea
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(300.dp)
            ) {
                itemsIndexed(
                    items = itemIds,
                    key = { _, id -> id.value }
                ) { index, id ->
                    PreviewFocusButton(
                        id = id,
                        label = "Lazy FocusItem ${index + 1}",
                        supportingText = "Stable id=${id.value}",
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewModeHeader(title: String, subtitle: String) {
    val isInTouchMode: Boolean = LocalIsInTouchMode.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isInTouchMode) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Text(
                text = if (isInTouchMode) "TOUCH MODE" else "ROTARY / FOCUS MODE",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** Extracted composable proving FocusItem no longer needs to be a direct FocusAreaScope child. */
@Composable
internal fun PreviewFocusButton(
    id: FocusItemId,
    label: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusItem(
        id = id,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        semantics = FocusItemSemantics(label = label, role = FocusItemRole.Button),
        layout = FocusItemLayout(fillCrossAxis = false)
    ) { state ->
        PreviewFocusSurface(state = state) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewVolumeItem(
    modifier: Modifier,
    volume: Float,
    onVolumeChanged: (Float) -> Unit,
    directManipulation: DirectManipulationConfig
) {
    FocusItem(
        id = PreviewTargets.homeVolume,
        modifier = modifier,
        directManipulation = directManipulation,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics = FocusItemSemantics(
            label = "Volume",
            stateDescription = "${(volume * 100).toInt()} percent",
            role = FocusItemRole.Adjustable
        ),
        layout = FocusItemLayout(fillCrossAxis = false)
    ) { state ->
        val isInTouchMode = LocalIsInTouchMode.current
        val showFocus = state.isFocused && !isInTouchMode
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (showFocus) 3.dp else 1.dp,
                    color = when {
                        state.isDirectManipulationMode -> MaterialTheme.colorScheme.tertiary
                        showFocus -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (state.isDirectManipulationMode) {
                    "Volume · DIRECT · ${(volume * 100).toInt()}%"
                } else {
                    "Volume · ${(volume * 100).toInt()}%"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Slider(value = volume, onValueChange = onVolumeChanged)
            Text(
                "Center enters Direct Mode; rotate changes 5%; Back exits.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PreviewFocusSurface(
    state: FocusItemRenderState,
    content: @Composable () -> Unit
) {
    val isInTouchMode: Boolean = LocalIsInTouchMode.current
    val showFocus = state.isFocused && !isInTouchMode
    val borderColor = when {
        state.isDirectManipulationMode -> MaterialTheme.colorScheme.tertiary
        showFocus -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = when {
        state.isPressed -> MaterialTheme.colorScheme.primaryContainer
        showFocus -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .border(
                width = if (showFocus) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

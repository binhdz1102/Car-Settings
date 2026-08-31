package com.android.car.settings.core.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.DirectManipulationConfig
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.LocalIsInTouchMode
import com.b231001.bmaterial.ccp.rotaryfocus.LocalRotaryFocusController
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget
import com.b231001.bmaterial.ccp.rotaryfocus.rememberLazyListFocusHandler
import com.b231001.bmaterial.uicomponents.bswitch.BSwitch
import com.b231001.bmaterial.uicomponents.button.BButtonSize
import com.b231001.bmaterial.uicomponents.button.BIconButton
import com.b231001.bmaterial.uicomponents.button.BIconButtonSize
import com.b231001.bmaterial.uicomponents.button.BIconButtonStyle

private val LocalSettingsRotaryArea = staticCompositionLocalOf { false }
private val LocalSettingsFirstFocusRegistration =
    staticCompositionLocalOf<(FocusItemId) -> Unit> { {} }
private val LocalSettingsDestinationKey = staticCompositionLocalOf { "settings" }

/**
 * Standard icon slot for settings rows.  The stock Material icon intrinsic size (24dp) is too
 * small on a 1920x1080 head unit; keeping this helper in core:ui prevents feature rows from
 * silently drifting back to undersized touch/rotary visuals.
 */
@Composable
fun SettingsLeadingIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(SettingsTokens.LeadingIconSize),
        // Untinted icons fall back to LocalContentColor, which stays Color.Black when a raw
        // token-colored ancestor surface skips contentColorFor() — invisible on dark surfaces.
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    @DrawableRes titleIconRes: Int? = null,
    onBack: (() -> Unit)? = null,
    /** Stable route identity; never derive focus IDs from a localized title. */
    destinationKey: String? = null,
    subtitle: String? = null,
    isRoot: Boolean = false,
    @Suppress("UNUSED_PARAMETER") depth: Int = 0,
    /** Stable content FocusItem key to select when this destination is first entered. */
    firstContentFocusId: String? = null,
    /** False while the repository's initial snapshot may still insert an earlier focus row. */
    isContentFocusReady: Boolean = true,
    /** State of a long content list whose off-screen rows must be materialized for CCP focus. */
    lazyListState: LazyListState? = null,
    /** Focus-row key to physical LazyColumn index mapping; excludes non-focusable headers. */
    lazyFocusItemIndexById: Map<String, Int> = emptyMap(),
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Every Settings destination owns Back at the screen boundary. This handler is composed
    // inside NavHost, so it takes precedence over NavHost's generic pop while nested handlers
    // (dialogs, direct manipulation and Vehicle sub-pages) can still consume Back first.
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    val routeKey = destinationKey?.takeIf { it.isNotBlank() } ?: stableRotaryKey(title)
    // Navigation keeps the old and new destination composed for a short frame even when
    // transitions are disabled.  Scope FocusAreas by stable destination identity so that frame
    // cannot register duplicate `settings-header`/`settings-detail` IDs in the CCP host.
    val areaKey = stableRotaryKey(routeKey)
    val appBarAreaId = FocusAreaId("settings-header-$areaKey")
    val contentAreaId = FocusAreaId("settings-detail-$areaKey")
    val hasAppBarFocusItems = onBack?.takeUnless { isRoot } != null || actions != null
    val detailPaneRing =
        remember(appBarAreaId, contentAreaId, hasAppBarFocusItems) {
            settingsFocusAreaRing(
                if (hasAppBarFocusItems) {
                    listOf(appBarAreaId, contentAreaId)
                } else {
                    listOf(contentAreaId)
                },
            )
        }
    val appBarFocusPolicy = detailPaneRing[appBarAreaId]
    val contentFocusPolicy = detailPaneRing.getValue(contentAreaId)
    // Keep materialization state per destination. A root destination entered by CCP must not let
    // B-Material's native FocusArea select a transient fallback row while its requested lazy item
    // is still being composed or while screen data is still loading.
    val contentFocusItemAvailability = remember(routeKey) { mutableStateMapOf<FocusItemId, Boolean>() }
    val onFocusItemAvailability =
        remember(routeKey) {
            { itemId: FocusItemId, isEnabled: Boolean ->
                if (isEnabled) {
                    contentFocusItemAvailability[itemId] = true
                } else {
                    contentFocusItemAvailability.remove(itemId)
                }
                Unit
            }
        }
    val firstContentItemId =
        firstContentFocusId?.let { settingsFocusItemId(routeKey, it) }
    val discoveredFirstContentItemId = remember(routeKey) { mutableStateOf<FocusItemId?>(null) }
    val firstFocusableContentItemId =
        settingsFirstContentItemToFocus(
            isContentFocusReady = isContentFocusReady,
            declaredItemId = firstContentItemId,
            discoveredItemId = discoveredFirstContentItemId.value,
        )
    val contentFallback =
        firstFocusableContentItemId?.let { itemId ->
            RotaryFocusTarget(areaId = contentAreaId, itemId = itemId)
        }
    val backFallback =
        onBack?.takeUnless { isRoot }?.let {
            RotaryFocusTarget(
                areaId = appBarAreaId,
                itemId = FocusItemId("settings-back-$routeKey"),
            )
        }
    val lazyFocusSpec =
        remember(routeKey, lazyFocusItemIndexById) {
            settingsLazyFocusSpec(routeKey, lazyFocusItemIndexById)
        }
    val lazyFocusHandler =
        if (lazyListState != null && lazyFocusSpec.itemIndexById.isNotEmpty()) {
            rememberLazyListFocusHandler(
                state = lazyListState,
                itemIndexById = lazyFocusSpec.itemIndexById,
            )
        } else {
            null
        }
    val focusEntryState = LocalSettingsFocusEntryState.current
    val pendingFocusEntry = focusEntryState?.pendingRequest
    val focusController = LocalRotaryFocusController.current
    val isInTouchMode = LocalIsInTouchMode.current
    SideEffect {
        focusEntryState?.onDestinationVisible(routeKey)
    }
    LaunchedEffect(focusEntryState, pendingFocusEntry?.requestId, routeKey) {
        focusEntryState?.claimPendingFocus(routeKey)
    }
    val ownsFocusHandoff =
        pendingFocusEntry?.let { request -> settingsFocusEntryBelongsTo(request, routeKey) } == true
    val fallback =
        settingsDestinationFallback(
            isRoot = isRoot,
            contentFallback = contentFallback,
            backFallback = backFallback,
            hasExplicitFocusHandoff = ownsFocusHandoff,
        )
    val requestedTargetMaterialized =
        firstFocusableContentItemId?.let { contentFocusItemAvailability[it] == true } == true
    // A handoff remains the sole owner of detail focus until its requested item exists. Once the
    // item materializes, normal FocusArea behavior is restored and the controller delivers the
    // queued request. Cold start has no pending request, so Search remains the initial focus.
    val contentFocusAllowed = !ownsFocusHandoff || requestedTargetMaterialized
    LaunchedEffect(
        focusController,
        focusEntryState,
        pendingFocusEntry?.requestId,
        pendingFocusEntry?.destinationKey,
        routeKey,
        firstFocusableContentItemId,
        requestedTargetMaterialized,
        isInTouchMode,
    ) {
        if (isInTouchMode) {
            focusEntryState?.cancelPendingFocus()
            return@LaunchedEffect
        }
        val request = pendingFocusEntry ?: return@LaunchedEffect
        val target = contentFallback ?: return@LaunchedEffect
        if (focusController == null || request.destinationKey != routeKey) {
            return@LaunchedEffect
        }
        // B-Material queues this request when the destination or lazy item has not reached the
        // View tree yet. Keep ownership until a real item focus event settles the request: list
        // data may replace the first row between this dispatch and B-Material's next layout pass.
        // A target can briefly become disabled while the screen's repository publishes its first
        // snapshot. In that case B-Material correctly drops its native pending request; replay the
        // same logical target on the materialization transition without creating a retry loop.
        val targetWasClaimed = focusEntryState.dispatchFocusIfCurrent(request.requestId, target)
        val targetWasAlreadyClaimed = focusEntryState.dispatchedTarget == target
        if (targetWasClaimed || (requestedTargetMaterialized && targetWasAlreadyClaimed)) {
            focusController.requestFocus(target)
        }
    }
    RotaryDestination(
        destinationKey = "settings-$routeKey",
        fallback = fallback,
    ) {
        CompositionLocalProvider(LocalSettingsDestinationKey provides routeKey) {
            Scaffold(
                containerColor = settingsBackgroundColor(),
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {
                    FocusArea(
                        id = appBarAreaId,
                        isFocusAllowed = hasAppBarFocusItems,
                        // Material3's default top-app-bar insets include the edge-to-edge status
                        // bar.  The native CCP area would then report a 122dp header while
                        // Scaffold's content starts at the 64dp app-bar baseline, making the two
                        // sibling FocusAreas overlap.  Keep the app-bar area at its actual content
                        // height and let the host/shell own system-bar insets.
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                .height(80.dp),
                        wrapAround = appBarFocusPolicy?.wrapAround ?: false,
                        nextFocusArea = appBarFocusPolicy?.nextFocusArea,
                        previousFocusArea = appBarFocusPolicy?.previousFocusArea,
                    ) {
                        SettingsHeaderBar(
                            title = title,
                            titleIconRes = titleIconRes,
                            subtitle = subtitle,
                            onBack = onBack?.takeUnless { isRoot },
                            backLabel = stringResource(R.string.settings_back_content_description),
                            backId = FocusItemId("settings-back-$routeKey"),
                            actions = { actions?.invoke() },
                        )
                    }
                },
            ) { padding ->
                FocusArea(
                    id = contentAreaId,
                    firstFocusAt = firstFocusableContentItemId,
                    isFocusAllowed = contentFocusAllowed,
                    modifier = Modifier.padding(padding).fillMaxWidth(),
                    wrapAround = contentFocusPolicy.wrapAround,
                    previousFocusArea = contentFocusPolicy.previousFocusArea,
                    nextFocusArea = contentFocusPolicy.nextFocusArea,
                    focusOrder = lazyFocusSpec.focusOrder,
                    onFocusItemUnavailable = lazyFocusHandler,
                ) {
                    CompositionLocalProvider(
                        LocalSettingsRotaryArea provides true,
                        LocalSettingsFirstFocusRegistration provides { itemId ->
                            if (discoveredFirstContentItemId.value == null) {
                                discoveredFirstContentItemId.value = itemId
                            }
                        },
                        LocalSettingsContentFocusObserver provides { itemId ->
                            focusEntryState?.onContentItemFocused(routeKey, itemId)
                        },
                        LocalSettingsFocusItemAvailabilityObserver provides onFocusItemAvailability,
                    ) {
                        // FocusArea owns the vertical layout.  Adding a second, unconstrained Column
                        // here lets a FocusItem inherit the viewport's max height and was the reason a
                        // direct Settings row expanded to 1920x844 on the automotive emulator.
                        content()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarPanelScaffold(
    title: String,
    onClose: () -> Unit,
    /** Stable content FocusItem key to select when this panel is first entered. */
    firstContentFocusId: String? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onClose)
    val routeKey = stableRotaryKey(title)
    val areaKey = stableRotaryKey("panel-$routeKey")
    val appBarAreaId = FocusAreaId("settings-header-$areaKey")
    val contentAreaId = FocusAreaId("settings-detail-$areaKey")
    val detailPaneRing =
        remember(appBarAreaId, contentAreaId) {
            settingsFocusAreaRing(listOf(appBarAreaId, contentAreaId))
        }
    val appBarFocusPolicy = detailPaneRing.getValue(appBarAreaId)
    val contentFocusPolicy = detailPaneRing.getValue(contentAreaId)
    val firstContentItemId =
        firstContentFocusId?.let { settingsFocusItemId("panel-$routeKey", it) }
    val discoveredFirstContentItemId = remember(routeKey) { mutableStateOf<FocusItemId?>(null) }
    val firstFocusableContentItemId = firstContentItemId ?: discoveredFirstContentItemId.value
    RotaryDestination(
        destinationKey = "panel-$routeKey",
        fallback =
            firstFocusableContentItemId?.let { itemId ->
                RotaryFocusTarget(areaId = contentAreaId, itemId = itemId)
            } ?: RotaryFocusTarget(
                areaId = appBarAreaId,
                itemId = FocusItemId("panel-close-$routeKey"),
            ),
    ) {
        CompositionLocalProvider(LocalSettingsDestinationKey provides "panel-$routeKey") {
            Scaffold(
                topBar = {
                    FocusArea(
                        id = appBarAreaId,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                .height(80.dp),
                        wrapAround = appBarFocusPolicy.wrapAround,
                        nextFocusArea = appBarFocusPolicy.nextFocusArea,
                        previousFocusArea = appBarFocusPolicy.previousFocusArea,
                    ) {
                        SettingsHeaderBar(
                            title = title,
                            onBack = onClose,
                            backLabel = stringResource(R.string.panel_close_content_description),
                            backId = FocusItemId("panel-close-$routeKey"),
                            actions = { actions?.invoke() },
                        )
                    }
                },
            ) { padding ->
                FocusArea(
                    id = contentAreaId,
                    firstFocusAt = firstFocusableContentItemId,
                    modifier = Modifier.padding(padding).fillMaxWidth(),
                    wrapAround = contentFocusPolicy.wrapAround,
                    previousFocusArea = contentFocusPolicy.previousFocusArea,
                    nextFocusArea = contentFocusPolicy.nextFocusArea,
                ) {
                    CompositionLocalProvider(
                        LocalSettingsRotaryArea provides true,
                        LocalSettingsFirstFocusRegistration provides { itemId ->
                            if (discoveredFirstContentItemId.value == null) {
                                discoveredFirstContentItemId.value = itemId
                            }
                        },
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    busy: Boolean = false,
    /** Keeps the currently focused row parked while an asynchronous write is in flight. */
    retainFocusWhenDisabled: Boolean = busy,
    focusId: String = title,
    leading: @Composable (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionEnabled = enabled && !busy
    val rowHeight = settingsRowHeight(summary)
    RotarySettingsItem(
        focusId = focusId,
        label = title,
        stateDescription = if (checked) "On" else "Off",
        role = FocusItemRole.Toggle,
        enabled = interactionEnabled,
        retainFocusWhenUnavailable = retainFocusWhenDisabled,
        onClick = { if (interactionEnabled) onCheckedChange(!checked) },
        itemHeight = rowHeight,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
    ) { _ ->
        SettingsCardSurface(
            modifier = Modifier.fillMaxWidth().height(rowHeight),
            enabled = interactionEnabled,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // The single action of this row is the switch, so the whole row toggles:
                        // finger taps anywhere and rotary Center share one activation path.
                        .toggleable(
                            value = checked,
                            enabled = interactionEnabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        ).padding(
                            horizontal = SettingsTokens.CardHorizontalPadding,
                            vertical =
                                if (summary.isNullOrBlank()) {
                                    SettingsTokens.CardSingleLineVerticalPadding
                                } else {
                                    SettingsTokens.CardSummaryVerticalPadding
                                },
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                if (leading != null) Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    summary?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    BSwitch(
                        checked = checked,
                        enabled = interactionEnabled,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/**
 * A list row with an independent switch and a separate row action.  This is used by
 * Notifications where Center/row opens details but touching the switch changes only the policy.
 */
@Composable
fun SettingsActionToggleRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    busy: Boolean = false,
    /** Keeps the row focusable only if it already owned focus before becoming unavailable. */
    retainFocusWhenDisabled: Boolean = busy,
    switchEnabled: Boolean = enabled,
    focusId: String = title,
    leading: @Composable (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
    onRowClick: () -> Unit,
) {
    val rowEnabled = enabled && !busy
    val rowHeight = settingsRowHeight(summary)
    RotarySettingsItem(
        focusId = focusId,
        label = title,
        stateDescription = if (checked) "On" else "Off",
        role = FocusItemRole.Button,
        enabled = rowEnabled,
        retainFocusWhenUnavailable = retainFocusWhenDisabled,
        onClick = onRowClick,
        itemHeight = rowHeight,
        // Let the nested Compose switch receive a touch.  Rotary Center still invokes the
        // FocusItem callback and therefore opens the detail route exactly once.
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
    ) { _ ->
        SettingsCardSurface(
            modifier = Modifier.fillMaxWidth().height(rowHeight),
            enabled = rowEnabled,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            enabled = rowEnabled,
                            role = Role.Button,
                            onClick = onRowClick,
                        ).padding(
                            horizontal = SettingsTokens.CardHorizontalPadding,
                            vertical =
                                if (summary.isNullOrBlank()) {
                                    SettingsTokens.CardSingleLineVerticalPadding
                                } else {
                                    SettingsTokens.CardSummaryVerticalPadding
                                },
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                if (leading != null) Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    summary?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    BSwitch(
                        checked = checked,
                        enabled = switchEnabled && !busy,
                        onCheckedChange = onCheckedChange,
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityAwareSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    busy: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        busy = busy,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
fun PanelErrorState(
    message: String,
    onDismiss: () -> Unit,
) {
    SettingsActionRow(
        title = stringResource(R.string.panel_action_failed_title),
        summary = message,
        onClick = onDismiss,
    )
}

@Composable
fun SettingsActionRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    /** Renders a trailing chevron so users can see the row opens another screen. */
    navigates: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    focusId: String = title,
    onClick: () -> Unit,
) {
    val rowHeight = settingsRowHeight(summary)
    RotarySettingsItem(
        focusId = focusId,
        label = title,
        role = FocusItemRole.Button,
        enabled = enabled,
        onClick = onClick,
        itemHeight = rowHeight,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
    ) { _ ->
        SettingsCardSurface(
            modifier = Modifier.fillMaxWidth().height(rowHeight),
            enabled = enabled,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        ).padding(
                            horizontal = SettingsTokens.CardHorizontalPadding,
                            vertical =
                                if (summary.isNullOrBlank()) {
                                    SettingsTokens.CardSingleLineVerticalPadding
                                } else {
                                    SettingsTokens.CardSummaryVerticalPadding
                                },
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                if (leading != null) Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    summary?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke()
                if (navigates) SettingsChevronHint()
            }
        }
    }
}

/** Trailing affordance for rows that navigate to another screen. */
@Composable
fun SettingsChevronHint(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        modifier = modifier.padding(start = 8.dp).size(32.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(SettingsTokens.SectionTopGap))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = SettingsTokens.CardHorizontalPadding, vertical = 10.dp),
        )
        content()
    }
}

@Composable
fun KeyValueRow(
    key: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsTokens.CardHorizontalPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/**
 * Compact, edge-to-edge-safe header shared by standard and vehicle destinations.
 *
 * Material3's TopAppBar applies a status-bar inset that is larger than the visual header on the
 * automotive host.  Keeping the title and action FocusItems in this explicit 64dp row means the
 * native CCP header area and detail area have disjoint geometry while the shell owns system-bar
 * padding.
 */
@Composable
internal fun SettingsHeaderBar(
    title: String,
    @DrawableRes titleIconRes: Int? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    backId: FocusItemId? = null,
    titleTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null && backLabel != null && backId != null) {
            RotaryAppBarButton(id = backId, label = backLabel, onClick = onBack)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                titleIconRes?.let { iconRes ->
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        // Vector fills are black; without a tint the header icon disappears on
                        // dark surfaces.
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = title,
                    modifier =
                        Modifier
                            .then(
                                if (titleIconRes == null) Modifier else Modifier.padding(start = 12.dp),
                            ).then(
                                titleTag?.let { Modifier.testTag(it) }
                                    ?: Modifier,
                            ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
private fun RotaryAppBarButton(
    id: FocusItemId,
    label: String,
    onClick: () -> Unit,
) {
    FocusItem(
        id = id,
        layout = FocusItemLayout(fillCrossAxis = false, minWidth = 76.dp, minHeight = 76.dp),
        onClick = onClick,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics = FocusItemSemantics(label = label, role = FocusItemRole.Button),
    ) { _ ->
        BIconButton(
            onClick = onClick,
            style = BIconButtonStyle.Text,
            size = BIconButtonSize.Lg,
            modifier = Modifier.size(76.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Rotary-aware B-Material action for app bars (refresh, overflow, and similar commands). */
@Composable
fun SettingsAppBarAction(
    id: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    FocusItem(
        id = FocusItemId("settings-action-${stableRotaryKey(id)}"),
        isEnabled = enabled,
        layout = FocusItemLayout(width = 76.dp, height = 76.dp, fillCrossAxis = false),
        onClick = onClick,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics = FocusItemSemantics(label = contentDescription, role = FocusItemRole.Button),
    ) { _ ->
        BIconButton(
            onClick = onClick,
            enabled = enabled,
            style = BIconButtonStyle.Text,
            size = BIconButtonSize.Lg,
            modifier = Modifier.size(76.dp),
            content = icon,
        )
    }
}

@Composable
private fun RotarySettingsItem(
    focusId: String,
    label: String,
    stateDescription: String? = null,
    role: FocusItemRole,
    enabled: Boolean,
    retainFocusWhenUnavailable: Boolean = false,
    onClick: () -> Unit,
    itemHeight: androidx.compose.ui.unit.Dp,
    directManipulation: DirectManipulationConfig? = null,
    touchBehavior: FocusItemTouchBehavior = FocusItemTouchBehavior.View,
    content: @Composable (focused: Boolean) -> Unit,
) {
    // The real B-Material FocusItem intentionally suppresses its Compose renderer from the
    // platform accessibility tree. The stable FocusItem id (logged at debug level during
    // development if needed) is the auditable contract for UIAutomator verifiers.
    val focusItemId = settingsFocusItemId(LocalSettingsDestinationKey.current, focusId)
    val controller = LocalRotaryFocusController.current
    val focusEnabled =
        enabled ||
            (retainFocusWhenUnavailable && controller?.currentFocusTarget?.itemId == focusItemId)
    if (LocalSettingsRotaryArea.current) {
        val registerFirstFocus = LocalSettingsFirstFocusRegistration.current
        SideEffect {
            if (focusEnabled) registerFirstFocus(focusItemId)
        }
        FocusItem(
            id = focusItemId,
            isEnabled = focusEnabled,
            onClick = if (enabled) onClick else null,
            directManipulation = directManipulation,
            semantics =
                FocusItemSemantics(
                    label = label,
                    stateDescription = stateDescription,
                    role = role,
                ),
            // Reserve a real gap in the focus bounds as well as in the visual card. This keeps
            // adjacent rows distinct even when several rows are emitted inside one LazyColumn
            // item (a common pattern on Bluetooth and Wi-Fi screens).
            layout = FocusItemLayout(height = itemHeight + SettingsTokens.CardGap),
            touchBehavior = touchBehavior,
        ) { state ->
            content(state.isFocused)
        }
    } else if (LocalRotaryFocusController.current != null) {
        // Dialogs and rotary panels do not install SettingsScaffold's local marker, but they
        // still need the same stable FocusItem contract for list rows and switches.
        FocusItem(
            id = focusItemId,
            isEnabled = focusEnabled,
            onClick = if (enabled) onClick else null,
            directManipulation = directManipulation,
            semantics =
                FocusItemSemantics(
                    label = label,
                    stateDescription = stateDescription,
                    role = role,
                ),
            layout = FocusItemLayout(height = itemHeight + SettingsTokens.CardGap),
            touchBehavior = touchBehavior,
        ) { state ->
            content(state.isFocused)
        }
    } else {
        content(false)
    }
}

/**
 * B-Material button wrapper for form content which is not naturally represented by a list row.
 * Settings screens and rotary dialogs share this component so a form action is never a Compose-
 * only island in an otherwise CCP traversable destination.
 */
@Composable
fun SettingsFormButton(
    focusId: String,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val render: @Composable () -> Unit = {
        AutomotiveButton(
            label = label,
            onClick = onClick,
            enabled = enabled,
            size = BButtonSize.Lg,
            modifier =
                modifier
                    .height(SettingsTokens.FormControlHeight),
        )
    }
    if (LocalSettingsRotaryArea.current) {
        RotarySettingsItem(
            focusId = focusId,
            label = label,
            role = FocusItemRole.Button,
            enabled = enabled,
            onClick = onClick,
            itemHeight = SettingsTokens.FormItemHeight,
            touchBehavior = FocusItemTouchBehavior.ComposeContent,
        ) { render() }
    } else {
        render()
    }
}

/** B-Material text field with a real CCP item on Settings and rotary dialog surfaces. */
@Composable
fun SettingsFormTextField(
    focusId: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val activate: () -> Unit =
        remember(focusRequester, onClick) {
            {
                focusRequester.requestFocus()
                onClick?.invoke()
            }
        }
    val render: @Composable () -> Unit = {
        AutomotiveTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            placeholder = placeholder,
            trailingIcon = trailingIcon,
            modifier =
                modifier
                    .height(SettingsTokens.FormControlHeight)
                    .focusRequester(focusRequester),
        )
        // Show error message below the field when in error state.
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
    if (LocalSettingsRotaryArea.current) {
        RotarySettingsItem(
            focusId = focusId,
            label = label,
            role = FocusItemRole.Custom("android.widget.EditText"),
            enabled = enabled,
            onClick = activate,
            itemHeight = SettingsTokens.FormItemHeight,
            touchBehavior = FocusItemTouchBehavior.ComposeContent,
        ) { render() }
    } else {
        render()
    }
}

/** Rotary-adjustable B-Material slider for settings forms and panels. */
@Composable
fun SettingsFormSlider(
    focusId: String,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    showValueLabel: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val step =
        if (steps > 0) {
            (valueRange.endInclusive - valueRange.start) / (steps + 1)
        } else {
            (valueRange.endInclusive - valueRange.start) / 20f
        }.coerceAtLeast(0.0001f)
    val latestValue = rememberUpdatedState(value)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val directManipulation =
        remember(focusId, valueRange, steps, enabled) {
            DirectManipulationConfig(
                onRotary = { event ->
                    latestOnValueChange.value(
                        (latestValue.value + event.detents * step)
                            .coerceIn(valueRange.start, valueRange.endInclusive),
                    )
                },
            )
        }
    val render: @Composable () -> Unit = {
        Column(
            modifier =
                modifier
                    .height(SettingsTokens.FormSliderHeight)
                    .padding(SettingsTokens.FormSliderContentPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            AutomotiveSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                showValueLabel = showValueLabel,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        }
    }
    if (LocalSettingsRotaryArea.current) {
        RotarySettingsItem(
            focusId = focusId,
            label = label,
            role = FocusItemRole.Adjustable,
            enabled = enabled,
            onClick = {},
            itemHeight = SettingsTokens.FormSliderHeight,
            directManipulation = directManipulation,
            touchBehavior = FocusItemTouchBehavior.ComposeContent,
        ) { render() }
    } else {
        render()
    }
}

/** Large automotive list-item metrics: readable text plus a dedicated inter-card gap. */
private fun settingsRowHeight(summary: String?): androidx.compose.ui.unit.Dp =
    if (summary.isNullOrBlank()) SettingsTokens.CardMinHeight else SettingsTokens.CardMinHeight + 16.dp

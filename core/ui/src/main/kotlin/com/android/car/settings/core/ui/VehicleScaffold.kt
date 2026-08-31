package com.android.car.settings.core.ui

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.LocalIsInTouchMode
import com.b231001.bmaterial.ccp.rotaryfocus.LocalRotaryFocusController
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget

private val LocalVehicleDetailFocusAreaPolicies =
    staticCompositionLocalOf<Map<FocusAreaId, SettingsFocusAreaPolicy>> { emptyMap() }

/** Vehicle-control screen chrome with an Automotive-friendly, real-View rotary target. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSettingsScaffold(
    title: String,
    @DrawableRes titleIconRes: Int? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** Stable route identity for rotary restoration; never derive it from a localized title. */
    destinationKey: String? = null,
    /** Stable first content item used when CCP opens this destination. */
    firstContentFocusId: FocusItemId? = null,
    /** False while the destination's first content target is not yet available. */
    isContentFocusReady: Boolean = true,
    backContentDescription: String = stringResource(R.string.vehicle_back_content_description),
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    /** Native content areas in visual rotary order after Back/app-bar actions. */
    contentFocusAreaIds: List<String> = emptyList(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val routeKey = destinationKey?.takeIf { it.isNotBlank() } ?: stableVehicleRotaryKey(title)
    // Vehicle screens form a detail-local ring. The app bar never links back to the category
    // pane; Center/Back navigation owns explicit handoff between the two panes.
    val areaKey = stableVehicleRotaryKey(routeKey)
    val appBarAreaId = FocusAreaId("settings-header-$areaKey")
    val contentAreaIds = remember(contentFocusAreaIds) { contentFocusAreaIds.map(::FocusAreaId) }
    val detailPaneRing =
        remember(appBarAreaId, contentAreaIds) {
            settingsFocusAreaRing(listOf(appBarAreaId) + contentAreaIds)
        }
    val appBarFocusPolicy = detailPaneRing.getValue(appBarAreaId)
    val focusEntryState = LocalSettingsFocusEntryState.current
    val pendingFocusEntry = focusEntryState?.pendingRequest
    val focusController = LocalRotaryFocusController.current
    val isInTouchMode = LocalIsInTouchMode.current
    val focusItemAvailability = remember(routeKey) { mutableStateMapOf<FocusItemId, Boolean>() }
    val onFocusItemAvailability =
        remember(routeKey) {
            { itemId: FocusItemId, isEnabled: Boolean ->
                if (isEnabled) {
                    focusItemAvailability[itemId] = true
                } else {
                    focusItemAvailability.remove(itemId)
                }
                Unit
            }
        }
    val firstContentAreaId = contentFocusAreaIds.firstOrNull()?.let(::FocusAreaId)
    val firstContentTarget =
        firstContentAreaId
            ?.let { areaId ->
                firstContentFocusId?.let { itemId -> RotaryFocusTarget(areaId, itemId) }
            }?.takeIf { isContentFocusReady }
    SideEffect {
        focusEntryState?.onDestinationVisible(routeKey)
    }
    LaunchedEffect(focusEntryState, pendingFocusEntry?.requestId, routeKey) {
        focusEntryState?.claimPendingFocus(routeKey)
    }
    val requestedTargetMaterialized =
        firstContentTarget?.let { target -> focusItemAvailability[target.itemId] == true } == true
    LaunchedEffect(
        focusController,
        focusEntryState,
        pendingFocusEntry?.requestId,
        pendingFocusEntry?.destinationKey,
        routeKey,
        firstContentTarget,
        requestedTargetMaterialized,
        isInTouchMode,
    ) {
        if (isInTouchMode) {
            focusEntryState?.cancelPendingFocus()
            return@LaunchedEffect
        }
        val request = pendingFocusEntry ?: return@LaunchedEffect
        val target = firstContentTarget ?: return@LaunchedEffect
        if (focusController == null || request.destinationKey != routeKey || !requestedTargetMaterialized) {
            return@LaunchedEffect
        }
        val targetWasClaimed = focusEntryState.dispatchFocusIfCurrent(request.requestId, target)
        val targetWasAlreadyClaimed = focusEntryState.dispatchedTarget == target
        if (targetWasClaimed || (requestedTargetMaterialized && targetWasAlreadyClaimed)) {
            focusController.requestFocus(target)
        }
    }

    LaunchedEffect(title) {
        Log.i("MySystemVehicle", "vehicle-screen title=$title")
    }

    CompositionLocalProvider(LocalVehicleDetailFocusAreaPolicies provides detailPaneRing) {
        CompositionLocalProvider(
            LocalSettingsContentFocusObserver provides { itemId ->
                focusEntryState?.onContentItemFocused(routeKey, itemId)
            },
            LocalSettingsFocusItemAvailabilityObserver provides onFocusItemAvailability,
        ) {
            Scaffold(
                modifier = modifier,
                containerColor = settingsBackgroundColor(),
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    FocusArea(
                        id = appBarAreaId,
                        // Do not include the edge-to-edge status-bar inset in the CCP header bounds;
                        // Scaffold lays content out from the 64dp app-bar baseline and the two areas
                        // must remain disjoint for rotary traversal validation.
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
                            titleIconRes = titleIconRes,
                            onBack =
                                onBack?.let { back ->
                                    {
                                        Log.i("MySystemVehicle", "vehicle-appbar-back title=$title")
                                        back()
                                    }
                                },
                            backLabel = backContentDescription,
                            backId = FocusItemId("vehicle-back-$routeKey"),
                            titleTag = "vehicle-feature-screen-title",
                            actions = actions,
                        )
                    }
                },
                snackbarHost = snackbarHost,
                content = content,
            )
        }
    }
}

/** Returns the detail-local ring policy for a vehicle area, with safe local wrapping in previews. */
@Composable
internal fun vehicleDetailFocusAreaPolicy(areaId: FocusAreaId): SettingsFocusAreaPolicy =
    LocalVehicleDetailFocusAreaPolicies.current[areaId]
        ?: settingsFocusAreaRing(listOf(areaId)).getValue(areaId)

/** A B-Material grouped surface for one logical vehicle capability. */
@Composable
fun VehicleControlSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsCardSurface(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            content()
        }
    }
}

private fun stableVehicleRotaryKey(value: String): String = value.hashCode().toUInt().toString(16)

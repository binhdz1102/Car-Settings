package com.android.car.settings.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.uicomponents.button.BButtonSize

/**
 * Compact, non-disruptive pending feedback shared by writable vehicle controls.
 *
 * The control itself remains mounted and interactive. This pulse is deliberately not a spinner
 * and never replaces the current Switch/Slider/Enum value.
 */
@Composable
fun VehiclePendingIndicator(
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.vehicle_control_pending),
    size: Dp = 10.dp,
    shape: Shape = CircleShape,
) {
    val transition = rememberInfiniteTransition(label = "vehicle-write-pending")
    val pulseAlpha by
        transition.animateFloat(
            initialValue = 0.42f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(durationMillis = VehicleMotionTokens.PENDING_PULSE_DURATION_MILLIS),
                    RepeatMode.Reverse,
                ),
            label = "vehicle-write-pending-alpha",
        )
    Box(
        modifier =
            modifier
                .size(size)
                .alpha(pulseAlpha)
                .background(MaterialTheme.colorScheme.primary, shape)
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    stateDescription = contentDescription
                },
    )
}

/** Actionable, screen-reader-announced vehicle error. */
@Composable
fun VehicleErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.vehicle_control_error_title),
    retryLabel: String = stringResource(R.string.vehicle_control_retry),
    dismissContentDescription: String = stringResource(R.string.vehicle_control_dismiss_error),
    onRetry: (() -> Unit)? = null,
    retryFocusId: FocusItemId? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize()
                .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                AnimatedContent(
                    targetState = message,
                    transitionSpec = {
                        fadeIn(tween(VehicleMotionTokens.CONTENT_ENTER_DURATION_MILLIS)) togetherWith
                            fadeOut(tween(VehicleMotionTokens.CONTENT_EXIT_DURATION_MILLIS))
                    },
                    label = "vehicle-error-message",
                ) { visibleMessage ->
                    Text(text = visibleMessage, style = MaterialTheme.typography.bodyMedium)
                }
                if (onRetry != null) {
                    // A retry is always actionable, including banners rendered outside a
                    // vehicle-category FocusArea. Keep a deterministic fallback ID instead of
                    // silently creating a touch-only button when the caller has no area-specific
                    // ID.
                    val resolvedRetryFocusId =
                        retryFocusId
                            ?: FocusItemId("vehicle-error-retry-${stableFeedbackKey(title)}")
                    FocusItem(
                        id = resolvedRetryFocusId,
                        onClick = onRetry,
                        touchBehavior = FocusItemTouchBehavior.ComposeContent,
                        semantics =
                            FocusItemSemantics(
                                label = retryLabel,
                                role = FocusItemRole.Button,
                            ),
                    ) {
                        AutomotiveTextButton(
                            label = retryLabel,
                            onClick = onRetry,
                        )
                    }
                }
            }
            if (onDismiss != null) {
                RotaryAction(
                    id = "vehicle-error-dismiss-${stableFeedbackKey(title)}",
                    label = dismissContentDescription,
                    onClick = onDismiss,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) { focused ->
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).rotaryFocusBorder(focused),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = dismissContentDescription,
                        )
                    }
                }
            }
        }
    }
}

/** Explicit empty state used when a VHAL capability or area is not available. */
@Composable
fun VehicleUnavailableState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.vehicle_control_unavailable),
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { stateDescription = title },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actionLabel != null && onAction != null) {
                AutomotiveButton(label = actionLabel, onClick = onAction)
            }
        }
    }
}

/**
 * A feature-specific information action. Each caller supplies its own content so ADAS details
 * cannot accidentally be reused across unrelated functions.
 */
@Composable
fun VehicleInfoButton(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    contentDescription: String =
        stringResource(R.string.vehicle_control_info_content_description, title),
    limitations: String? = null,
    dependencies: String? = null,
    additionalSections: List<VehicleInfoSection> = emptyList(),
    enabled: Boolean = true,
    onOpenInfo: (() -> Unit)? = null,
) {
    var isOpen by rememberSaveable(title) { mutableStateOf(false) }

    IconButton(
        enabled = enabled,
        onClick = {
            if (onOpenInfo != null) {
                onOpenInfo()
            } else {
                isOpen = true
            }
        },
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = contentDescription,
        )
    }

    if (isOpen && onOpenInfo == null) {
        VehicleInfoBottomSheet(
            title = title,
            body = body,
            limitations = limitations,
            dependencies = dependencies,
            additionalSections = additionalSections,
            onDismissRequest = { isOpen = false },
        )
    }
}

/**
 * Rotary-aware information action used beside a vehicle control row.
 *
 * The control itself owns a separate [FocusItem], so the information action must not be nested
 * inside that item. Keeping this as a sibling gives CCP users an explicit, predictable stop while
 * still allowing the icon to remain a compact touch target.
 */
@Composable
fun VehicleInfoFocusItem(
    focusId: FocusItemId,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val infoLabel = stringResource(R.string.vehicle_control_info_content_description, title)
    FocusItem(
        id = focusId,
        modifier = modifier,
        isEnabled = enabled,
        onClick = onClick,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        layout = FocusItemLayout(fillCrossAxis = false, minWidth = 64.dp, minHeight = 72.dp),
        semantics =
            FocusItemSemantics(
                label = infoLabel,
                role = FocusItemRole.Button,
            ),
    ) { state ->
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).rotaryFocusBorder(state.isFocused),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = infoLabel,
            )
        }
    }
}

/** Modal information surface with an explicit close action and pane semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleInfoBottomSheet(
    title: String,
    body: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    limitations: String? = null,
    limitationsHeading: String =
        stringResource(R.string.vehicle_control_info_limitations_heading),
    dependencies: String? = null,
    dependenciesHeading: String =
        stringResource(R.string.vehicle_control_info_dependencies_heading),
    additionalSections: List<VehicleInfoSection> = emptyList(),
    closeLabel: String = stringResource(R.string.vehicle_control_info_close),
    closeContentDescription: String =
        stringResource(R.string.vehicle_control_info_close_content_description),
) {
    val infoScrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.semantics { paneTitle = title },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .vehicleBScrollbar(infoScrollState)
                    .verticalScroll(infoScrollState)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = body, style = MaterialTheme.typography.bodyLarge)
            if (limitations != null) {
                VehicleInfoSectionContent(
                    title = limitationsHeading,
                    body = limitations,
                )
            }
            if (dependencies != null) {
                VehicleInfoSectionContent(
                    title = dependenciesHeading,
                    body = dependencies,
                )
            }
            additionalSections.forEach { section ->
                VehicleInfoSectionContent(
                    title = section.title,
                    body = section.body,
                )
            }
            RotaryAction(
                id = "vehicle-info-sheet-close-${stableFeedbackKey(title)}",
                label = closeContentDescription,
                onClick = onDismissRequest,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .sizeIn(
                            minWidth = SettingsTokens.DialogActionMinWidth,
                            minHeight = SettingsTokens.DialogActionMinHeight,
                        ),
            ) { focused ->
                AutomotiveTextButton(
                    onClick = onDismissRequest,
                    size = BButtonSize.Lg,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = SettingsTokens.DialogActionMinHeight)
                            .rotaryFocusBorder(focused)
                            .semantics { contentDescription = closeContentDescription },
                ) { Text(text = closeLabel) }
            }
        }
    }
}

@Composable
private fun VehicleInfoSectionContent(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun stableFeedbackKey(value: String): String = value.hashCode().toUInt().toString(16)

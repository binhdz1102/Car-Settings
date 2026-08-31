package com.android.car.settings.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val VEHICLE_TOP_VIEW_ASPECT_RATIO = 1570f / 1001f
private val VEHICLE_TOP_VIEW_MAX_WIDTH = 640.dp
private val ZONE_MARKER_SIZE = 48.dp

/**
 * Top-view vehicle artwork with optional capability-area markers. Marker positions are supplied by
 * the feature as presentation fractions, keeping this module independent from vehicle-area types.
 */
@Composable
fun VehicleTopView(
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.vehicle_top_view_content_description),
    zones: List<VehicleTopViewZone> = emptyList(),
    selectedAreaId: Int? = null,
    onZoneSelected: ((VehicleTopViewZone) -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    zonesContentDescription: String =
        stringResource(R.string.vehicle_zone_selector_content_description),
) {
    val interactive = enabled && !readOnly && onZoneSelected != null

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .widthIn(max = VEHICLE_TOP_VIEW_MAX_WIDTH)
                    .fillMaxWidth()
                    .aspectRatio(VEHICLE_TOP_VIEW_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .then(if (zones.isEmpty()) Modifier else Modifier.selectableGroup())
                    .semantics {
                        if (zones.isNotEmpty()) this.contentDescription = zonesContentDescription
                        if (!enabled) disabled()
                    },
        ) {
            Image(
                painter = painterResource(R.drawable.vehicle_top_view),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            zones.forEach { zone ->
                val selected = zone.areaId == selectedAreaId
                var hasComposeFocus by remember(zone.areaId) { mutableStateOf(false) }
                val x = maxWidth * zone.horizontalFraction - ZONE_MARKER_SIZE / 2
                val y = maxHeight * zone.verticalFraction - ZONE_MARKER_SIZE / 2
                val markerModifier =
                    if (interactive) {
                        Modifier.selectable(
                            selected = selected,
                            enabled = zone.enabled,
                            role = Role.RadioButton,
                            onClick = { onZoneSelected.invoke(zone) },
                        )
                    } else {
                        Modifier.semantics {
                            this.selected = selected
                            stateDescription = zone.label
                            if (!enabled || !zone.enabled) disabled()
                        }
                    }

                Surface(
                    modifier =
                        Modifier
                            .offset(x = x, y = y)
                            .size(ZONE_MARKER_SIZE)
                            .then(markerModifier)
                            .onFocusChanged { hasComposeFocus = it.hasFocus }
                            .rotaryFocusBorder(hasComposeFocus, shape = CircleShape)
                            .semantics { this.contentDescription = zone.contentDescription },
                    shape = CircleShape,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        },
                    contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    border =
                        BorderStroke(
                            width = if (selected) 3.dp else 1.dp,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = zone.shortLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (!enabled) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                drawContent()
                                drawRect(Color.Black.copy(alpha = 0.22f))
                            },
                )
            }
        }
    }
}

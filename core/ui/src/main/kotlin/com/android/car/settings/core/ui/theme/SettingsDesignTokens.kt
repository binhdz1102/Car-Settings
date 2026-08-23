@file:Suppress("MatchingDeclarationName")

package com.android.car.settings.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Semantic colors shared by the Settings shell, cards and dialogs. */
object SettingsTokens {
    val BackgroundDark = Color(0xFF15191A)
    val SurfaceDark = Color(0xFF1D2426)
    val SurfaceRaisedDark = Color(0xFF273237)
    val SelectedDark = Color(0xFF3B5360)
    val PrimaryDark = Color(0xFF92DAFB)
    val PrimaryContainerDark = Color(0xFF075D7E)
    val TextDark = Color(0xFFE4E8EA)
    val MutedTextDark = Color(0xFFB2BDC1)
    val OutlineDark = Color(0xFF536168)
    val DividerDark = Color(0xFF485257)

    val BackgroundLight = Color(0xFFF6F8F9)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceRaisedLight = Color(0xFFEAF0F2)
    val SelectedLight = Color(0xFFC9E5F1)
    val PrimaryLight = Color(0xFF00668A)
    val PrimaryContainerLight = Color(0xFFBDE9FF)
    val TextLight = Color(0xFF172126)
    val MutedTextLight = Color(0xFF4E5E66)
    val OutlineLight = Color(0xFF73838A)
    val DividerLight = Color(0xFFB7C4C9)

    val CardShape = RoundedCornerShape(18.dp)
    val DialogShape = RoundedCornerShape(24.dp)

    /**
     * Persistent rail width at automotive density: 340dp of category content plus the shared
     * 48dp scrollbar gutter. Reserving the gutter must not force category labels to wrap.
     */
    val RailWidth = 388.dp

    val ShellHorizontalPadding = 28.dp
    val ShellVerticalPadding = 20.dp
    val ShellPaneGap = 28.dp
    val RailHeaderHeight = 72.dp

    /**
     * Automotive touch/rotary targets are deliberately larger than phone Settings rows. The
     * 92dp rail target keeps eight categories visible on a 1080p head unit while giving text,
     * icon and focus-ring clearance room.
     */
    val RailItemMinHeight = 92.dp
    val RailItemGap = 10.dp
    val CardMinHeight = 104.dp

    /** Reserved between every card's focus bounds so adjacent rows never visually touch. */
    val CardGap = 10.dp

    /** Keeps the rotary ring visually clear of the surface edge and adjacent content. */
    val FocusRingClearance = 4.dp

    /** Shared dialog action metrics for both native rotary and touch fallback windows. */
    val DialogActionMinWidth = 176.dp
    val DialogActionMinHeight = 72.dp
    val DialogActionGap = 16.dp

    val CardHorizontalPadding = 24.dp
    val CardSingleLineVerticalPadding = 16.dp
    val CardSummaryVerticalPadding = 18.dp

    /** Controls share a card-sized focus target instead of a small control in a tall empty box. */
    val FormItemHeight = 72.dp
    val FormControlHeight = 64.dp
    val FormSliderHeight = 96.dp
    val LeadingIconSize = 32.dp
    val SectionTopGap = 16.dp
    val DetailPaneHorizontalPadding = 16.dp
    val DetailPaneVerticalPadding = 8.dp
}

@Composable
fun settingsBackgroundColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        SettingsTokens.BackgroundDark
    } else {
        SettingsTokens.BackgroundLight
    }

@Composable
fun settingsSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        SettingsTokens.SurfaceDark
    } else {
        SettingsTokens.SurfaceLight
    }

@Composable
fun settingsSurfaceRaisedColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        SettingsTokens.SurfaceRaisedDark
    } else {
        SettingsTokens.SurfaceRaisedLight
    }

@Composable
fun settingsSelectedColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        SettingsTokens.SelectedDark
    } else {
        SettingsTokens.SelectedLight
    }

@Composable
fun settingsPrimaryColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun settingsMutedTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun settingsOutlineColor(): Color = MaterialTheme.colorScheme.outline

@Composable
fun settingsDividerColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        SettingsTokens.DividerDark
    } else {
        SettingsTokens.DividerLight
    }

private fun Color.luminance(): Float = (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)

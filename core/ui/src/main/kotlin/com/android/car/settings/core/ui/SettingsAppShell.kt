@file:Suppress("MatchingDeclarationName")

package com.android.car.settings.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.rememberLazyListFocusHandler

/** UI-only category model; business identity remains in core:settings-api. */
data class SettingsCategoryUiModel(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
)

val SettingsRailFocusAreaId = FocusAreaId("settings-rail")
val SettingsDetailFocusAreaId = FocusAreaId("settings-detail")
val SettingsSearchFocusItemId = FocusItemId("settings-search")

@Composable
fun SettingsAppShell(
    categories: List<SettingsCategoryUiModel>,
    selectedCategoryKey: String,
    onCategorySelected: (SettingsCategoryUiModel) -> Unit,
    onSearch: () -> Unit,
    focusEntryState: SettingsFocusEntryState,
    modifier: Modifier = Modifier,
    headerTitle: String = stringResource(R.string.settings_shell_default_title),
    content: @Composable () -> Unit,
) {
    val railFocusIndexes = settingsRailLazyFocusIndexById(categories)
    val enabledCategoryItems = railFocusIndexes.keys.toList()
    val railListState = rememberLazyListState()
    val railFocusOrder = listOf(SettingsSearchFocusItemId) + enabledCategoryItems
    val railFocusHandler =
        rememberLazyListFocusHandler(
            state = railListState,
            itemIndexById = railFocusIndexes,
        )
    CompositionLocalProvider(
        LocalSettingsFocusEntryState provides focusEntryState,
    ) {
        Row(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(settingsBackgroundColor())
                    .padding(
                        horizontal = SettingsTokens.ShellHorizontalPadding,
                        vertical = SettingsTokens.ShellVerticalPadding,
                    ),
            horizontalArrangement = Arrangement.spacedBy(SettingsTokens.ShellPaneGap),
        ) {
            FocusArea(
                id = SettingsRailFocusAreaId,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(SettingsTokens.RailWidth)
                        .clipToBounds(),
                firstFocusAt = SettingsSearchFocusItemId,
                focusOrder = railFocusOrder,
                wrapAround = true,
                onFocusItemUnavailable = railFocusHandler,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            // MainActivity deliberately draws edge-to-edge for the native rotary
                            // host; keep the header and final category clear of AAOS system bars.
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(end = 2.dp),
                ) {
                    SettingsShellHeader(headerTitle, onSearch)
                    Text(
                        text = stringResource(R.string.settings_shell_categories),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = settingsPrimaryColor(),
                    )
                    AutomotiveLazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                        state = railListState,
                        verticalArrangement = Arrangement.spacedBy(SettingsTokens.RailItemGap),
                    ) {
                        items(categories, key = { it.key }) { category ->
                            SettingsCategoryItem(
                                category = category,
                                selected = category.key == selectedCategoryKey,
                                onClick = { onCategorySelected(category) },
                            )
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxHeight().width(1.dp),
                color = settingsDividerColor(),
            ) {}
            SettingsDetailPane(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                content = content,
            )
        }
    }
}

/** Maps logical category targets to the actual LazyColumn slots, including disabled rows. */
internal fun settingsRailLazyFocusIndexById(categories: List<SettingsCategoryUiModel>): Map<FocusItemId, Int> =
    categories
        .mapIndexedNotNull { index, category ->
            if (category.enabled) FocusItemId("settings-category-${category.key}") to index else null
        }.toMap()

@Composable
private fun SettingsShellHeader(
    title: String,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(SettingsTokens.RailHeaderHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Settings, contentDescription = title, tint = settingsPrimaryColor(), modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        FocusItem(
            id = SettingsSearchFocusItemId,
            onClick = onSearch,
            semantics =
                FocusItemSemantics(
                    label = stringResource(R.string.settings_shell_search),
                    role = FocusItemRole.Button,
                ),
            touchBehavior = FocusItemTouchBehavior.ComposeContent,
            layout = FocusItemLayout(fillCrossAxis = false),
        ) { _ ->
            Surface(
                onClick = onSearch,
                modifier = Modifier.size(56.dp),
                shape = SettingsTokens.CardShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.settings_shell_search),
                        tint = settingsMutedTextColor(),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategoryUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusItem(
        id = FocusItemId("settings-category-${category.key}"),
        modifier = Modifier.fillMaxWidth(),
        isEnabled = category.enabled,
        onClick = onClick,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics = FocusItemSemantics(label = category.label, role = FocusItemRole.Button),
        layout = FocusItemLayout(fillCrossAxis = true),
    ) { state ->
        // Pressed: momentary feedback. Selected: persistent active-category data state.
        // Ordinary CCP focus is rendered exclusively by the enclosing FocusItem border.
        val color =
            when {
                state.isPressed -> settingsSelectedColor()
                selected -> settingsSelectedColor()
                else -> settingsSurfaceColor()
            }
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsTokens.RailItemMinHeight)
                    .clickable(enabled = category.enabled, role = Role.Button, onClick = onClick),
            shape = SettingsTokens.CardShape,
            color = color,
            border = BorderStroke(1.dp, settingsOutlineColor()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint =
                        if (selected) {
                            settingsPrimaryColor()
                        } else {
                            settingsMutedTextColor()
                        },
                    modifier = Modifier.size(SettingsTokens.LeadingIconSize),
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    category.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun SettingsDetailPane(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Raw SettingsToken colors cannot be mapped by contentColorFor(), which would leave the
    // ambient LocalContentColor at its Color.Black default and render dark-mode text/icons
    // unreadable. Propagate the scheme content color explicitly.
    Surface(
        modifier = modifier,
        color = settingsBackgroundColor(),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        // Keep the first/last card away from the persistent divider and the system edge. This
        // also gives the inset focus ring enough air when a row is selected next to the rail.
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = SettingsTokens.DetailPaneHorizontalPadding,
                    vertical = SettingsTokens.DetailPaneVerticalPadding,
                ),
        ) {
            content()
        }
    }
}

@Composable
fun SettingsCardSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val borderColor =
        if (enabled) {
            settingsOutlineColor()
        } else {
            settingsOutlineColor().copy(alpha = 0.35f)
        }
    val cardColor =
        when {
            !enabled -> settingsBackgroundColor().copy(alpha = 0.7f)
            selected -> settingsSelectedColor()
            else -> settingsSurfaceColor()
        }
    Surface(
        modifier = modifier,
        shape = SettingsTokens.CardShape,
        color = cardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(modifier = if (!enabled) Modifier.alpha(0.38f) else Modifier) {
            content()
        }
    }
}

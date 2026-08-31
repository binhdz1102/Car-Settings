package com.android.car.settings.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.uicomponents.scrollbar.BScrollbarStyle
import com.b231001.bmaterial.uicomponents.scrollbar.bLazyScrollbar
import com.b231001.bmaterial.uicomponents.scrollbar.bScrollbar

val AutomotiveScrollbarStyle =
    BScrollbarStyle(
        thickness = 12.dp,
        padding = 8.dp,
        minThumbLength = 64.dp,
        cornerRadius = 6.dp,
    )

/** Reserved AAOS touch target; rendering and pointer behavior stay owned by B-Material. */
val AutomotiveScrollbarGutterWidth = 48.dp

fun Modifier.automotiveScrollbar(
    scrollState: ScrollState,
    orientation: Orientation = Orientation.Vertical,
): Modifier =
    bScrollbar(
        scrollState = scrollState,
        orientation = orientation,
        style = AutomotiveScrollbarStyle,
        autoHideEnabled = false,
        gutterWidth = AutomotiveScrollbarGutterWidth,
    )

fun Modifier.automotiveLazyScrollbar(
    state: LazyListState,
    orientation: Orientation = Orientation.Vertical,
): Modifier =
    bLazyScrollbar(
        state = state,
        orientation = orientation,
        style = AutomotiveScrollbarStyle,
        autoHideEnabled = false,
        gutterWidth = AutomotiveScrollbarGutterWidth,
    )

/** App-level façade that applies the shared AAOS scrollbar style to every settings list. */
@Composable
fun AutomotiveLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.automotiveLazyScrollbar(state),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

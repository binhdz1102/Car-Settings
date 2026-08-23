package com.b231001.bmaterial.ccp.rotaryfocus.preview

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusDestination
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusHost
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget

/** Interactive AAOS sample covering navigation, dialog restoration, custom order and lazy focus. */
@Composable
public fun RotaryFocusDemo(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = navController,
            startDestination = PreviewRoute.Home,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(PreviewRoute.Home) {
                RotaryFocusDestination(
                    destinationKey = PreviewRoute.Home,
                    fallback = PreviewTargets.homeDefault
                ) {
                    PreviewHomeScreen(
                        onOpenList = { navController.navigate(PreviewRoute.OrderedList) }
                    )
                }
            }
            composable(PreviewRoute.OrderedList) {
                RotaryFocusDestination(
                    destinationKey = PreviewRoute.OrderedList,
                    fallback = PreviewTargets.orderedListDefault
                ) {
                    PreviewOrderedListScreen(
                        onOpenLazyList = { navController.navigate(PreviewRoute.LazyList) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(PreviewRoute.LazyList) {
                RotaryFocusDestination(
                    destinationKey = PreviewRoute.LazyList,
                    fallback = PreviewTargets.lazyListDefault
                ) {
                    PreviewLazyListScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

internal object PreviewRoute {
    const val Home: String = "focus-preview/home"
    const val OrderedList: String = "focus-preview/ordered-list"
    const val LazyList: String = "focus-preview/lazy-list"
}

internal object PreviewTargets {
    val homeArea = FocusAreaId("preview.home.main")
    val homeNext = FocusItemId("open-ordered-list")
    val homeDialog = FocusItemId("open-home-dialog")
    val homeVolume = FocusItemId("home-volume")
    val homeDefault = RotaryFocusTarget(homeArea, homeNext)

    val orderedActionsArea = FocusAreaId("preview.ordered.actions")
    val orderedListArea = FocusAreaId("preview.ordered.list")
    val orderedBack = FocusItemId("ordered-back")
    val orderedDialog = FocusItemId("open-ordered-dialog")
    val orderedOne = FocusItemId("ordered-button-1")
    val orderedTwo = FocusItemId("ordered-button-2")
    val orderedThree = FocusItemId("ordered-button-3")
    val orderedListDefault = RotaryFocusTarget(orderedListArea, orderedOne)

    val lazyHeaderArea = FocusAreaId("preview.lazy.header")
    val lazyListArea = FocusAreaId("preview.lazy.list")
    val lazyBack = FocusItemId("lazy-back")
    val lazyItemIds: List<FocusItemId> = List(40) { FocusItemId("lazy-item-$it") }
    val lazyListDefault = RotaryFocusTarget(lazyListArea, lazyItemIds.first())

    fun dialogArea(owner: String): FocusAreaId = FocusAreaId("preview.dialog.$owner")

    fun dialogDefault(owner: String): RotaryFocusTarget = RotaryFocusTarget(
        dialogArea(owner),
        FocusItemId("dialog-confirm")
    )
}

@Preview(name = "AAOS rotary navigation", widthDp = 1100, heightDp = 650)
@Composable
private fun RotaryFocusNavigationPreview() {
    MaterialTheme {
        RotaryFocusHost(
            modifier = Modifier.fillMaxSize(),
            hostId = "studio-focus-preview"
        ) {
            RotaryFocusDemo(Modifier.fillMaxSize())
        }
    }
}

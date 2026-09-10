package com.android.car.settings.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VehicleSceneRendererTest {
    @Test
    fun scene_requiresStableIdAndUniqueLayers() {
        val scene =
            VehicleSceneSpec(
                id = "climate_cabin_top_view",
                layers =
                    listOf(
                        VehicleLayerSpec("base", 1, VehiclePreviewAnchor(.5f, .5f)),
                        VehicleLayerSpec("highlight", 2, VehiclePreviewAnchor(.4f, .4f)),
                    ),
            )

        assertEquals("climate_cabin_top_view", scene.id)
        assertEquals(2, scene.layers.size)
    }

    @Test
    fun scene_rejectsDuplicateLayerIds() {
        assertThrows(IllegalArgumentException::class.java) {
            VehicleSceneSpec(
                id = "invalid",
                layers =
                    listOf(
                        VehicleLayerSpec("base", 1, VehiclePreviewAnchor(.5f, .5f)),
                        VehicleLayerSpec("base", 2, VehiclePreviewAnchor(.5f, .5f)),
                    ),
            )
        }
    }
}

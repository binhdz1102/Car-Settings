package com.android.car.settings.core.ui

import com.android.car.settings.core.vehicle.VehiclePropertyError
import com.android.car.settings.core.vehicle.VehiclePropertyOperation
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleVisualPolicyTest {
    @Test
    fun unrestrictedPolicyAllowsPreviewAndGuidePlayback() {
        val policy = VehicleUxPolicyState.Unrestricted.toVehicleVisualPolicy()

        assertTrue(policy.allowPreviewTransition)
        assertTrue(policy.allowGuide)
        assertTrue(policy.allowGuidePlayback)
    }

    @Test
    fun restrictedAndUnavailablePoliciesDisableGuide() {
        val restricted =
            VehicleUxPolicyState.Restricted(activeRestrictions = 1).toVehicleVisualPolicy()
        val unavailable =
            VehicleUxPolicyState
                .Unavailable(
                    VehiclePropertyError.ServiceUnavailable(VehiclePropertyOperation.UX_RESTRICTIONS),
                ).toVehicleVisualPolicy()

        assertFalse(restricted.allowGuide)
        assertFalse(restricted.allowGuidePlayback)
        assertFalse(unavailable.allowGuide)
        assertFalse(unavailable.allowGuidePlayback)
    }
}

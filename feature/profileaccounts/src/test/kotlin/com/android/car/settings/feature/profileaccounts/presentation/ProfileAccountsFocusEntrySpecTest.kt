package com.android.car.settings.feature.profileaccounts.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileAccountsFocusEntrySpecTest {
    @Test
    fun currentProfile_isTheFirstFocusableRowBeforeAccountsLoad() {
        val spec =
            profileAccountsRootFocusSpec(
                currentProfileId = 10,
                accountFocusIds = emptyList(),
                canModifyAccounts = false,
                isWorking = true,
            )

        assertEquals("profile-current-10", spec.firstContentFocusId)
        assertEquals(
            mapOf(
                "profile-current-10" to 1,
                "profile-manage-other" to 2,
            ),
            spec.itemIndexByFocusId,
        )
    }

    @Test
    fun absentCurrentProfile_fallsBackToManageProfilesWithoutShiftingPhysicalIndexes() {
        val spec =
            profileAccountsRootFocusSpec(
                currentProfileId = null,
                accountFocusIds = listOf("profile-account-personal/example"),
                canModifyAccounts = true,
                isWorking = false,
            )

        assertEquals("profile-manage-other", spec.firstContentFocusId)
        assertEquals(
            mapOf(
                "profile-manage-other" to 1,
                "profile-master-sync" to 3,
                "profile-account-personal/example" to 4,
                "profile-add-account" to 5,
            ),
            spec.itemIndexByFocusId,
        )
    }
}

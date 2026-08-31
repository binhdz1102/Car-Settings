package com.android.car.settings.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionResultTest {
    @Test
    fun `runAction converts ordinary failure`() {
        val failure = IllegalStateException("failed")

        val result = runAction { throw failure }

        assertThat(result).isEqualTo(ActionResult.Failure("failed", failure))
        assertThat((result as ActionResult.Failure).code).isEqualTo(ActionFailureCode.UNKNOWN)
    }

    @Test
    fun `runAction propagates cancellation`() {
        val cancellation = CancellationException("cancelled")

        val thrown =
            assertThrows(CancellationException::class.java) {
                runAction { throw cancellation }
            }

        assertThat(thrown).isSameInstanceAs(cancellation)
    }

    @Test
    fun `runAction classifies permission failure without parsing its text`() {
        val result = runAction { throw SecurityException("platform detail") }

        assertThat((result as ActionResult.Failure).code)
            .isEqualTo(ActionFailureCode.PERMISSION_DENIED)
        assertThat(result.technicalDetail).contains("SecurityException")
    }

    @Test
    fun `explicit failure code supports policy failures without an exception`() {
        val result =
            ActionResult.Failure(
                message = "Unavailable while driving",
                code = ActionFailureCode.UX_RESTRICTED,
            )

        assertThat(result.code).isEqualTo(ActionFailureCode.UX_RESTRICTED)
        assertThat(result.technicalDetail).isNull()
    }
}

package com.android.car.settings.feature.bluetooth.data

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BluetoothActionRunnerTest {
    @Test
    fun `cancellation is rethrown without conversion to failure`() =
        runTest {
            val cancellation = CancellationException("cancelled")
            var convertedFailure: Throwable? = null
            var observed: Throwable? = null

            try {
                runBluetoothAction(
                    block = { throw cancellation },
                    onFailure = {
                        convertedFailure = it
                        ActionResult.Failure("unexpected", it)
                    },
                )
            } catch (throwable: Throwable) {
                observed = throwable
            }

            assertThat(observed).isSameInstanceAs(cancellation)
            assertThat(convertedFailure).isNull()
        }

    @Test
    fun `ordinary exception is converted to failure`() =
        runTest {
            val exception = IllegalStateException("failed")

            val result =
                runBluetoothAction(
                    block = { throw exception },
                    onFailure = { ActionResult.Failure(checkNotNull(it.message), it) },
                )

            assertThat(result).isEqualTo(ActionResult.Failure("failed", exception))
        }
}

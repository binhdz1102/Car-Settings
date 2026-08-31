package com.android.car.settings.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveOperationTrackerTest {
    @Test
    fun `busy state remains active until every overlapping operation completes`() =
        runTest {
            val tracker = ActiveOperationTracker()
            val firstRelease = CompletableDeferred<Unit>()
            val secondRelease = CompletableDeferred<Unit>()
            val first = launch { tracker.track { firstRelease.await() } }
            val second = launch { tracker.track { secondRelease.await() } }

            runCurrent()
            assertThat(tracker.isActive.value).isTrue()

            firstRelease.complete(Unit)
            first.join()
            assertThat(tracker.isActive.value).isTrue()

            secondRelease.complete(Unit)
            second.join()
            assertThat(tracker.isActive.value).isFalse()
        }

    @Test
    fun `failure cannot leave tracker active`() =
        runTest {
            val tracker = ActiveOperationTracker()
            var failure: IllegalStateException? = null

            try {
                tracker.track { error("failed") }
            } catch (throwable: IllegalStateException) {
                failure = throwable
            }

            assertThat(failure).hasMessageThat().isEqualTo("failed")
            assertThat(tracker.isActive.value).isFalse()
        }

    @Test
    fun `cancellation propagates and cannot leave tracker active`() =
        runTest {
            val tracker = ActiveOperationTracker()
            val job = launch { tracker.track { awaitCancellation() } }

            runCurrent()
            assertThat(tracker.isActive.value).isTrue()

            job.cancelAndJoin()

            assertThat(job.isCancelled).isTrue()
            assertThat(tracker.isActive.value).isFalse()
        }
}

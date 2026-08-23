package com.android.car.settings.feature.bluetooth.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileProxyLeaseManagerTest {
    @Test
    fun `successful use closes proxy exactly once`() =
        runTest {
            val connector = FakeProfileProxyConnector()
            val manager = ProfileProxyLeaseManager(connector, timeoutMillis = 1_000)

            val result = async { manager.withProxy(PROFILE_ID) { "used-$it" } }
            runCurrent()
            connector.connect("proxy")

            assertThat(result.await()).isEqualTo("used-proxy")
            assertThat(connector.closed).containsExactly(PROFILE_ID to "proxy")
        }

    @Test
    fun `exception from operation still closes proxy`() =
        runTest {
            val connector = FakeProfileProxyConnector()
            val manager = ProfileProxyLeaseManager(connector, timeoutMillis = 1_000)
            var failure: Throwable? = null

            val operation =
                launch {
                    try {
                        manager.withProxy(PROFILE_ID) { error("operation failed") }
                    } catch (throwable: Throwable) {
                        failure = throwable
                    }
                }
            runCurrent()
            connector.connect("proxy")
            operation.join()

            assertThat(failure).hasMessageThat().isEqualTo("operation failed")
            assertThat(connector.closed).containsExactly(PROFILE_ID to "proxy")
        }

    @Test
    fun `timeout followed by late callback closes proxy`() =
        runTest {
            val connector = FakeProfileProxyConnector()
            val manager = ProfileProxyLeaseManager(connector, timeoutMillis = 100)
            var failure: Throwable? = null

            val operation =
                launch {
                    try {
                        manager.withProxy(PROFILE_ID) { Unit }
                    } catch (throwable: Throwable) {
                        failure = throwable
                    }
                }
            runCurrent()
            advanceTimeBy(101)
            operation.join()
            connector.connect("late-proxy")

            assertThat(failure).isInstanceOf(TimeoutCancellationException::class.java)
            assertThat(connector.closed).containsExactly(PROFILE_ID to "late-proxy")
        }

    @Test
    fun `caller cancellation followed by late callback closes proxy`() =
        runTest {
            val connector = FakeProfileProxyConnector()
            val manager = ProfileProxyLeaseManager(connector, timeoutMillis = 1_000)

            val operation = launch { manager.withProxy(PROFILE_ID) { Unit } }
            runCurrent()
            operation.cancelAndJoin()
            connector.connect("late-proxy")

            assertThat(operation.isCancelled).isTrue()
            assertThat(connector.closed).containsExactly(PROFILE_ID to "late-proxy")
        }

    @Test
    fun `disconnect before acquisition fails and later callback is closed`() =
        runTest {
            val connector = FakeProfileProxyConnector()
            val manager = ProfileProxyLeaseManager(connector, timeoutMillis = 1_000)
            var failure: Throwable? = null

            val operation =
                launch {
                    try {
                        manager.withProxy(PROFILE_ID) { Unit }
                    } catch (throwable: Throwable) {
                        failure = throwable
                    }
                }
            runCurrent()
            connector.disconnect()
            operation.join()
            connector.connect("late-proxy")

            assertThat(failure).hasMessageThat().contains("disconnected")
            assertThat(connector.closed).containsExactly(PROFILE_ID to "late-proxy")
        }

    @Test
    fun `synchronous callback from rejected request is closed`() =
        runTest {
            val connector =
                FakeProfileProxyConnector(
                    accepted = false,
                    synchronousProxy = "rejected-proxy",
                )
            val manager = ProfileProxyLeaseManager(connector, timeoutMillis = 1_000)
            var failure: Throwable? = null

            try {
                manager.withProxy(PROFILE_ID) { Unit }
            } catch (throwable: Throwable) {
                failure = throwable
            }

            assertThat(failure).hasMessageThat().contains("unavailable")
            assertThat(connector.closed).containsExactly(PROFILE_ID to "rejected-proxy")
        }

    private companion object {
        const val PROFILE_ID = 2
    }
}

private class FakeProfileProxyConnector(
    private val accepted: Boolean = true,
    private val synchronousProxy: String? = null,
) : ProfileProxyConnector<String> {
    val closed = mutableListOf<Pair<Int, String>>()
    private var requestedProfileId: Int? = null
    private var listener: ProfileProxyConnector.Listener<String>? = null

    override fun request(
        profileId: Int,
        listener: ProfileProxyConnector.Listener<String>,
    ): Boolean {
        requestedProfileId = profileId
        this.listener = listener
        synchronousProxy?.let { listener.onConnected(profileId, it) }
        return accepted
    }

    override fun close(
        profileId: Int,
        proxy: String,
    ) {
        closed += profileId to proxy
    }

    fun connect(proxy: String) {
        listener?.onConnected(checkNotNull(requestedProfileId), proxy)
    }

    fun disconnect() {
        listener?.onDisconnected(checkNotNull(requestedProfileId))
    }
}

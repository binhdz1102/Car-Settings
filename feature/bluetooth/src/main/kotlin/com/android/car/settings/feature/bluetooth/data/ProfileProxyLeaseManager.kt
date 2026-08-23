package com.android.car.settings.feature.bluetooth.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * Small lifecycle seam around Bluetooth's callback-based profile proxy API.
 *
 * A proxy has exactly one owner. Before [PendingRequest.await] succeeds the request owns it; after
 * that the returned lease owns it. This lets timeout/cancellation and late callbacks close the
 * proxy deterministically instead of leaking it.
 */
internal interface ProfileProxyConnector<P : Any> {
    fun request(
        profileId: Int,
        listener: Listener<P>,
    ): Boolean

    fun close(
        profileId: Int,
        proxy: P,
    )

    interface Listener<P : Any> {
        fun onConnected(
            profileId: Int,
            proxy: P,
        )

        fun onDisconnected(profileId: Int)
    }
}

internal class ProfileProxyLeaseManager<P : Any>(
    private val connector: ProfileProxyConnector<P>,
    private val timeoutMillis: Long,
) {
    init {
        require(timeoutMillis > 0) { "Profile proxy timeout must be positive" }
    }

    suspend fun <R> withProxy(
        profileId: Int,
        block: (P) -> R,
    ): R {
        val request = PendingRequest(profileId, connector, timeoutMillis)
        val accepted =
            try {
                connector.request(profileId, request.listener)
            } catch (throwable: Throwable) {
                request.abort()
                throw throwable
            }
        if (!accepted) {
            request.abort()
            error("Bluetooth profile $profileId is unavailable")
        }

        val lease = request.await()
        return try {
            block(lease.proxy)
        } finally {
            lease.close()
        }
    }

    private class PendingRequest<P : Any>(
        private val profileId: Int,
        private val connector: ProfileProxyConnector<P>,
        private val timeoutMillis: Long,
    ) {
        private val lock = Any()
        private val result = CompletableDeferred<P>()
        private var state = RequestState.WAITING
        private var deliveredProxy: P? = null

        val listener =
            object : ProfileProxyConnector.Listener<P> {
                override fun onConnected(
                    profileId: Int,
                    proxy: P,
                ) {
                    if (profileId != this@PendingRequest.profileId) {
                        connector.close(profileId, proxy)
                        return
                    }

                    val accepted =
                        synchronized(lock) {
                            if (state == RequestState.WAITING) {
                                deliveredProxy = proxy
                                state = RequestState.DELIVERED
                                true
                            } else {
                                false
                            }
                        }
                    if (accepted) {
                        result.complete(proxy)
                    } else {
                        connector.close(profileId, proxy)
                    }
                }

                override fun onDisconnected(profileId: Int) {
                    if (profileId != this@PendingRequest.profileId) return

                    var proxyToClose: P? = null
                    val shouldFail =
                        synchronized(lock) {
                            when (state) {
                                RequestState.WAITING -> {
                                    state = RequestState.CLOSED
                                    true
                                }

                                RequestState.DELIVERED -> {
                                    proxyToClose = deliveredProxy
                                    deliveredProxy = null
                                    state = RequestState.CLOSED
                                    true
                                }

                                RequestState.ACQUIRED,
                                RequestState.CLOSED,
                                -> false
                            }
                        }
                    proxyToClose?.let { connector.close(profileId, it) }
                    if (shouldFail) {
                        result.completeExceptionally(
                            IllegalStateException("Bluetooth profile $profileId disconnected"),
                        )
                    }
                }
            }

        suspend fun await(): ProfileProxyLease<P> =
            try {
                val proxy = withTimeout(timeoutMillis) { result.await() }
                val acquired =
                    synchronized(lock) {
                        if (
                            state == RequestState.DELIVERED &&
                            deliveredProxy === proxy
                        ) {
                            deliveredProxy = null
                            state = RequestState.ACQUIRED
                            true
                        } else {
                            false
                        }
                    }
                check(acquired) { "Bluetooth profile $profileId disconnected before use" }
                ProfileProxyLease(proxy, ::release)
            } catch (throwable: Throwable) {
                abort()
                throw throwable
            }

        fun abort() {
            var proxyToClose: P? = null
            synchronized(lock) {
                when (state) {
                    RequestState.WAITING -> state = RequestState.CLOSED
                    RequestState.DELIVERED -> {
                        proxyToClose = deliveredProxy
                        deliveredProxy = null
                        state = RequestState.CLOSED
                    }

                    RequestState.ACQUIRED,
                    RequestState.CLOSED,
                    -> Unit
                }
            }
            proxyToClose?.let { connector.close(profileId, it) }
        }

        private fun release(proxy: P) {
            val shouldClose =
                synchronized(lock) {
                    if (state == RequestState.ACQUIRED) {
                        state = RequestState.CLOSED
                        true
                    } else {
                        false
                    }
                }
            if (shouldClose) connector.close(profileId, proxy)
        }
    }
}

private class ProfileProxyLease<P : Any>(
    val proxy: P,
    release: (P) -> Unit,
) : AutoCloseable {
    private val closeAction = OnceCloseAction(proxy, release)

    override fun close() = closeAction.close()
}

private class OnceCloseAction<P : Any>(
    private val proxy: P,
    private val release: (P) -> Unit,
) {
    private val lock = Any()
    private var closed = false

    fun close() {
        val shouldRelease =
            synchronized(lock) {
                if (closed) {
                    false
                } else {
                    closed = true
                    true
                }
            }
        if (shouldRelease) release(proxy)
    }
}

private enum class RequestState {
    WAITING,
    DELIVERED,
    ACQUIRED,
    CLOSED,
}

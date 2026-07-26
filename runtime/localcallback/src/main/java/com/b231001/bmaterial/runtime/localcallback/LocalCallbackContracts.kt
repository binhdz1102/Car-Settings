package com.b231001.bmaterial.runtime.localcallback

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A handle returned by an external callback source.
 *
 * Implementations must release the exact callback instance registered by
 * [LocalCallbackSource.register]. [close] may be called from any thread and must be idempotent.
 */
public fun interface LocalCallbackRegistration : AutoCloseable {
    public override fun close()
}

/**
 * Receives values from an external callback.
 *
 * [emit] is safe to call concurrently and never suspends the source callback thread. [fail] is
 * reserved for fatal source failures. Recoverable domain errors should normally be emitted as part
 * of the application's event type.
 */
public interface LocalCallbackEmitter<T> {
    public fun emit(value: T)

    public fun fail(cause: Throwable)
}

/**
 * Bridges one expensive external callback registration into [LocalCallback].
 *
 * Registration is performed only while at least one local subscriber needs the source. The
 * returned [LocalCallbackRegistration] is closed when the final subscriber leaves, the key is
 * removed, or the owning registry is closed.
 */
public fun interface LocalCallbackSource<T> {
    public fun register(emitter: LocalCallbackEmitter<T>): LocalCallbackRegistration
}

/**
 * Adapts callback-style platform APIs without making the local-callback module depend on them.
 *
 * The same callback object produced by [callbackFactory] is passed to [register] and [unregister].
 * If registration throws, best-effort cleanup is attempted before the original error is rethrown.
 */
public fun <T, C : Any> localCallbackSource(
    callbackFactory: (LocalCallbackEmitter<T>) -> C,
    register: (C) -> Unit,
    unregister: (C) -> Unit
): LocalCallbackSource<T> = LocalCallbackSource { emitter ->
    val callback = callbackFactory(emitter)
    try {
        register(callback)
    } catch (registrationFailure: Throwable) {
        try {
            unregister(callback)
        } catch (cleanupFailure: Throwable) {
            registrationFailure.addSuppressed(cleanupFailure)
        }
        throw registrationFailure
    }

    val closed = AtomicBoolean(false)
    LocalCallbackRegistration {
        if (closed.compareAndSet(false, true)) {
            unregister(callback)
        }
    }
}

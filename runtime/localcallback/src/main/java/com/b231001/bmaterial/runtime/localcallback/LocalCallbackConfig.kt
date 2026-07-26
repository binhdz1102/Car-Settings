package com.b231001.bmaterial.runtime.localcallback

/** Determines which buffered value is discarded when a local subscriber cannot keep up. */
public enum class LocalCallbackOverflowStrategy {
    /** Preserve the newest real-time data by replacing the oldest queued value. */
    DROP_OLDEST,

    /** Preserve already queued events and discard the newly emitted value. */
    DROP_LATEST
}

/**
 * Delivery and lifecycle policy for one shared callback.
 *
 * No policy blocks the external callback thread. Increasing [subscriberBufferCapacity] can reduce
 * drops during bounded bursts, but only an application-level acknowledgement protocol can provide
 * end-to-end lossless delivery.
 */
public data class LocalCallbackConfig(
    public val sourceBufferCapacity: Int = DEFAULT_SOURCE_BUFFER_CAPACITY,
    public val subscriberBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    public val overflowStrategy: LocalCallbackOverflowStrategy =
        LocalCallbackOverflowStrategy.DROP_OLDEST,
    public val replayLatest: Boolean = true,
    public val stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MILLIS
) {
    init {
        require(sourceBufferCapacity > 0) {
            "sourceBufferCapacity must be greater than zero"
        }
        require(subscriberBufferCapacity > 0) {
            "subscriberBufferCapacity must be greater than zero"
        }
        require(stopTimeoutMillis >= 0L) {
            "stopTimeoutMillis must not be negative"
        }
    }

    public companion object {
        public const val DEFAULT_SOURCE_BUFFER_CAPACITY: Int = 256
        public const val DEFAULT_BUFFER_CAPACITY: Int = 64
        public const val DEFAULT_STOP_TIMEOUT_MILLIS: Long = 5_000L

        /** A state/data policy: cache the latest value and debounce short subscriber gaps. */
        public fun state(
            sourceBufferCapacity: Int = DEFAULT_SOURCE_BUFFER_CAPACITY,
            subscriberBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
            overflowStrategy: LocalCallbackOverflowStrategy =
                LocalCallbackOverflowStrategy.DROP_OLDEST,
            stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MILLIS
        ): LocalCallbackConfig = LocalCallbackConfig(
            sourceBufferCapacity = sourceBufferCapacity,
            subscriberBufferCapacity = subscriberBufferCapacity,
            overflowStrategy = overflowStrategy,
            replayLatest = true,
            stopTimeoutMillis = stopTimeoutMillis
        )

        /** An event policy: do not replay an old event and stop immediately when unused. */
        public fun events(
            sourceBufferCapacity: Int = DEFAULT_SOURCE_BUFFER_CAPACITY,
            subscriberBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
            overflowStrategy: LocalCallbackOverflowStrategy =
                LocalCallbackOverflowStrategy.DROP_OLDEST,
            stopTimeoutMillis: Long = 0L
        ): LocalCallbackConfig = LocalCallbackConfig(
            sourceBufferCapacity = sourceBufferCapacity,
            subscriberBufferCapacity = subscriberBufferCapacity,
            overflowStrategy = overflowStrategy,
            replayLatest = false,
            stopTimeoutMillis = stopTimeoutMillis
        )
    }
}

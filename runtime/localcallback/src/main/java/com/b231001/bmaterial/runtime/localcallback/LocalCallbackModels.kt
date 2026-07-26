package com.b231001.bmaterial.runtime.localcallback

/**
 * An identity-based, type-safe key.
 *
 * Keep and reuse the same key instance wherever a source should be shared. Two keys with the same
 * name are intentionally different, preventing unrelated systems from being merged accidentally.
 */
public class LocalCallbackKey<T> private constructor(
    public val name: String
) {
    init {
        require(name.isNotBlank()) { "LocalCallbackKey name must not be blank" }
    }

    public override fun toString(): String = "LocalCallbackKey($name)"

    public companion object {
        public fun <T> create(name: String): LocalCallbackKey<T> = LocalCallbackKey(name)
    }
}

/** Current registration lifecycle for a shared callback source. */
public enum class LocalCallbackStatus {
    IDLE,
    STARTING,
    ACTIVE,
    STOPPING,
    CLOSED
}

/** Cheap observable lifecycle data; high-frequency counters are available through snapshot(). */
public data class LocalCallbackLifecycle(
    public val status: LocalCallbackStatus,
    public val subscriberCount: Int,
    public val isRegistered: Boolean
)

/** Point-in-time diagnostics suitable for tests, dashboards, and production telemetry. */
public data class LocalCallbackSnapshot(
    public val keyName: String,
    public val lifecycle: LocalCallbackLifecycle,
    public val registrationAttempts: Long,
    public val successfulRegistrations: Long,
    public val unregistrations: Long,
    public val unregistrationFailures: Long,
    public val registrationFailures: Long,
    public val fatalSourceFailures: Long,
    public val sourceEvents: Long,
    public val droppedSourceEvents: Long,
    public val enqueuedDeliveries: Long,
    public val droppedDeliveries: Long,
    public val replayDeliveries: Long,
    public val hasLatestValue: Boolean,
    public val lastFailure: String?
)

/** Indicates that a flow was terminated because its registry or key was explicitly closed. */
public class LocalCallbackClosedException(
    message: String
) : IllegalStateException(message)

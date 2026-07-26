package com.b231001.bmaterial.runtime.localcallback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A locally shared stream backed by at most one external callback registration.
 *
 * Every collector receives its own bounded mailbox, so a slow consumer never blocks the external
 * callback thread or another consumer. Use [snapshot] for high-frequency diagnostics without
 * forcing state updates for every source event.
 */
public class LocalCallback<T> internal constructor(
    private val entry: LocalCallbackEntry<T>
) {
    public val key: LocalCallbackKey<T>
        get() = entry.key

    public val config: LocalCallbackConfig
        get() = entry.config

    public val events: Flow<T>
        get() = entry.events

    public val lifecycle: StateFlow<LocalCallbackLifecycle>
        get() = entry.lifecycle

    public fun snapshot(): LocalCallbackSnapshot = entry.snapshot()

    public fun latestValueOrNull(): T? = entry.latestValueOrNull()

    /** Convenience API for callback-oriented callers that do not want to collect a Flow directly. */
    public fun subscribe(
        scope: CoroutineScope,
        onEvent: (T) -> Unit
    ): Job = subscribe(scope = scope, onEvent = onEvent, onFailure = {})

    /**
     * Convenience API with a terminal failure callback.
     *
     * Cancelling the returned job is equivalent to unregistering this local consumer. It does not
     * affect other consumers.
     */
    public fun subscribe(
        scope: CoroutineScope,
        onEvent: (T) -> Unit,
        onFailure: (Throwable) -> Unit
    ): Job = scope.launch {
        try {
            events.collect(onEvent)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            onFailure(failure)
        }
    }
}

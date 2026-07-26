package com.b231001.bmaterial.runtime.localcallback

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class FakeCallbackSource<T>(
    private val synchronousInitialValue: InitialValue<T> = InitialValue.None
) : LocalCallbackSource<T> {
    private val emitters = CopyOnWriteArrayList<LocalCallbackEmitter<T>>()
    private val failNextRegistration = AtomicBoolean(false)

    val registrationCount = AtomicInteger()
    val unregistrationCount = AtomicInteger()
    val activeRegistrationCount = AtomicInteger()
    val maxActiveRegistrationCount = AtomicInteger()

    override fun register(emitter: LocalCallbackEmitter<T>): LocalCallbackRegistration {
        if (failNextRegistration.compareAndSet(true, false)) {
            throw IllegalStateException("synthetic registration failure")
        }

        registrationCount.incrementAndGet()
        val active = activeRegistrationCount.incrementAndGet()
        maxActiveRegistrationCount.updateAndGet { current -> maxOf(current, active) }
        emitters += emitter
        if (synchronousInitialValue is InitialValue.Value) {
            emitter.emit(synchronousInitialValue.value)
        }

        val closed = AtomicBoolean(false)
        return LocalCallbackRegistration {
            if (closed.compareAndSet(false, true)) {
                activeRegistrationCount.decrementAndGet()
                unregistrationCount.incrementAndGet()
            }
        }
    }

    fun failNextRegistration() {
        failNextRegistration.set(true)
    }

    fun emit(value: T) {
        check(emitters.isNotEmpty()) { "No callback has been registered" }
        emitters.last().emit(value)
    }

    fun emitFromRegistration(index: Int, value: T) {
        emitters[index].emit(value)
    }

    fun fail(cause: Throwable) {
        check(emitters.isNotEmpty()) { "No callback has been registered" }
        emitters.last().fail(cause)
    }
}

internal sealed interface InitialValue<out T> {
    data object None : InitialValue<Nothing>

    data class Value<T>(val value: T) : InitialValue<T>
}

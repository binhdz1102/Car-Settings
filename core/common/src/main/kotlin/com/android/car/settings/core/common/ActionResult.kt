package com.android.car.settings.core.common

sealed interface ActionResult {
    data object Success : ActionResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : ActionResult
}

inline fun runAction(block: () -> Unit): ActionResult =
    try {
        block()
        ActionResult.Success
    } catch (throwable: Throwable) {
        ActionResult.Failure(
            message = throwable.message ?: throwable.javaClass.simpleName,
            cause = throwable,
        )
    }

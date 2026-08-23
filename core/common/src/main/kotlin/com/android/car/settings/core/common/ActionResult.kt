package com.android.car.settings.core.common

import kotlinx.coroutines.CancellationException

/** Stable, machine-readable reason for a failed application action. */
enum class ActionFailureCode {
    PERMISSION_DENIED,
    NOT_SUPPORTED,
    NOT_FOUND,
    INVALID_INPUT,
    UX_RESTRICTED,
    SERVICE_UNAVAILABLE,
    OPERATION_REJECTED,
    UNKNOWN,
}

sealed interface ActionResult {
    data object Success : ActionResult

    data class Failure(
        /** Safe fallback text for legacy callers that have not migrated to localized errors yet. */
        val message: String,
        val cause: Throwable? = null,
        val code: ActionFailureCode = cause.toActionFailureCode(),
        /** Diagnostic detail must be logged only and must never replace localized UI copy. */
        val technicalDetail: String? = cause?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" },
    ) : ActionResult
}

inline fun runAction(block: () -> Unit): ActionResult =
    try {
        block()
        ActionResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        exception.toActionFailure()
    }

fun Throwable.toActionFailure(
    fallbackMessage: String = message ?: javaClass.simpleName,
    code: ActionFailureCode = toActionFailureCode(),
): ActionResult.Failure =
    ActionResult.Failure(
        message = fallbackMessage,
        cause = this,
        code = code,
    )

fun Throwable?.toActionFailureCode(): ActionFailureCode =
    when (this) {
        is SecurityException -> ActionFailureCode.PERMISSION_DENIED
        is UnsupportedOperationException -> ActionFailureCode.NOT_SUPPORTED
        is NoSuchElementException -> ActionFailureCode.NOT_FOUND
        is IllegalArgumentException -> ActionFailureCode.INVALID_INPUT
        else -> ActionFailureCode.UNKNOWN
    }

fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

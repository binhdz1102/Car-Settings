package com.android.car.settings.logging

import android.util.Log
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AppTimberTree(
    private val appVersionName: String,
    private val appVersionCode: Int,
    private val gitCommitHash: String,
    private val isDebugBuild: Boolean = true,
) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        isDebugBuild || priority >= Log.INFO

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        val caller = resolveCaller()
        val logTag = tag ?: caller.className
        val header =
            "[${timestamp()}] " +
                "[${priorityName(priority)}] " +
                "[version:$appVersionName:$appVersionCode] " +
                "[commit:$gitCommitHash] " +
                "[class:${caller.className}] " +
                "[function:${caller.methodName}]"
        val fullMessage = appendThrowable(message, t)

        fullMessage
            .lineSequence()
            .ifEmpty { sequenceOf("") }
            .forEach { line ->
                Log.println(priority, logTag, "$header $line")
            }
    }

    private fun timestamp(): String = LocalDateTime.now().format(TIMESTAMP_FORMATTER)

    private fun priorityName(priority: Int): String =
        when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN"
        }

    private fun appendThrowable(
        message: String,
        throwable: Throwable?,
    ): String {
        if (throwable == null) return message

        val stackTrace = Log.getStackTraceString(throwable)
        return if (message.isBlank()) {
            stackTrace
        } else {
            "$message\n$stackTrace"
        }
    }

    private fun resolveCaller(): CallerInfo {
        val stackTrace = Throwable().stackTrace
        val caller =
            stackTrace.firstOrNull { element ->
                !element.className.startsWith(TIMBER_PACKAGE) &&
                    element.className != AppTimberTree::class.java.name &&
                    element.className != Thread::class.java.name &&
                    element.methodName !in LOG_HELPER_METHODS
            }

        return CallerInfo(
            className = caller?.className?.substringAfterLast('.') ?: "UnknownClass",
            methodName = caller?.methodName ?: "unknownMethod",
        )
    }

    private data class CallerInfo(
        val className: String,
        val methodName: String,
    )

    companion object {
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private const val TIMBER_PACKAGE = "timber.log."
        private val LOG_HELPER_METHODS = setOf("logDebug", "logInfo", "logWarn", "logError")
    }
}

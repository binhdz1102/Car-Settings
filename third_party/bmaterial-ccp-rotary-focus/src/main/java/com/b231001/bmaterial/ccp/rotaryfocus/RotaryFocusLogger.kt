package com.b231001.bmaterial.ccp.rotaryfocus

import android.util.Log

/** Runtime logging hook. Logging is intentionally enabled during the hardware-validation phase. */
public object RotaryFocusLogger {
    @Volatile
    public var enabled: Boolean = true

    @Volatile
    public var sink: RotaryLogSink = AndroidRotaryLogSink

    internal fun verbose(message: () -> String) {
        if (enabled) sink.log(RotaryLogLevel.Verbose, message(), null)
    }

    internal fun debug(message: () -> String) {
        if (enabled) sink.log(RotaryLogLevel.Debug, message(), null)
    }

    internal fun warning(message: () -> String) {
        if (enabled) sink.log(RotaryLogLevel.Warning, message(), null)
    }

    internal fun error(message: () -> String, throwable: Throwable? = null) {
        if (enabled) sink.log(RotaryLogLevel.Error, message(), throwable)
    }
}

public fun interface RotaryLogSink {
    public fun log(
        level: RotaryLogLevel,
        message: String,
        throwable: Throwable?
    )
}

public enum class RotaryLogLevel {
    Verbose,
    Debug,
    Warning,
    Error
}

private object AndroidRotaryLogSink : RotaryLogSink {
    private const val TAG: String = "BMaterialRotary"

    override fun log(level: RotaryLogLevel, message: String, throwable: Throwable?) {
        when (level) {
            RotaryLogLevel.Verbose -> Log.v(TAG, message, throwable)
            RotaryLogLevel.Debug -> Log.d(TAG, message, throwable)
            RotaryLogLevel.Warning -> Log.w(TAG, message, throwable)
            RotaryLogLevel.Error -> Log.e(TAG, message, throwable)
        }
    }
}

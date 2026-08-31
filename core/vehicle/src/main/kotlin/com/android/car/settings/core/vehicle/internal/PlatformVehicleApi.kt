package com.android.car.settings.core.vehicle.internal

import com.android.car.settings.core.vehicle.VehiclePropertyAccess
import com.android.car.settings.core.vehicle.VehiclePropertyAreaType
import com.android.car.settings.core.vehicle.VehiclePropertyChangeMode
import com.android.car.settings.core.vehicle.VehiclePropertyStatus
import com.android.car.settings.core.vehicle.VehiclePropertyValueType

internal data class PlatformVehicleSession(
    val properties: PlatformVehiclePropertyGateway,
    val uxRestrictions: PlatformVehicleUxGateway?,
)

internal interface PlatformCarConnector {
    fun open(listener: Listener): PlatformCarRegistration

    interface Listener {
        fun onConnected(session: PlatformVehicleSession)

        fun onDisconnected(description: String?)

        fun onConnectionFailed(description: String)
    }
}

internal fun interface PlatformCarRegistration {
    fun close()
}

internal data class PlatformPropertyConfig(
    val propertyId: Int,
    val valueType: VehiclePropertyValueType?,
    val platformTypeName: String,
    val access: VehiclePropertyAccess,
    val changeMode: VehiclePropertyChangeMode,
    val areaType: VehiclePropertyAreaType,
    val areas: List<PlatformAreaConfig>,
    val minSampleRateHz: Float,
    val maxSampleRateHz: Float,
    val configArray: List<Int> = emptyList(),
)

internal data class PlatformAreaConfig(
    val areaId: Int,
    val access: VehiclePropertyAccess,
    val minValue: Any?,
    val maxValue: Any?,
    val supportedEnumValues: List<Any>,
)

internal data class PlatformPropertyValue(
    val propertyId: Int,
    val areaId: Int,
    val status: VehiclePropertyStatus,
    val timestampNanos: Long,
    val value: Any?,
)

internal sealed interface PlatformPropertyEvent {
    data class Changed(
        val value: PlatformPropertyValue,
    ) : PlatformPropertyEvent

    data class Error(
        val propertyId: Int,
        val areaId: Int,
        val errorCode: Int,
    ) : PlatformPropertyEvent
}

internal object PlatformSetErrorCode {
    const val TRY_AGAIN = 1
    const val INVALID_ARGUMENT = 2
    const val PROPERTY_NOT_AVAILABLE = 3
    const val ACCESS_DENIED = 4
}

internal fun interface PlatformPropertySubscription {
    fun close()
}

internal interface PlatformVehiclePropertyGateway {
    fun getConfig(propertyId: Int): PlatformPropertyConfig?

    fun get(
        valueType: VehiclePropertyValueType,
        propertyId: Int,
        areaId: Int,
    ): PlatformPropertyValue

    fun set(
        valueType: VehiclePropertyValueType,
        propertyId: Int,
        areaId: Int,
        value: Any,
    )

    fun subscribe(
        propertyId: Int,
        areaIds: Set<Int>,
        updateRateHz: Float,
        callback: (PlatformPropertyEvent) -> Unit,
    ): PlatformPropertySubscription
}

internal sealed class PlatformVehicleException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class PermissionDenied(
        message: String,
        cause: Throwable? = null,
    ) : PlatformVehicleException(message, cause)

    class Unavailable(
        val retryable: Boolean,
        val detailCode: Int? = null,
        message: String,
        cause: Throwable? = null,
    ) : PlatformVehicleException(message, cause)

    class InvalidArgument(
        message: String,
        cause: Throwable? = null,
    ) : PlatformVehicleException(message, cause)

    class Service(
        message: String,
        cause: Throwable? = null,
    ) : PlatformVehicleException(message, cause)
}

internal data class PlatformUxRestrictions(
    val requiresDistractionOptimization: Boolean,
    val activeRestrictions: Int,
)

internal fun interface PlatformUxSubscription {
    fun close()
}

internal interface PlatformVehicleUxGateway {
    fun current(): PlatformUxRestrictions?

    fun subscribe(callback: (PlatformUxRestrictions) -> Unit): PlatformUxSubscription
}

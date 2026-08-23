package com.android.car.settings.core.vehicle

/** The three scalar VHAL types supported by the shared client. */
enum class VehiclePropertyValueType {
    BOOLEAN,
    INT,
    FLOAT,
}

/** A typed vehicle property identifier. Construct specs through the companion factories. */
@ConsistentCopyVisibility
data class VehiclePropertySpec<T : Any> internal constructor(
    val propertyId: Int,
    val valueType: VehiclePropertyValueType,
) {
    init {
        require(propertyId != 0) { "A vehicle property ID must be non-zero" }
    }

    companion object {
        fun boolean(propertyId: Int): VehiclePropertySpec<Boolean> = VehiclePropertySpec(propertyId, VehiclePropertyValueType.BOOLEAN)

        fun int(propertyId: Int): VehiclePropertySpec<Int> = VehiclePropertySpec(propertyId, VehiclePropertyValueType.INT)

        fun float(propertyId: Int): VehiclePropertySpec<Float> = VehiclePropertySpec(propertyId, VehiclePropertyValueType.FLOAT)
    }
}

@JvmInline
value class VehiclePropertyArea(
    val areaId: Int,
) {
    companion object {
        val GLOBAL = VehiclePropertyArea(0)
    }
}

enum class VehiclePropertyAccess {
    NONE,
    READ,
    WRITE,
    READ_WRITE,
    ;

    val canRead: Boolean
        get() = this == READ || this == READ_WRITE

    val canWrite: Boolean
        get() = this == WRITE || this == READ_WRITE
}

enum class VehiclePropertyChangeMode {
    STATIC,
    ON_CHANGE,
    CONTINUOUS,
    UNKNOWN,
}

enum class VehiclePropertyAreaType {
    GLOBAL,
    WINDOW,
    MIRROR,
    SEAT,
    DOOR,
    WHEEL,
    VENDOR,
    UNKNOWN,
}

data class VehiclePropertyAreaCapability<T : Any>(
    val area: VehiclePropertyArea,
    val access: VehiclePropertyAccess,
    val minValue: T? = null,
    val maxValue: T? = null,
    val supportedEnumValues: List<T> = emptyList(),
)

data class VehiclePropertyCapability<T : Any>(
    val spec: VehiclePropertySpec<T>,
    val access: VehiclePropertyAccess,
    val changeMode: VehiclePropertyChangeMode,
    val areaType: VehiclePropertyAreaType,
    val areas: List<VehiclePropertyAreaCapability<T>>,
    val minSampleRateHz: Float,
    val maxSampleRateHz: Float,
    /** Property-specific configuration values copied from CarPropertyConfig.configArray. */
    val configArray: List<Int> = emptyList(),
) {
    fun area(area: VehiclePropertyArea): VehiclePropertyAreaCapability<T>? = areas.firstOrNull { it.area == area }
}

enum class VehiclePropertyStatus {
    AVAILABLE,
    UNAVAILABLE,
    ERROR,
}

data class VehiclePropertyValue<T : Any>(
    val spec: VehiclePropertySpec<T>,
    val area: VehiclePropertyArea,
    val value: T?,
    val status: VehiclePropertyStatus,
    val timestampNanos: Long,
)

enum class VehiclePropertyOperation {
    CONNECT,
    CAPABILITY,
    READ,
    WRITE,
    SUBSCRIBE,
    UX_RESTRICTIONS,
}

sealed interface VehiclePropertyError {
    val propertyId: Int?
    val area: VehiclePropertyArea?
    val description: String

    data class Unsupported(
        override val propertyId: Int,
        override val area: VehiclePropertyArea? = null,
        override val description: String = "Vehicle property is not supported",
    ) : VehiclePropertyError

    data class Unavailable(
        override val propertyId: Int,
        override val area: VehiclePropertyArea,
        val retryable: Boolean,
        val detailCode: Int? = null,
        override val description: String = "Vehicle property is temporarily unavailable",
    ) : VehiclePropertyError

    data class PermissionDenied(
        override val propertyId: Int?,
        override val area: VehiclePropertyArea?,
        override val description: String = "Vehicle property permission was denied",
    ) : VehiclePropertyError

    data class ServiceUnavailable(
        val operation: VehiclePropertyOperation,
        override val propertyId: Int? = null,
        override val area: VehiclePropertyArea? = null,
        override val description: String = "CarService is unavailable",
    ) : VehiclePropertyError

    data class Timeout(
        val operation: VehiclePropertyOperation,
        val timeoutMillis: Long,
        override val propertyId: Int? = null,
        override val area: VehiclePropertyArea? = null,
        override val description: String = "Vehicle property operation timed out",
    ) : VehiclePropertyError

    data class InvalidArea(
        override val propertyId: Int,
        override val area: VehiclePropertyArea,
        override val description: String = "Area is not supported by this vehicle property",
    ) : VehiclePropertyError

    data class TypeMismatch(
        override val propertyId: Int,
        val expected: VehiclePropertyValueType,
        val actualTypeName: String,
        override val area: VehiclePropertyArea? = null,
        override val description: String = "Vehicle property type does not match its specification",
    ) : VehiclePropertyError

    data class InvalidValue(
        override val propertyId: Int,
        override val area: VehiclePropertyArea,
        override val description: String = "Value is outside the supported vehicle property contract",
    ) : VehiclePropertyError

    data class WriteNotAllowed(
        override val propertyId: Int,
        override val area: VehiclePropertyArea,
        override val description: String = "Vehicle property is not writable",
    ) : VehiclePropertyError

    data class UxRestricted(
        override val propertyId: Int,
        override val area: VehiclePropertyArea,
        override val description: String = "Vehicle setting is unavailable while driving",
    ) : VehiclePropertyError

    data class WriteRejected(
        override val propertyId: Int,
        override val area: VehiclePropertyArea,
        val platformErrorCode: Int,
        override val description: String = "Vehicle rejected the write request",
    ) : VehiclePropertyError
}

sealed interface VehiclePropertyResult<out T> {
    data class Success<T>(
        val value: T,
    ) : VehiclePropertyResult<T>

    data class Failure(
        val error: VehiclePropertyError,
    ) : VehiclePropertyResult<Nothing>
}

sealed interface VehiclePropertyEvent<out T : Any> {
    data class ValueChanged<T : Any>(
        val value: VehiclePropertyValue<T>,
    ) : VehiclePropertyEvent<T>

    data class Error(
        val error: VehiclePropertyError,
    ) : VehiclePropertyEvent<Nothing>
}

enum class VehicleWriteConfirmation {
    CALLBACK,
    READBACK,
}

sealed interface VehiclePropertyWriteResult<out T : Any> {
    data class Pending<T : Any>(
        val spec: VehiclePropertySpec<T>,
        val area: VehiclePropertyArea,
        val requestedValue: T,
    ) : VehiclePropertyWriteResult<T>

    data class Confirmed<T : Any>(
        val value: VehiclePropertyValue<T>,
        val confirmation: VehicleWriteConfirmation,
    ) : VehiclePropertyWriteResult<T>

    data class Error<T : Any>(
        val spec: VehiclePropertySpec<T>,
        val area: VehiclePropertyArea,
        val requestedValue: T,
        val error: VehiclePropertyError,
    ) : VehiclePropertyWriteResult<T>
}

package com.example.myapplication.core.realcar

/**
 * Nhóm lỗi chuẩn hóa khi làm việc trực tiếp với `CarPropertyManager`.
 *
 * Android Automotive có thể ném nhiều loại exception khác nhau tùy vendor, quyền hệ
 * thống, trạng thái vehicle HAL và trạng thái CarService. Wrapper gom các lỗi đó thành
 * những nhánh rõ nghĩa để tầng gọi có thể quyết định hiển thị stale data, retry, yêu cầu
 * quyền, hoặc bỏ qua property không được xe hỗ trợ.
 */
open class RealCarPropertyException(
    val propertyId: Int,
    val areaId: Int,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class UnsupportedProperty(
        propertyId: Int,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
            message = "Vehicle property không được hỗ trợ: ${RealVehiclePropertyIds.nameOf(propertyId)}",
        )

    class UnsupportedArea(
        propertyId: Int,
        requestedAreaId: Int,
        supportedAreaIds: Set<Int>,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = requestedAreaId,
            message =
                "Area $requestedAreaId không được hỗ trợ cho ${RealVehiclePropertyIds.nameOf(propertyId)}. " +
                    "Các area hợp lệ: $supportedAreaIds",
        )

    class PermissionDenied(
        operation: RealCarPropertyOperation,
        propertyId: Int,
        areaId: Int,
        cause: Throwable? = null,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "Không có quyền ${operation.name.lowercase()} " +
                    RealVehiclePropertyIds.nameOf(propertyId),
            cause = cause,
        )

    class ServiceNotReady(
        operation: String,
        propertyId: Int,
        areaId: Int,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "CarService chưa sẵn sàng cho thao tác $operation trên " +
                    RealVehiclePropertyIds.nameOf(propertyId),
        )

    class ServiceUnavailable(
        operation: String,
        propertyId: Int,
        areaId: Int,
        cause: Throwable? = null,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "CarService không khả dụng trong thao tác $operation trên " +
                    RealVehiclePropertyIds.nameOf(propertyId),
            cause = cause,
        )

    class PropertyUnavailable(
        propertyId: Int,
        areaId: Int,
        status: Int? = null,
        detailedErrorCode: Int? = null,
        retryable: Boolean = false,
        cause: Throwable? = null,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                buildString {
                    append("Vehicle property chưa có dữ liệu khả dụng: ")
                    append(RealVehiclePropertyIds.nameOf(propertyId))
                    status?.let { append(", status=$it") }
                    detailedErrorCode?.let { append(", code=$it") }
                    if (retryable) append(", có thể thử lại")
                },
            cause = cause,
        )

    class TypeMismatch(
        propertyId: Int,
        areaId: Int,
        expectedType: String,
        actualType: String,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "Sai kiểu dữ liệu cho ${RealVehiclePropertyIds.nameOf(propertyId)}. " +
                    "Mong đợi $expectedType nhưng nhận $actualType",
        )

    class InvalidValue(
        propertyId: Int,
        areaId: Int,
        reason: String,
        cause: Throwable? = null,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message = "Giá trị không hợp lệ cho ${RealVehiclePropertyIds.nameOf(propertyId)}: $reason",
            cause = cause,
        )

    class InvalidUpdateRate(
        propertyId: Int,
        areaId: Int,
        updateRateHz: Float,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message = "Update rate $updateRateHz Hz không hợp lệ cho ${RealVehiclePropertyIds.nameOf(propertyId)}",
        )

    class CallbackRegistrationFailed(
        propertyId: Int,
        areaId: Int,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message = "Không đăng ký được callback cho ${RealVehiclePropertyIds.nameOf(propertyId)}",
        )

    class CallbackDispatchFailed(
        propertyId: Int,
        areaId: Int,
        cause: Throwable,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message = "Callback lỗi khi nhận ${RealVehiclePropertyIds.nameOf(propertyId)}",
            cause = cause,
        )

    class PlatformErrorCode(
        propertyId: Int,
        areaId: Int,
        errorCode: Int,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message = "CarPropertyManager trả errorCode=$errorCode cho ${RealVehiclePropertyIds.nameOf(propertyId)}",
        )

    companion object {
        const val UNKNOWN_PROPERTY_ID = -1
    }
}

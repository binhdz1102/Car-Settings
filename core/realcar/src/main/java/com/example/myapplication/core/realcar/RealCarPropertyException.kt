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
    val isRetryable: Boolean = false,
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
            isRetryable = true,
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
            isRetryable = true,
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
            isRetryable = retryable,
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

    /**
     * Framework không hoàn tất async request trong thời hạn yêu cầu.
     *
     * The framework did not complete an asynchronous request within the requested timeout.
     */
    class Timeout(
        operation: RealCarPropertyOperation,
        propertyId: Int,
        areaId: Int,
        timeoutMillis: Long,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "Quá thời gian / Timeout ${timeoutMillis}ms khi " +
                    "${operation.name.lowercase()} ${RealVehiclePropertyIds.nameOf(propertyId)}",
            isRetryable = true,
        )

    /**
     * Request async bị hủy theo lifecycle/coroutine.
     *
     * The asynchronous request was cancelled with its lifecycle/coroutine.
     */
    class Cancelled(
        operation: RealCarPropertyOperation,
        propertyId: Int,
        areaId: Int,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "Đã hủy / Cancelled ${operation.name.lowercase()} " +
                    RealVehiclePropertyIds.nameOf(propertyId),
            isRetryable = true,
        )

    class AsyncOperationFailed(
        operation: RealCarPropertyOperation,
        propertyId: Int,
        areaId: Int,
        errorCode: Int,
        detailedErrorCode: Int,
        retryable: Boolean,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "Async ${operation.name.lowercase()} thất bại / failed cho " +
                    "${RealVehiclePropertyIds.nameOf(propertyId)}: " +
                    "errorCode=$errorCode, detailedErrorCode=$detailedErrorCode",
            isRetryable = retryable,
        )

    /**
     * VHAL đã nhận request nhưng báo lỗi xử lý nội bộ; CarService vẫn đang kết nối.
     *
     * VHAL accepted the request but reported an internal processing failure. This must not
     * be treated as a CarService disconnect. The caller may retry according to its policy.
     */
    class InternalError(
        propertyId: Int,
        areaId: Int,
        cause: Throwable? = null,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "VHAL báo lỗi nội bộ cho ${RealVehiclePropertyIds.nameOf(propertyId)} " +
                    "tại area $areaId; CarService vẫn kết nối",
            cause = cause,
            isRetryable = true,
        )

    /**
     * Runtime failure không thuộc taxonomy chuẩn của Android Automotive.
     *
     * An unexpected framework/vendor runtime failure. It is kept separate from
     * [ServiceUnavailable] so one malformed property cannot trigger a service reconnect.
     */
    class PlatformFailure(
        operation: RealCarPropertyOperation,
        propertyId: Int,
        areaId: Int,
        cause: Throwable,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "Framework/vendor lỗi khi ${operation.name.lowercase()} " +
                    "${RealVehiclePropertyIds.nameOf(propertyId)} tại area $areaId: " +
                    (cause.message ?: cause.javaClass.simpleName),
            cause = cause,
        )

    class WriteVerificationFailed(
        propertyId: Int,
        areaId: Int,
        expectedValue: Any?,
        actualValue: Any?,
    ) : RealCarPropertyException(
            propertyId = propertyId,
            areaId = areaId,
            message =
                "Xác minh ghi thất bại / Write verification failed cho " +
                    "${RealVehiclePropertyIds.nameOf(propertyId)}: " +
                    "expected=$expectedValue, actual=$actualValue",
        )

    companion object {
        const val UNKNOWN_PROPERTY_ID = -1
    }
}

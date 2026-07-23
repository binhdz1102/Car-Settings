package com.example.myapplication.core.realcar

/**
 * Nguồn dữ liệu trả về sau một thao tác với `CarPropertyManager`.
 *
 * `REMOTE` nghĩa là dữ liệu vừa được lấy hoặc ghi qua CarService thật. `CALLBACK` đến từ
 * luồng event của vehicle HAL. `CACHE` là giá trị cuối cùng wrapper giữ lại để UI có thể
 * hiển thị dữ liệu cũ khi xe tạm thời chưa sẵn sàng. `LOCAL` dùng cho các thao tác chỉ
 * ghi nhận cục bộ, chẳng hạn đăng ký callback trong lúc CarService đang kết nối.
 */
enum class RealCarPropertyValueSource {
    REMOTE,
    CALLBACK,
    CACHE,
    LOCAL,
}

/**
 * Result an toàn cho API vehicle property thật.
 *
 * Các hàm `try...` trả kiểu này để caller xử lý lỗi quyền, lỗi property chưa sẵn sàng,
 * lỗi service mất kết nối, hoặc lỗi sai kiểu dữ liệu mà không cần `try/catch` ở UI.
 */
sealed class RealCarPropertyResult<out T> {
    data class Success<T>(
        val value: T,
        val source: RealCarPropertyValueSource,
    ) : RealCarPropertyResult<T>()

    data class Failure(
        val error: RealCarPropertyException,
        val staleValue: RealCarPropertyValue? = null,
    ) : RealCarPropertyResult<Nothing>()

    fun getOrNull(): T? =
        when (this) {
            is Success -> value
            is Failure -> null
        }

    fun getOrThrow(): T =
        when (this) {
            is Success -> value
            is Failure -> throw error
        }
}

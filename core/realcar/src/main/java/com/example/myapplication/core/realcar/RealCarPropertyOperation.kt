package com.example.myapplication.core.realcar

/**
 * Nhóm thao tác đang thực hiện với vehicle property thật.
 *
 * Giá trị này được đưa vào lỗi để caller biết lỗi phát sinh ở bước đọc, ghi hay subscribe.
 * Ví dụ cùng một property có thể được phép đọc nhưng không được phép ghi; khi đó wrapper
 * trả [RealCarPropertyException.PermissionDenied] với operation là [WRITE].
 */
enum class RealCarPropertyOperation {
    READ,
    WRITE,
    OBSERVE,
}

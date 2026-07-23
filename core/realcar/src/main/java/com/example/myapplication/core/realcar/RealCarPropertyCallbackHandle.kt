package com.example.myapplication.core.realcar

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handle đại diện cho một lượt đăng ký lắng nghe vehicle property thật.
 *
 * Khi gọi [close], callback được gỡ khỏi danh sách nội bộ và wrapper sẽ hủy subscribe
 * với `CarPropertyManager` nếu không còn listener nào quan tâm property đó. Handle dùng
 * [AtomicBoolean] để thao tác đóng là idempotent, giúp Fragment/Activity có thể gọi nhiều
 * lần trong các nhánh lifecycle mà không tạo lỗi unregister trùng.
 */
class RealCarPropertyCallbackHandle(
    private val onClose: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            onClose()
        }
    }
}

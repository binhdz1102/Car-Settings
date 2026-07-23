package com.example.myapplication.core.realcar

/**
 * Trạng thái kết nối từ ứng dụng đến CarService thật của Android Automotive.
 *
 * Luồng xử lý chuẩn là `DISCONNECTED -> CONNECTING -> CONNECTED`. Khi xe khởi động lại,
 * CarService bị chết, hoặc ứng dụng chủ động đóng kết nối, wrapper sẽ chuyển sang
 * `DISCONNECTED` hoặc `CLOSED` để các hàm đọc/ghi có thể trả lỗi có kiểm soát thay vì
 * làm crash tầng UI.
 */
enum class RealCarConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CLOSED,
}

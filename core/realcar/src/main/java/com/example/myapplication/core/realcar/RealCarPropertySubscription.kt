package com.example.myapplication.core.realcar

/**
 * Mô tả một yêu cầu observe vehicle property thật.
 *
 * Kiểu này dùng cho các màn hình cần theo dõi nhiều property cùng lúc. Caller gom danh
 * sách request và đưa vào `observeProperties(...)`; manager sẽ đăng ký callback cục bộ
 * theo từng `(propertyId, areaId)` nhưng chỉ giữ một subscription platform cho mỗi
 * property với update rate cao nhất đang cần.
 */
data class RealCarPropertySubscription(
    val propertyId: Int,
    val areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
    val updateRateHz: Float = RealCarPropertyManager.SENSOR_RATE_ONCHANGE,
)

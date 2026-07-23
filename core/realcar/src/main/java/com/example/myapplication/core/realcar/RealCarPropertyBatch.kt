package com.example.myapplication.core.realcar

/**
 * Yêu cầu đọc dùng cho batch không đồng nhất kiểu.
 *
 * A heterogeneous batch-read request. [expectedType] is optional; when supplied, the
 * manager validates the requested type before sending the request to CarService.
 */
data class RealCarPropertyReadRequest(
    val propertyId: Int,
    val areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
    val expectedType: Class<*>? = null,
)

/**
 * Yêu cầu ghi dùng cho batch.
 *
 * A batch-write request. Keep [waitForPropertyUpdate] enabled for safety-critical UI so
 * success means VHAL confirmed the update. Disable it only for fire-and-forget telemetry.
 */
data class RealCarPropertyWriteRequest(
    val propertyId: Int,
    val areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
    val value: Any,
    val expectedType: Class<*>? = value.javaClass,
    val waitForPropertyUpdate: Boolean = true,
    val updateRateHz: Float = RealCarPropertyManager.SENSOR_RATE_ONCHANGE,
)

/**
 * Tùy chọn hiệu năng cho batch lớn.
 *
 * Performance options for large batches. Requests are split to avoid Binder transaction
 * pressure and chunks are executed concurrently with a bounded level of parallelism.
 * [fallbackToSynchronousRead] retries only async internal failures, which is useful for VHAL
 * implementations that advertise async support but do not fully implement it.
 */
data class RealCarBatchOptions(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val maxRequestsPerChunk: Int = DEFAULT_MAX_REQUESTS_PER_CHUNK,
    val maxConcurrentChunks: Int = DEFAULT_MAX_CONCURRENT_CHUNKS,
    val fallbackToSynchronousRead: Boolean = true,
) {
    init {
        require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS) {
            "timeoutMillis must be in 1..$MAX_TIMEOUT_MILLIS"
        }
        require(maxRequestsPerChunk in 1..MAX_REQUESTS_PER_CHUNK) {
            "maxRequestsPerChunk must be in 1..$MAX_REQUESTS_PER_CHUNK"
        }
        require(maxConcurrentChunks in 1..MAX_CONCURRENT_CHUNKS) {
            "maxConcurrentChunks must be in 1..$MAX_CONCURRENT_CHUNKS"
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val DEFAULT_MAX_REQUESTS_PER_CHUNK = 100
        const val DEFAULT_MAX_CONCURRENT_CHUNKS = 4
        const val MAX_TIMEOUT_MILLIS = 60_000L
        const val MAX_REQUESTS_PER_CHUNK = 500
        const val MAX_CONCURRENT_CHUNKS = 8
    }
}

data class RealCarBatchItem<T>(
    val requestIndex: Int,
    val propertyId: Int,
    val areaId: Int,
    val result: RealCarPropertyResult<T>,
)

/**
 * Kết quả batch luôn chứa một item cho mỗi request đầu vào và giữ nguyên thứ tự.
 *
 * A batch result always contains one item per input request in the original order.
 * Partial success is expected: inspect [successCount], [failureCount], or each item.
 */
data class RealCarBatchResult<T>(
    val items: List<RealCarBatchItem<T>>,
    val elapsedRealtimeMillis: Long,
) {
    val successCount: Int
        get() = items.count { it.result is RealCarPropertyResult.Success }

    val failureCount: Int
        get() = items.size - successCount

    val isCompleteSuccess: Boolean
        get() = items.isNotEmpty() && failureCount == 0

    fun failures(): List<RealCarBatchItem<T>> = items.filter { it.result is RealCarPropertyResult.Failure }
}

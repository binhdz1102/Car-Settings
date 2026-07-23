package com.example.myapplication.core.realcar

/**
 * Metadata bất biến của một area.
 *
 * Immutable per-area metadata. Min/max values are defensively copied when they are arrays.
 */
data class RealCarPropertyAreaInfo(
    val areaId: Int,
    val access: RealCarPropertyAccess,
    val minimumValue: Any?,
    val maximumValue: Any?,
) {
    val canRead: Boolean
        get() = access == RealCarPropertyAccess.READ || access == RealCarPropertyAccess.READ_WRITE

    val canWrite: Boolean
        get() = access == RealCarPropertyAccess.WRITE || access == RealCarPropertyAccess.READ_WRITE
}

/**
 * Metadata property được chuyển đổi từ `CarPropertyConfig`.
 *
 * Public, platform-independent view of `CarPropertyConfig`, suitable for validation,
 * diagnostics, and dynamically generated controls.
 */
data class RealCarPropertyInfo(
    val propertyId: Int,
    val name: String,
    val valueClass: Class<*>,
    val access: RealCarPropertyAccess,
    val changeMode: RealCarPropertyChangeMode,
    val areaType: Int,
    val areas: List<RealCarPropertyAreaInfo>,
    val minimumSampleRateHz: Float,
    val maximumSampleRateHz: Float,
    val configArray: List<Int>,
) {
    val canRead: Boolean
        get() = access == RealCarPropertyAccess.READ || access == RealCarPropertyAccess.READ_WRITE

    val canWrite: Boolean
        get() = access == RealCarPropertyAccess.WRITE || access == RealCarPropertyAccess.READ_WRITE

    val readableAreas: List<RealCarPropertyAreaInfo>
        get() = areas.filter(RealCarPropertyAreaInfo::canRead)

    val writableAreas: List<RealCarPropertyAreaInfo>
        get() = areas.filter(RealCarPropertyAreaInfo::canWrite)
}

enum class RealCarPropertyAccess {
    NONE,
    READ,
    WRITE,
    READ_WRITE,
}

enum class RealCarPropertyChangeMode {
    STATIC,
    ON_CHANGE,
    CONTINUOUS,
    UNKNOWN,
}

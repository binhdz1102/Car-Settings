package com.android.car.settings.core.vehicle

import com.android.car.settings.core.vehicle.internal.PlatformAreaConfig
import com.android.car.settings.core.vehicle.internal.PlatformPropertyConfig
import com.android.car.settings.core.vehicle.internal.PlatformPropertyEvent
import com.android.car.settings.core.vehicle.internal.PlatformPropertySubscription
import com.android.car.settings.core.vehicle.internal.PlatformPropertyValue
import com.android.car.settings.core.vehicle.internal.PlatformUxRestrictions
import com.android.car.settings.core.vehicle.internal.PlatformUxSubscription
import com.android.car.settings.core.vehicle.internal.PlatformVehiclePropertyGateway
import com.android.car.settings.core.vehicle.internal.PlatformVehicleSession
import com.android.car.settings.core.vehicle.internal.PlatformVehicleUxGateway

internal class TestPropertyGateway : PlatformVehiclePropertyGateway {
    val configs = mutableMapOf<Int, PlatformPropertyConfig>()
    val values = mutableMapOf<Pair<Int, Int>, PlatformPropertyValue>()
    val subscriptions = mutableListOf<TestPropertySubscription>()
    val setCalls = mutableListOf<SetCall>()

    var configFailure: RuntimeException? = null
    var getFailure: RuntimeException? = null
    var setFailure: RuntimeException? = null
    var subscribeFailure: RuntimeException? = null
    var onSet: ((SetCall) -> Unit)? = null

    override fun getConfig(propertyId: Int): PlatformPropertyConfig? {
        configFailure?.let { throw it }
        return configs[propertyId]
    }

    override fun get(
        valueType: VehiclePropertyValueType,
        propertyId: Int,
        areaId: Int,
    ): PlatformPropertyValue {
        getFailure?.let { throw it }
        return requireNotNull(values[propertyId to areaId])
    }

    override fun set(
        valueType: VehiclePropertyValueType,
        propertyId: Int,
        areaId: Int,
        value: Any,
    ) {
        setFailure?.let { throw it }
        val call = SetCall(valueType, propertyId, areaId, value)
        setCalls += call
        onSet?.invoke(call)
    }

    override fun subscribe(
        propertyId: Int,
        areaIds: Set<Int>,
        updateRateHz: Float,
        callback: (PlatformPropertyEvent) -> Unit,
    ): PlatformPropertySubscription {
        subscribeFailure?.let { throw it }
        return TestPropertySubscription(propertyId, areaIds, updateRateHz, callback)
            .also(subscriptions::add)
    }

    fun emit(event: PlatformPropertyEvent) {
        subscriptions
            .filterNot { it.closed }
            .filter { subscription ->
                when (event) {
                    is PlatformPropertyEvent.Changed ->
                        subscription.propertyId == event.value.propertyId &&
                            (subscription.areaIds.isEmpty() || event.value.areaId in subscription.areaIds)
                    is PlatformPropertyEvent.Error ->
                        subscription.propertyId == event.propertyId &&
                            (subscription.areaIds.isEmpty() || event.areaId in subscription.areaIds)
                }
            }.forEach { it.callback(event) }
    }
}

internal data class SetCall(
    val valueType: VehiclePropertyValueType,
    val propertyId: Int,
    val areaId: Int,
    val value: Any,
)

internal class TestPropertySubscription(
    val propertyId: Int,
    val areaIds: Set<Int>,
    val updateRateHz: Float,
    val callback: (PlatformPropertyEvent) -> Unit,
) : PlatformPropertySubscription {
    var closed = false
        private set

    override fun close() {
        closed = true
    }
}

internal class TestUxGateway(
    var currentValue: PlatformUxRestrictions? = null,
) : PlatformVehicleUxGateway {
    private var callback: ((PlatformUxRestrictions) -> Unit)? = null
    var closed = false
        private set

    override fun current(): PlatformUxRestrictions? = currentValue

    override fun subscribe(callback: (PlatformUxRestrictions) -> Unit): PlatformUxSubscription {
        this.callback = callback
        return PlatformUxSubscription {
            closed = true
            this.callback = null
        }
    }

    fun emit(value: PlatformUxRestrictions) {
        currentValue = value
        callback?.invoke(value)
    }
}

internal fun testSession(
    gateway: PlatformVehiclePropertyGateway = TestPropertyGateway(),
    uxGateway: PlatformVehicleUxGateway? = null,
) = PlatformVehicleSession(gateway, uxGateway)

internal fun intConfig(
    propertyId: Int = TEST_PROPERTY_ID,
    areas: List<PlatformAreaConfig> =
        listOf(
            PlatformAreaConfig(
                areaId = TEST_AREA_LEFT,
                access = VehiclePropertyAccess.READ_WRITE,
                minValue = 0,
                maxValue = 10,
                supportedEnumValues = emptyList(),
            ),
        ),
    access: VehiclePropertyAccess = VehiclePropertyAccess.READ_WRITE,
) = PlatformPropertyConfig(
    propertyId = propertyId,
    valueType = VehiclePropertyValueType.INT,
    platformTypeName = Int::class.javaObjectType.name,
    access = access,
    changeMode = VehiclePropertyChangeMode.ON_CHANGE,
    areaType = VehiclePropertyAreaType.SEAT,
    areas = areas,
    minSampleRateHz = 0f,
    maxSampleRateHz = 0f,
)

internal fun intValue(
    value: Int,
    propertyId: Int = TEST_PROPERTY_ID,
    areaId: Int = TEST_AREA_LEFT,
    status: VehiclePropertyStatus = VehiclePropertyStatus.AVAILABLE,
    timestampNanos: Long = 1L,
) = PlatformPropertyValue(propertyId, areaId, status, timestampNanos, value)

internal const val TEST_PROPERTY_ID = 0x11400100
internal const val TEST_BOOLEAN_PROPERTY_ID = 0x11200101
internal const val TEST_FLOAT_PROPERTY_ID = 0x11600102
internal const val TEST_AREA_LEFT = 0x1
internal const val TEST_AREA_RIGHT = 0x4

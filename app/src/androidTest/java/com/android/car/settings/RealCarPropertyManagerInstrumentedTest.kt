package com.android.car.settings

import android.car.VehicleAreaSeat
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.core.realcar.RealCarBatchOptions
import com.example.myapplication.core.realcar.RealCarConnectionState
import com.example.myapplication.core.realcar.RealCarProperty
import com.example.myapplication.core.realcar.RealCarPropertyException
import com.example.myapplication.core.realcar.RealCarPropertyManager
import com.example.myapplication.core.realcar.RealCarPropertyReadRequest
import com.example.myapplication.core.realcar.RealCarPropertyResult
import com.example.myapplication.core.realcar.RealCarPropertyWriteRequest
import com.example.myapplication.core.realcar.RealVehiclePropertyIds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealCarPropertyManagerInstrumentedTest {
    private lateinit var manager: RealCarPropertyManager

    @Before
    fun setUp() =
        runBlocking {
            manager = RealCarPropertyManager(ApplicationProvider.getApplicationContext())
            assertTrue(manager.connect() is RealCarPropertyResult.Success)
        }

    @After
    fun tearDown() {
        manager.close()
    }

    @Test
    fun readsTypedScalarVectorAndMetadataFromRealVhal() =
        runBlocking {
            val make =
                manager.read(
                    RealCarProperty.string(RealVehiclePropertyIds.INFO_MAKE),
                )
            val fuelTypes =
                manager.read(
                    RealCarProperty.intArray(RealVehiclePropertyIds.INFO_FUEL_TYPE),
                )
            val temperature =
                manager.read(
                    RealCarProperty.float(
                        RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                        VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    ),
                )
            val metadata =
                manager
                    .getPropertyInfo(RealVehiclePropertyIds.HVAC_TEMPERATURE_SET)
                    .getOrThrow()

            assertTrue(make.getOrThrow().isNotBlank())
            assertTrue(fuelTypes.getOrThrow().isNotEmpty())
            assertTrue(temperature.getOrThrow() in 17.5f..32.5f)
            assertEquals(Float::class.javaObjectType, metadata.valueClass)
            assertTrue(metadata.areas.any { it.areaId == VehicleAreaSeat.SEAT_ROW_1_LEFT })
        }

    @Test
    fun inventoriesAndReadsEveryVisibleReadableAreaFromRealVhal() =
        runBlocking {
            val infos = manager.getPropertyInfos().getOrThrow()
            assertTrue(infos.isNotEmpty())
            assertEquals(infos.size, infos.map { it.propertyId }.distinct().size)
            assertTrue(infos.any { it.propertyId == RealVehiclePropertyIds.HVAC_TEMPERATURE_SET })

            val requests =
                infos.flatMap { info ->
                    info.readableAreas.map { area ->
                        RealCarPropertyReadRequest(
                            propertyId = info.propertyId,
                            areaId = area.areaId,
                            expectedType = info.valueClass,
                        )
                    }
                }
            val batch =
                manager.tryGetProperties(
                    requests = requests,
                    options =
                        RealCarBatchOptions(
                            timeoutMillis = 15_000,
                            maxRequestsPerChunk = 80,
                            maxConcurrentChunks = 4,
                        ),
                )

            assertEquals(requests.size, batch.items.size)
            assertEquals(requests.indices.toList(), batch.items.map { it.requestIndex })
            assertTrue(batch.successCount > 0)

            val validatedTypes = mutableSetOf<String>()
            batch.items.forEach { item ->
                val success = item.result as? RealCarPropertyResult.Success ?: return@forEach
                val expectedType = checkNotNull(requests[item.requestIndex].expectedType)
                when (expectedType) {
                    Boolean::class.javaObjectType -> success.value.asBoolean()
                    Int::class.javaObjectType -> success.value.asInt()
                    Long::class.javaObjectType -> success.value.asLong()
                    Float::class.javaObjectType -> success.value.asFloat()
                    String::class.java -> success.value.asString()
                    Array<Int>::class.java -> success.value.asIntArray()
                    Array<Long>::class.java -> success.value.asLongArray()
                    Array<Float>::class.java -> success.value.asFloatArray()
                    ByteArray::class.java -> success.value.asByteArray()
                    Array<Any?>::class.java -> success.value.asMixed()
                    else -> throw AssertionError("Unhandled VHAL value type: ${expectedType.name}")
                }
                validatedTypes += expectedType.simpleName
            }
            assertTrue(validatedTypes.containsAll(listOf("Boolean", "Integer", "Float", "String")))

            val failedItems = batch.failures()
            val errors =
                failedItems.map { (it.result as RealCarPropertyResult.Failure).error }
            assertFalse(errors.any { it is RealCarPropertyException.TypeMismatch })
            assertFalse(errors.any { it is RealCarPropertyException.UnsupportedArea })
            assertFalse(errors.any { it is RealCarPropertyException.UnsupportedProperty })
            assertFalse(errors.any { it is RealCarPropertyException.ServiceUnavailable })
            assertEquals(RealCarConnectionState.CONNECTED, manager.connectionState.value)

            Log.i(
                TEST_TAG,
                "VHAL inventory: configs=${infos.size}, readableAreas=${requests.size}, " +
                    "writableAreas=${infos.sumOf { it.writableAreas.size }}, " +
                    "types=${infos.groupingBy { it.valueClass.simpleName }.eachCount()}, " +
                    "validatedTypes=$validatedTypes, " +
                    "success=${batch.successCount}, failures=${errors.groupingBy {
                        it.javaClass.simpleName
                    }.eachCount()}",
            )
            Log.i(
                TEST_TAG,
                "VHAL unavailable details: " +
                    failedItems.joinToString { item ->
                        val error = (item.result as RealCarPropertyResult.Failure).error
                        "0x${item.propertyId.toUInt().toString(16)}[${item.areaId}]=" +
                            "${error.javaClass.simpleName}(${error.message})"
                    },
            )
            Unit
        }

    @Test
    fun confirmedWritesRoundTripAndRestoreOriginalValues() =
        runBlocking {
            val driverTemperature =
                RealCarProperty.float(
                    RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                )
            val passengerTemperature =
                RealCarProperty.float(
                    RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    VehicleAreaSeat.SEAT_ROW_1_RIGHT,
                )
            val fan =
                RealCarProperty.int(
                    RealVehiclePropertyIds.HVAC_FAN_SPEED,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                )
            val ac =
                RealCarProperty.boolean(
                    RealVehiclePropertyIds.HVAC_AC_ON,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                )
            val originalDriverTemperature = manager.read(driverTemperature).getOrThrow()
            val originalPassengerTemperature = manager.read(passengerTemperature).getOrThrow()
            val originalFan = manager.read(fan).getOrThrow()
            val originalAc = manager.read(ac).getOrThrow()

            try {
                val targetDriverTemperature =
                    if (originalDriverTemperature < 32.5f) {
                        originalDriverTemperature + 0.5f
                    } else {
                        originalDriverTemperature - 0.5f
                    }
                val targetPassengerTemperature =
                    if (originalPassengerTemperature < 32.5f) {
                        originalPassengerTemperature + 0.5f
                    } else {
                        originalPassengerTemperature - 0.5f
                    }
                val targetFan = if (originalFan < 7) originalFan + 1 else originalFan - 1

                assertTrue(manager.write(driverTemperature, targetDriverTemperature) is RealCarPropertyResult.Success)
                assertTrue(
                    manager.write(passengerTemperature, targetPassengerTemperature) is
                        RealCarPropertyResult.Success,
                )
                assertTrue(manager.write(fan, targetFan) is RealCarPropertyResult.Success)
                assertTrue(manager.write(ac, !originalAc) is RealCarPropertyResult.Success)

                assertEquals(targetDriverTemperature, manager.read(driverTemperature).getOrThrow())
                assertEquals(targetPassengerTemperature, manager.read(passengerTemperature).getOrThrow())
                assertEquals(targetFan, manager.read(fan).getOrThrow())
                assertEquals(!originalAc, manager.read(ac).getOrThrow())

                val confirmedBatch =
                    manager.trySetProperties(
                        listOf(
                            driverTemperature.writeRequest(targetDriverTemperature),
                            passengerTemperature.writeRequest(targetPassengerTemperature),
                            fan.writeRequest(targetFan),
                            ac.writeRequest(!originalAc),
                        ),
                    )
                assertEquals(4, confirmedBatch.successCount)

                val orderedDuplicateWrites =
                    manager.trySetProperties(
                        listOf(
                            driverTemperature.writeRequest(originalDriverTemperature),
                            driverTemperature.writeRequest(targetDriverTemperature),
                            driverTemperature.writeRequest(originalDriverTemperature),
                        ),
                    )
                assertEquals(3, orderedDuplicateWrites.successCount)
                assertEquals(originalDriverTemperature, manager.read(driverTemperature).getOrThrow())
            } finally {
                manager.write(driverTemperature, originalDriverTemperature)
                manager.write(passengerTemperature, originalPassengerTemperature)
                manager.write(fan, originalFan)
                manager.write(ac, originalAc)
            }
        }

    @Test
    fun rejectsInvalidWritesAndInvalidSubscriptionBeforeChangingVhal() =
        runBlocking {
            val writeBatch =
                manager.trySetProperties(
                    listOf(
                        RealCarPropertyWriteRequest(
                            propertyId = RealVehiclePropertyIds.INFO_MAKE,
                            value = "must-not-be-written",
                            expectedType = String::class.java,
                        ),
                        RealCarProperty
                            .float(
                                RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                                VehicleAreaSeat.SEAT_ROW_1_LEFT,
                            ).writeRequest(99f),
                    ),
                )
            val writeErrors =
                writeBatch.items.map {
                    (it.result as RealCarPropertyResult.Failure).error
                }
            assertTrue(writeErrors.any { it is RealCarPropertyException.PermissionDenied })
            assertTrue(writeErrors.any { it is RealCarPropertyException.InvalidValue })

            val callback =
                object : RealCarPropertyManager.CarPropertyEventCallback {
                    override fun onChangeEvent(value: com.example.myapplication.core.realcar.RealCarPropertyValue) = Unit
                }
            val invalidRate =
                manager.registerCallbackSafely(
                    callback = callback,
                    propertyId = RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    updateRateHz = -1f,
                )
            assertTrue(
                (invalidRate as RealCarPropertyResult.Failure).error is
                    RealCarPropertyException.InvalidUpdateRate,
            )
        }

    @Test
    fun largeAsyncBatchIsOrderedAndSupportsPartialFailures() =
        runBlocking {
            val validRequests =
                listOf(
                    RealCarProperty
                        .float(
                            RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                            VehicleAreaSeat.SEAT_ROW_1_LEFT,
                        ).asReadRequest(),
                    RealCarProperty.string(RealVehiclePropertyIds.INFO_MAKE).asReadRequest(),
                    RealCarProperty.intArray(RealVehiclePropertyIds.INFO_FUEL_TYPE).asReadRequest(),
                )
            val largeBatch =
                manager.tryGetProperties(
                    requests = List(240) { validRequests[it % validRequests.size] },
                    options =
                        RealCarBatchOptions(
                            maxRequestsPerChunk = 60,
                            maxConcurrentChunks = 4,
                        ),
                )

            assertEquals(240, largeBatch.items.size)
            assertEquals((0 until 240).toList(), largeBatch.items.map { it.requestIndex })
            assertEquals(
                "Failures: " +
                    largeBatch
                        .failures()
                        .groupingBy {
                            (it.result as RealCarPropertyResult.Failure).error.let { error ->
                                "${error.javaClass.simpleName}:${error.message}"
                            }
                        }.eachCount(),
                240,
                largeBatch.successCount,
            )

            val errorBatch =
                manager.tryGetProperties(
                    listOf(
                        validRequests.first(),
                        RealCarPropertyReadRequest(0x12345678),
                        RealCarPropertyReadRequest(
                            propertyId = RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                            areaId = 999,
                            expectedType = Float::class.javaObjectType,
                        ),
                        RealCarPropertyReadRequest(
                            propertyId = RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                            areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
                            expectedType = String::class.java,
                        ),
                    ),
                )

            assertEquals(1, errorBatch.successCount)
            assertEquals(3, errorBatch.failureCount)
            assertTrue(
                errorBatch.failures().any {
                    (it.result as RealCarPropertyResult.Failure).error is
                        RealCarPropertyException.UnsupportedProperty
                },
            )
            assertTrue(
                errorBatch.failures().any {
                    (it.result as RealCarPropertyResult.Failure).error is
                        RealCarPropertyException.UnsupportedArea
                },
            )
            assertTrue(
                errorBatch.failures().any {
                    (it.result as RealCarPropertyResult.Failure).error is
                        RealCarPropertyException.TypeMismatch
                },
            )
        }

    @Test
    fun typedFlowReceivesConfirmedWrite() =
        runBlocking {
            val property =
                RealCarProperty.float(
                    RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                )
            val original = manager.read(property).getOrThrow()
            val target = if (original < 32.5f) original + 0.5f else original - 0.5f

            try {
                val observed =
                    async {
                        withTimeout(5_000) {
                            manager
                                .observe(property)
                                .first {
                                    (it as? RealCarPropertyResult.Success)?.value == target
                                }.getOrThrow()
                        }
                    }
                assertTrue(manager.write(property, target) is RealCarPropertyResult.Success)
                assertEquals(target, observed.await())
            } finally {
                manager.write(property, original)
            }
        }

    private companion object {
        const val TEST_TAG = "RealCarPropertyTest"
    }
}

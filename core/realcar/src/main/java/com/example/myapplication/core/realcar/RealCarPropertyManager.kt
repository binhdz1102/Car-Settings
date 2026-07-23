@file:Suppress("DEPRECATION", "LargeClass", "MaxLineLength", "ReturnCount", "TooGenericExceptionCaught", "TooManyFunctions")

package com.example.myapplication.core.realcar

import android.car.Car
import android.car.CarNotConnectedException
import android.car.hardware.CarPropertyConfig
import android.car.hardware.property.CarInternalErrorException
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.PropertyAccessDeniedSecurityException
import android.car.hardware.property.PropertyNotAvailableAndRetryException
import android.car.hardware.property.PropertyNotAvailableException
import android.car.hardware.property.Subscription
import android.content.Context
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Singleton wrapper làm việc trực tiếp với `CarPropertyManager` trong môi trường xe thật.
 *
 * Manager này được thiết kế để inject ở nhiều module (`data`, `feature`, ...), vì vậy chỉ
 * một instance được giữ trong process bằng Hilt. Cách này tránh việc mỗi màn hình tự tạo
 * `Car`/`CarPropertyManager`, giảm số lần bind tới CarService và giúp cache, subscription,
 * retry connection được dùng chung.
 *
 * Luồng xử lý coroutine:
 * - Constructor chỉ tạo scope nền và gọi [connectAsync], không chặn main thread.
 * - Đọc/ghi property là các hàm `suspend`; mọi lời gọi framework chạy trên dispatcher IO.
 * - Callback từ vehicle HAL dùng `CarPropertyManager.subscribePropertyEvents(..., Executor, ...)`
 *   với executor lấy từ coroutine dispatcher, nên event không bị ép về main thread.
 * - Observe nhiều property nên dùng [observeProperties]. Manager gom các listener theo
 *   `(propertyId, areaId)` và chỉ giữ một platform subscription cho mỗi property với rate
 *   cao nhất đang được yêu cầu.
 * - Khi CarService lỗi, mất kết nối hoặc chưa sẵn sàng, manager chuyển state, trả
 *   [RealCarPropertyResult.Failure] kèm stale cache nếu có và tự retry connection nền.
 */
@Singleton
class RealCarPropertyManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val appContext = context.applicationContext
        private val job = SupervisorJob()
        private val carDispatcher = Dispatchers.IO
        private val callbackDispatcher = Dispatchers.Default
        private val scope = CoroutineScope(job + carDispatcher)
        private val callbackExecutor = callbackDispatcher.asExecutor()
        private val connectionMutex = Mutex()
        private val subscriptionMutex = Mutex()

        private val callbacksByKey = ConcurrentHashMap<PropertyKey, CopyOnWriteArrayList<CallbackRegistration>>()
        private val callbackKeysByProperty = ConcurrentHashMap<Int, MutableSet<PropertyKey>>()
        private val cache = ConcurrentHashMap<PropertyKey, RealCarPropertyValue>()
        private val subscribedRates = ConcurrentHashMap<Int, Float>()
        private val subscribedAreas = ConcurrentHashMap<Int, Set<Int>>()
        private val propertyConfigCache = ConcurrentHashMap<Int, CarPropertyConfig<*>>()

        private val _connectionState = MutableStateFlow(RealCarConnectionState.DISCONNECTED)
        val connectionState: StateFlow<RealCarConnectionState> = _connectionState.asStateFlow()

        @Volatile
        private var car: Car? = null

        @Volatile
        private var carPropertyManager: CarPropertyManager? = null

        @Volatile
        private var closed = false

        @Volatile
        private var connectJob: Job? = null

        @Volatile
        private var reconnectJob: Job? = null

        @Volatile
        private var traceLoggingEnabled = true

        private val platformCallback =
            object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: android.car.hardware.CarPropertyValue<*>) {
                    val wrappedValue = RealCarPropertyValue.from(value)
                    val key = PropertyKey(wrappedValue.propertyId, wrappedValue.areaId)
                    logDebug("callback received ${wrappedValue.describeForLog()}")

                    if (wrappedValue.isAvailable) {
                        cache[key] = wrappedValue
                        dispatch(wrappedValue)
                    } else {
                        reportError(
                            RealCarPropertyException.PropertyUnavailable(
                                propertyId = wrappedValue.propertyId,
                                areaId = wrappedValue.areaId,
                                status = wrappedValue.status,
                            ),
                        )
                    }
                }

                override fun onErrorEvent(
                    propertyId: Int,
                    areaId: Int,
                ) {
                    reportError(
                        RealCarPropertyException.PropertyUnavailable(
                            propertyId = propertyId,
                            areaId = areaId,
                        ),
                    )
                }

                override fun onErrorEvent(
                    propertyId: Int,
                    areaId: Int,
                    errorCode: Int,
                ) {
                    reportError(platformErrorFromCode(propertyId, areaId, errorCode))
                }
            }

        init {
            connectAsync()
        }

        /**
         * Chủ động khởi động kết nối nền tới CarService.
         *
         * Hàm trả [Job] để module gọi có thể theo dõi nếu cần, nhưng thông thường caller
         * chỉ cần inject manager và dùng `try...`/`observe...`. Nếu service chưa sẵn sàng,
         * các thao tác đọc/ghi sẽ trả failure rõ nghĩa và tự kích hoạt retry.
         */
        fun connectAsync(): Job {
            if (closed) return Job().also { it.complete() }

            val runningJob = connectJob
            if (runningJob?.isActive == true) return runningJob

            return scope
                .launch {
                    connect()
                }.also { connectJob = it }
        }

        /**
         * Kết nối tới CarService trên dispatcher nền.
         *
         * Hàm này serialize bằng [Mutex] để nhiều module gọi đồng thời không tạo nhiều
         * instance `Car`. Khi kết nối thành công, manager tự resubscribe các property đã
         * có listener trước đó.
         */
        suspend fun connect(): RealCarPropertyResult<Unit> =
            connectionMutex.withLock {
                if (closed) {
                    return@withLock RealCarPropertyResult.Failure(serviceClosedError("connect"))
                }

                val currentCar = car
                if (currentCar?.isConnected == true && carPropertyManager != null) {
                    return@withLock RealCarPropertyResult.Success(Unit, RealCarPropertyValueSource.LOCAL)
                }

                _connectionState.value = RealCarConnectionState.CONNECTING
                logInfo("connecting to platform CarService")

                return@withLock withContext(carDispatcher) {
                    try {
                        val platformCar = currentCar ?: Car.createCar(appContext).also { car = it }
                        if (!platformCar.isConnected && !platformCar.isConnecting) {
                            platformCar.connect()
                        }
                        attachPropertyManager(platformCar)
                    } catch (error: RuntimeException) {
                        val mappedError =
                            mapThrowable(
                                operation = RealCarPropertyOperation.READ,
                                propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                                areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                                error = error,
                            )
                        markServiceUnavailable(mappedError, retry = true)
                        RealCarPropertyResult.Failure(mappedError)
                    }
                }
            }

        fun getConnectionState(): RealCarConnectionState = _connectionState.value

        fun isServiceReady(): Boolean = _connectionState.value == RealCarConnectionState.CONNECTED && carPropertyManager != null

        /**
         * Lấy cache cuối cùng của một property.
         *
         * Cache được cập nhật từ read/write thành công và event callback. Dữ liệu này phục
         * vụ UI hoặc fallback khi CarService chưa sẵn sàng, không nên dùng làm nguồn quyết
         * định điều khiển xe nếu chưa đọc lại property thật.
         */
        fun getCachedProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyValue? = cache[PropertyKey(propertyId, areaId)]

        fun setTraceLoggingEnabled(enabled: Boolean) {
            traceLoggingEnabled = enabled
            Timber.tag(TAG).i("trace logging enabled=$enabled")
        }

        suspend fun tryGetVehicleSpeed(areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID): RealCarPropertyResult<Float> =
            tryGetFloatProperty(RealVehiclePropertyIds.SPEED, areaId)

        suspend fun tryGetOdometerKilometers(areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID): RealCarPropertyResult<Float> =
            tryGetFloatProperty(RealVehiclePropertyIds.ODOMETER, areaId)

        suspend fun tryGetCurrentGear(areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID): RealCarPropertyResult<Int> =
            tryGetIntProperty(RealVehiclePropertyIds.GEAR, areaId)

        suspend fun getFloatProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): Float = tryGetFloatProperty(propertyId, areaId).getOrThrow()

        suspend fun tryGetFloatProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<Float> =
            readTypedProperty(Float::class.javaObjectType, propertyId, areaId) { value ->
                value.asFloat()
            }

        suspend fun setFloatProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: Float,
        ) {
            trySetFloatProperty(propertyId, areaId, value).getOrThrow()
        }

        suspend fun trySetFloatProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: Float,
        ): RealCarPropertyResult<Unit> = writePropertySafely(Float::class.javaObjectType, propertyId, areaId, value)

        suspend fun getIntProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): Int = tryGetIntProperty(propertyId, areaId).getOrThrow()

        suspend fun tryGetIntProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<Int> =
            readTypedProperty(Int::class.javaObjectType, propertyId, areaId) { value ->
                value.asInt()
            }

        suspend fun setIntProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: Int,
        ) {
            trySetIntProperty(propertyId, areaId, value).getOrThrow()
        }

        suspend fun trySetIntProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: Int,
        ): RealCarPropertyResult<Unit> = writePropertySafely(Int::class.javaObjectType, propertyId, areaId, value)

        suspend fun getBooleanProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): Boolean = tryGetBooleanProperty(propertyId, areaId).getOrThrow()

        suspend fun tryGetBooleanProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<Boolean> =
            readTypedProperty(Boolean::class.javaObjectType, propertyId, areaId) { value ->
                value.asBoolean()
            }

        suspend fun setBooleanProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: Boolean,
        ) {
            trySetBooleanProperty(propertyId, areaId, value).getOrThrow()
        }

        suspend fun trySetBooleanProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: Boolean,
        ): RealCarPropertyResult<Unit> = writePropertySafely(Boolean::class.javaObjectType, propertyId, areaId, value)

        suspend fun getStringProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): String = tryGetStringProperty(propertyId, areaId).getOrThrow()

        suspend fun tryGetStringProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<String> =
            readTypedProperty(String::class.java, propertyId, areaId) { value ->
                value.asString()
            }

        suspend fun setStringProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: String,
        ) {
            trySetStringProperty(propertyId, areaId, value).getOrThrow()
        }

        suspend fun trySetStringProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: String,
        ): RealCarPropertyResult<Unit> = writePropertySafely(String::class.java, propertyId, areaId, value)

        suspend fun getIntArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): IntArray = tryGetIntArrayProperty(propertyId, areaId).getOrThrow()

        suspend fun tryGetIntArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<IntArray> =
            readTypedProperty(IntArray::class.java, propertyId, areaId) { value ->
                value.asIntArray()
            }

        suspend fun tryGetLongProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<Long> =
            readTypedProperty(Long::class.javaObjectType, propertyId, areaId) { value ->
                value.asLong()
            }

        suspend fun trySetLongProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: Long,
        ): RealCarPropertyResult<Unit> = writePropertySafely(Long::class.javaObjectType, propertyId, areaId, value)

        suspend fun tryGetFloatArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<FloatArray> =
            readTypedProperty(FloatArray::class.java, propertyId, areaId) { value ->
                value.asFloatArray()
            }

        suspend fun trySetFloatArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: FloatArray,
        ): RealCarPropertyResult<Unit> =
            writePropertySafely(
                FloatArray::class.java,
                propertyId,
                areaId,
                value.copyOf(),
            )

        suspend fun tryGetLongArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<LongArray> =
            readTypedProperty(LongArray::class.java, propertyId, areaId) { value ->
                value.asLongArray()
            }

        suspend fun trySetLongArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: LongArray,
        ): RealCarPropertyResult<Unit> =
            writePropertySafely(
                LongArray::class.java,
                propertyId,
                areaId,
                value.copyOf(),
            )

        suspend fun tryGetByteArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<ByteArray> =
            readTypedProperty(ByteArray::class.java, propertyId, areaId) { value ->
                value.asByteArray()
            }

        suspend fun trySetByteArrayProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: ByteArray,
        ): RealCarPropertyResult<Unit> =
            writePropertySafely(
                ByteArray::class.java,
                propertyId,
                areaId,
                value.copyOf(),
            )

        /**
         * API typed khuyến nghị cho code mới / Recommended type-safe API for new code.
         */
        suspend fun <T : Any> read(property: RealCarProperty<T>): RealCarPropertyResult<T> =
            tryGetProperty(
                valueClass = property.valueClass,
                propertyId = property.propertyId,
                areaId = property.areaId,
            )

        /**
         * Ghi có xác nhận VHAL theo mặc định.
         *
         * Writes with VHAL confirmation by default. Set [waitForPropertyUpdate] to false only
         * for explicitly fire-and-forget operations.
         */
        suspend fun <T : Any> write(
            property: RealCarProperty<T>,
            value: T,
            waitForPropertyUpdate: Boolean = true,
            timeoutMillis: Long = RealCarBatchOptions.DEFAULT_TIMEOUT_MILLIS,
        ): RealCarPropertyResult<Unit> {
            val batch =
                trySetProperties(
                    requests =
                        listOf(
                            property.writeRequest(
                                value = value,
                                waitForPropertyUpdate = waitForPropertyUpdate,
                            ),
                        ),
                    options = RealCarBatchOptions(timeoutMillis = timeoutMillis),
                )
            return batch.items.single().result
        }

        /**
         * Observe typed value; lỗi vẫn giữ stale snapshot trong [RealCarPropertyResult.Failure].
         *
         * Observes typed values while preserving stale snapshots on failures.
         */
        fun <T : Any> observe(
            property: RealCarProperty<T>,
            updateRateHz: Float = SENSOR_RATE_ONCHANGE,
        ): Flow<RealCarPropertyResult<T>> =
            observeProperty(
                propertyId = property.propertyId,
                areaId = property.areaId,
                updateRateHz = updateRateHz,
            ).map { result ->
                result.map { value -> value.castValue(boxedClass(property.valueClass)) }
            }

        /**
         * Đọc property theo kiểu bất kỳ được framework hỗ trợ.
         *
         * Hàm chạy trên dispatcher nền và trả lỗi chuẩn hóa nếu thiếu quyền, sai kiểu,
         * property không tồn tại, area không hợp lệ hoặc CarService chưa sẵn sàng.
         */
        suspend fun <T : Any> getProperty(
            valueClass: Class<T>,
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): T = tryGetProperty(valueClass, propertyId, areaId).getOrThrow()

        suspend fun <T : Any> tryGetProperty(
            valueClass: Class<T>,
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarPropertyResult<T> {
            @Suppress("UNCHECKED_CAST")
            return when (valueClass) {
                Float::class.java,
                Float::class.javaObjectType,
                Float::class.javaPrimitiveType,
                -> tryGetFloatProperty(propertyId, areaId) as RealCarPropertyResult<T>

                Int::class.java,
                Int::class.javaObjectType,
                Int::class.javaPrimitiveType,
                -> tryGetIntProperty(propertyId, areaId) as RealCarPropertyResult<T>

                Boolean::class.java,
                Boolean::class.javaObjectType,
                Boolean::class.javaPrimitiveType,
                -> tryGetBooleanProperty(propertyId, areaId) as RealCarPropertyResult<T>

                Long::class.java,
                Long::class.javaObjectType,
                Long::class.javaPrimitiveType,
                -> tryGetLongProperty(propertyId, areaId) as RealCarPropertyResult<T>

                String::class.java -> tryGetStringProperty(propertyId, areaId) as RealCarPropertyResult<T>
                IntArray::class.java -> tryGetIntArrayProperty(propertyId, areaId) as RealCarPropertyResult<T>
                FloatArray::class.java -> tryGetFloatArrayProperty(propertyId, areaId) as RealCarPropertyResult<T>
                LongArray::class.java -> tryGetLongArrayProperty(propertyId, areaId) as RealCarPropertyResult<T>
                ByteArray::class.java -> tryGetByteArrayProperty(propertyId, areaId) as RealCarPropertyResult<T>
                else ->
                    readTypedProperty(boxedClass(valueClass), propertyId, areaId) { value ->
                        value.castValue(boxedClass(valueClass))
                    }
            }
        }

        /**
         * Ghi property thật qua `CarPropertyManager`.
         *
         * Wrapper không queue write khi service lỗi. Với xe thật, lệnh cũ có thể không còn
         * đúng ngữ cảnh sau reconnect; caller nên quyết định retry dựa trên state hiện tại.
         */
        suspend fun <T : Any> setProperty(
            valueClass: Class<T>,
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: T,
        ) {
            trySetProperty(valueClass, propertyId, areaId, value).getOrThrow()
        }

        suspend fun <T : Any> trySetProperty(
            valueClass: Class<T>,
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            value: T,
        ): RealCarPropertyResult<Unit> = writePropertySafely(boxedClass(valueClass), propertyId, areaId, value)

        /**
         * Đọc metadata để dựng UI động và kiểm tra capability.
         *
         * Reads normalized metadata for dynamic UI and capability checks.
         */
        suspend fun getPropertyInfo(propertyId: Int): RealCarPropertyResult<RealCarPropertyInfo> =
            withContext(carDispatcher) {
                val manager =
                    managerOrConnect()
                        ?: return@withContext RealCarPropertyResult.Failure(
                            serviceNotReadyError(
                                operation = "metadata",
                                propertyId = propertyId,
                                areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                            ),
                        )
                try {
                    val config =
                        propertyConfig(manager, propertyId)
                            ?: return@withContext RealCarPropertyResult.Failure(
                                RealCarPropertyException.UnsupportedProperty(propertyId),
                            )
                    RealCarPropertyResult.Success(
                        value = config.toPropertyInfo(),
                        source = RealCarPropertyValueSource.REMOTE,
                    )
                } catch (error: RuntimeException) {
                    RealCarPropertyResult.Failure(
                        mapThrowable(
                            operation = RealCarPropertyOperation.READ,
                            propertyId = propertyId,
                            areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                            error = error,
                        ),
                    )
                }
            }

        /**
         * Liệt kê metadata mà CarService cho phép process hiện tại truy cập.
         *
         * Lists every property configuration visible to the current process. The returned
         * objects are immutable and sorted by property id, making this API suitable for
         * capability discovery, diagnostics, and dynamically generated settings screens.
         */
        suspend fun getPropertyInfos(): RealCarPropertyResult<List<RealCarPropertyInfo>> =
            withContext(carDispatcher) {
                val manager =
                    managerOrConnect()
                        ?: return@withContext RealCarPropertyResult.Failure(
                            serviceNotReadyError(
                                operation = "metadata list",
                                propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                                areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                            ),
                        )
                try {
                    val configs = manager.propertyList
                    configs.forEach { config ->
                        propertyConfigCache[config.propertyId] = config
                    }
                    RealCarPropertyResult.Success(
                        value =
                            configs
                                .map { config -> config.toPropertyInfo() }
                                .sortedBy(RealCarPropertyInfo::propertyId),
                        source = RealCarPropertyValueSource.REMOTE,
                    )
                } catch (error: RuntimeException) {
                    RealCarPropertyResult.Failure(
                        mapThrowable(
                            operation = RealCarPropertyOperation.READ,
                            propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                            areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                            error = error,
                        ),
                    )
                }
            }

        /**
         * Batch read dùng API async của framework, hỗ trợ partial success và giữ thứ tự.
         *
         * Uses the framework asynchronous API, supports partial success, preserves input
         * order, chunks large request sets, and bounds concurrent Binder work.
         */
        suspend fun tryGetProperties(
            requests: Collection<RealCarPropertyReadRequest>,
            options: RealCarBatchOptions = RealCarBatchOptions(),
        ): RealCarBatchResult<RealCarPropertyValue> {
            val startedAt = SystemClock.elapsedRealtime()
            val indexedRequests = requests.mapIndexed(::IndexedReadRequest)
            if (indexedRequests.isEmpty()) {
                return RealCarBatchResult(emptyList(), 0)
            }
            val uniqueRequests =
                indexedRequests
                    .associateBy { indexed ->
                        ReadRequestIdentity(
                            propertyId = indexed.request.propertyId,
                            areaId = indexed.request.areaId,
                            expectedType = indexed.request.expectedType,
                        )
                    }.values
                    .toList()

            val manager = managerOrConnect()
            if (manager == null) {
                val error =
                    serviceNotReadyError(
                        operation = "batch read",
                        propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                        areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                    )
                return RealCarBatchResult(
                    items = indexedRequests.map { it.failure(error) },
                    elapsedRealtimeMillis = SystemClock.elapsedRealtime() - startedAt,
                )
            }

            val semaphore = Semaphore(options.maxConcurrentChunks)
            val items =
                coroutineScope {
                    uniqueRequests
                        .chunked(options.maxRequestsPerChunk)
                        .map { chunk ->
                            async(carDispatcher) {
                                semaphore.withPermit {
                                    getPropertyChunk(manager, chunk, options)
                                }
                            }
                        }.awaitAll()
                        .flatten()
                }
            val uniqueResults =
                items.associateBy { item ->
                    val request = indexedRequests.first { it.index == item.requestIndex }.request
                    ReadRequestIdentity(
                        propertyId = request.propertyId,
                        areaId = request.areaId,
                        expectedType = request.expectedType,
                    )
                }
            val expandedItems =
                indexedRequests.map { indexed ->
                    val identity =
                        ReadRequestIdentity(
                            propertyId = indexed.request.propertyId,
                            areaId = indexed.request.areaId,
                            expectedType = indexed.request.expectedType,
                        )
                    val result = checkNotNull(uniqueResults[identity]).result
                    RealCarBatchItem(
                        requestIndex = indexed.index,
                        propertyId = indexed.request.propertyId,
                        areaId = indexed.request.areaId,
                        result = result,
                    )
                }
            return RealCarBatchResult(
                items = expandedItems,
                elapsedRealtimeMillis = SystemClock.elapsedRealtime() - startedAt,
            )
        }

        /**
         * Batch write dùng xác nhận VHAL theo từng request và không làm mất partial result.
         *
         * Batch writes preserve per-request confirmation/failure and never turn a partial
         * failure into an all-or-nothing exception. Requests for different property/area
         * pairs can run concurrently; requests targeting the same pair are serialized in
         * input order so a later value cannot be overwritten by an earlier in-flight call.
         */
        suspend fun trySetProperties(
            requests: Collection<RealCarPropertyWriteRequest>,
            options: RealCarBatchOptions = RealCarBatchOptions(),
        ): RealCarBatchResult<Unit> {
            val startedAt = SystemClock.elapsedRealtime()
            val indexedRequests = requests.mapIndexed(::IndexedWriteRequest)
            if (indexedRequests.isEmpty()) {
                return RealCarBatchResult(emptyList(), 0)
            }

            val manager = managerOrConnect()
            if (manager == null) {
                val error =
                    serviceNotReadyError(
                        operation = "batch write",
                        propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                        areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                    )
                return RealCarBatchResult(
                    items = indexedRequests.map { it.failure(error) },
                    elapsedRealtimeMillis = SystemClock.elapsedRealtime() - startedAt,
                )
            }

            val items = mutableListOf<RealCarBatchItem<Unit>>()
            for (wave in buildWriteWaves(indexedRequests)) {
                val semaphore = Semaphore(options.maxConcurrentChunks)
                items +=
                    coroutineScope {
                        wave
                            .chunked(options.maxRequestsPerChunk)
                            .map { chunk ->
                                async(carDispatcher) {
                                    semaphore.withPermit {
                                        setPropertyChunk(manager, chunk, options.timeoutMillis)
                                    }
                                }
                            }.awaitAll()
                            .flatten()
                    }
            }
            return RealCarBatchResult(
                items = items.sortedBy(RealCarBatchItem<Unit>::requestIndex),
                elapsedRealtimeMillis = SystemClock.elapsedRealtime() - startedAt,
            )
        }

        /**
         * Observe một property bằng Flow.
         *
         * Flow dùng buffer conflated để khi vehicle HAL bắn event nhanh hơn UI xử lý, collector
         * chỉ giữ giá trị mới nhất thay vì dồn hàng trăm event gây áp lực bộ nhớ/main thread.
         */
        fun observeProperty(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            updateRateHz: Float = SENSOR_RATE_ONCHANGE,
        ): Flow<RealCarPropertyResult<RealCarPropertyValue>> =
            observeProperties(
                listOf(
                    RealCarPropertySubscription(
                        propertyId = propertyId,
                        areaId = areaId,
                        updateRateHz = updateRateHz,
                    ),
                ),
            )

        /**
         * Observe nhiều property cùng lúc, phù hợp các màn hình dashboard lớn.
         *
         * Manager đăng ký một callback cục bộ cho toàn bộ danh sách request, sau đó reconcile
         * platform subscription một lần để tránh lặp lại công việc khi caller theo dõi vài
         * chục hoặc vài trăm property.
         */
        fun observeProperties(subscriptions: Collection<RealCarPropertySubscription>): Flow<RealCarPropertyResult<RealCarPropertyValue>> =
            callbackFlow {
                val callback =
                    object : CarPropertyEventCallback {
                        override fun onChangeEvent(value: RealCarPropertyValue) {
                            trySend(RealCarPropertyResult.Success(value, RealCarPropertyValueSource.CALLBACK))
                        }

                        override fun onErrorEvent(
                            propertyId: Int,
                            areaId: Int,
                            error: RealCarPropertyException,
                        ) {
                            trySend(RealCarPropertyResult.Failure(error, cache[PropertyKey(propertyId, areaId)]))
                        }
                    }
                val handles = registerCallbacks(callback, subscriptions)
                awaitClose {
                    handles.forEach(RealCarPropertyCallbackHandle::close)
                }
            }.buffer(capacity = kotlinx.coroutines.channels.Channel.CONFLATED)
                .flowOn(callbackDispatcher)

        fun registerCallback(
            callback: CarPropertyEventCallback,
            propertyId: Int,
            updateRateHz: Float = SENSOR_RATE_ONCHANGE,
        ): Boolean =
            registerCallbackSafely(
                callback = callback,
                propertyId = propertyId,
                areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                updateRateHz = updateRateHz,
            ) is RealCarPropertyResult.Success

        fun registerCallbackForArea(
            callback: CarPropertyEventCallback,
            propertyId: Int,
            areaId: Int,
            updateRateHz: Float = SENSOR_RATE_ONCHANGE,
        ): Boolean =
            registerCallbackSafely(
                callback = callback,
                propertyId = propertyId,
                areaId = areaId,
                updateRateHz = updateRateHz,
            ) is RealCarPropertyResult.Success

        /**
         * Đăng ký callback theo lifecycle mà không chạy xử lý nặng trên main thread.
         *
         * `onDestroy` chỉ đóng handle; việc unsubscribe thật được đưa về scope nền của manager.
         */
        fun registerCallback(
            owner: LifecycleOwner,
            callback: CarPropertyEventCallback,
            propertyId: Int,
            updateRateHz: Float = SENSOR_RATE_ONCHANGE,
        ): RealCarPropertyCallbackHandle {
            val rawHandle =
                registerCallbackSafely(
                    callback = callback,
                    propertyId = propertyId,
                    areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                    updateRateHz = updateRateHz,
                ).getOrThrow()

            lateinit var lifecycleHandle: RealCarPropertyCallbackHandle
            val observer =
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        lifecycleHandle.close()
                    }
                }

            lifecycleHandle =
                RealCarPropertyCallbackHandle {
                    owner.lifecycle.removeObserver(observer)
                    rawHandle.close()
                }
            owner.lifecycle.addObserver(observer)
            return lifecycleHandle
        }

        fun registerCallbackSafely(
            callback: CarPropertyEventCallback,
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
            updateRateHz: Float = SENSOR_RATE_ONCHANGE,
        ): RealCarPropertyResult<RealCarPropertyCallbackHandle> {
            val rateError = validateUpdateRate(propertyId, areaId, updateRateHz)
            if (rateError != null) {
                notifySingleCallbackError(callback, rateError)
                return RealCarPropertyResult.Failure(rateError, cache[PropertyKey(propertyId, areaId)])
            }

            val registration = CallbackRegistration(callback, propertyId, areaId, updateRateHz)
            addCallbackRegistration(registration)
            cache[PropertyKey(propertyId, areaId)]?.let { cachedValue ->
                scope.launch(callbackDispatcher) {
                    dispatchToRegistration(registration, cachedValue)
                }
            }
            reconcileSubscriptionsAsync()

            val handle =
                RealCarPropertyCallbackHandle {
                    scope.launch {
                        removeCallbackRegistration(registration)
                        reconcileSubscriptions()
                    }
                }
            return RealCarPropertyResult.Success(handle, RealCarPropertyValueSource.LOCAL)
        }

        fun unregisterCallback(callback: CarPropertyEventCallback) {
            scope.launch {
                callbacksByKey.values.forEach { registrations ->
                    registrations.removeAll { it.callback == callback }
                }
                cleanupEmptyCallbackKeys()
                reconcileSubscriptions()
            }
        }

        fun unregisterCallback(
            callback: CarPropertyEventCallback,
            propertyId: Int,
        ) {
            scope.launch {
                callbackKeysByProperty[propertyId]
                    ?.forEach { key -> callbacksByKey[key]?.removeAll { it.callback == callback } }
                cleanupEmptyCallbackKeys()
                reconcileSubscriptions()
            }
        }

        fun unregisterCallbackForArea(
            callback: CarPropertyEventCallback,
            propertyId: Int,
            areaId: Int,
        ) {
            scope.launch {
                callbacksByKey[PropertyKey(propertyId, areaId)]?.removeAll { it.callback == callback }
                cleanupEmptyCallbackKeys()
                reconcileSubscriptions()
            }
        }

        /**
         * Đóng manager khi cần release thủ công.
         *
         * Với instance inject singleton, app thường không cần gọi hàm này. Sau khi đóng,
         * manager hủy scope coroutine và không reconnect nữa.
         */
        fun close() {
            logInfo("closing real car property manager")
            closed = true
            _connectionState.value = RealCarConnectionState.CLOSED
            callbacksByKey.clear()
            callbackKeysByProperty.clear()
            runCatching {
                subscribedRates.keys.forEach { propertyId ->
                    carPropertyManager?.unsubscribePropertyEvents(propertyId, platformCallback)
                }
            }.onFailure { error ->
                logWarn("failed to unsubscribe platform callbacks", error)
            }
            subscribedRates.clear()
            subscribedAreas.clear()
            propertyConfigCache.clear()
            carPropertyManager = null
            car?.disconnect()
            car = null
            job.cancel()
        }

        private fun registerCallbacks(
            callback: CarPropertyEventCallback,
            subscriptions: Collection<RealCarPropertySubscription>,
        ): List<RealCarPropertyCallbackHandle> {
            val handles = mutableListOf<RealCarPropertyCallbackHandle>()
            subscriptions.forEach { subscription ->
                val rateError =
                    validateUpdateRate(
                        propertyId = subscription.propertyId,
                        areaId = subscription.areaId,
                        updateRateHz = subscription.updateRateHz,
                    )
                if (rateError != null) {
                    notifySingleCallbackError(callback, rateError)
                    return@forEach
                }

                val registration =
                    CallbackRegistration(
                        callback = callback,
                        propertyId = subscription.propertyId,
                        areaId = subscription.areaId,
                        updateRateHz = subscription.updateRateHz,
                    )
                addCallbackRegistration(registration)
                cache[PropertyKey(subscription.propertyId, subscription.areaId)]?.let(callback::onChangeEvent)
                handles +=
                    RealCarPropertyCallbackHandle {
                        scope.launch {
                            removeCallbackRegistration(registration)
                            reconcileSubscriptions()
                        }
                    }
            }
            reconcileSubscriptionsAsync()
            return handles
        }

        private suspend fun <T : Any> readTypedProperty(
            valueClass: Class<T>,
            propertyId: Int,
            areaId: Int,
            mapper: (RealCarPropertyValue) -> T,
        ): RealCarPropertyResult<T> =
            withContext(carDispatcher) {
                val key = PropertyKey(propertyId, areaId)
                val manager =
                    managerOrConnect()
                        ?: return@withContext RealCarPropertyResult.Failure(
                            serviceNotReadyError("read", propertyId, areaId),
                            cache[key],
                        )

                val accessError =
                    validatePropertyRequest(
                        manager = manager,
                        operation = RealCarPropertyOperation.READ,
                        propertyId = propertyId,
                        areaId = areaId,
                        expectedType = valueClass,
                    )
                if (accessError != null) {
                    return@withContext RealCarPropertyResult.Failure(accessError, cache[key])
                }

                try {
                    val platformValueClass =
                        checkNotNull(propertyConfig(manager, propertyId))
                            .propertyType

                    @Suppress("UNCHECKED_CAST")
                    val platformValue =
                        manager.getProperty(
                            platformValueClass as Class<Any>,
                            propertyId,
                            areaId,
                        )
                    val wrappedValue = RealCarPropertyValue.from(platformValue)
                    val mappedValue = mapper(wrappedValue)
                    cache[key] = wrappedValue
                    logDebug("read success ${wrappedValue.describeForLog()}")
                    RealCarPropertyResult.Success(mappedValue, RealCarPropertyValueSource.REMOTE)
                } catch (error: RuntimeException) {
                    val mappedError = mapThrowable(RealCarPropertyOperation.READ, propertyId, areaId, error)
                    handlePotentialServiceFailure(mappedError)
                    logWarn("read failed property=${propertyLabel(propertyId)} area=$areaId", mappedError)
                    RealCarPropertyResult.Failure(mappedError, cache[key])
                }
            }

        private suspend fun <T : Any> writePropertySafely(
            valueClass: Class<T>,
            propertyId: Int,
            areaId: Int,
            value: T,
        ): RealCarPropertyResult<Unit> =
            withContext(carDispatcher) {
                val key = PropertyKey(propertyId, areaId)
                val manager =
                    managerOrConnect()
                        ?: return@withContext RealCarPropertyResult.Failure(
                            serviceNotReadyError("write", propertyId, areaId),
                            cache[key],
                        )

                val accessError =
                    validatePropertyRequest(
                        manager = manager,
                        operation = RealCarPropertyOperation.WRITE,
                        propertyId = propertyId,
                        areaId = areaId,
                        expectedType = valueClass,
                    )
                if (accessError != null) {
                    return@withContext RealCarPropertyResult.Failure(accessError, cache[key])
                }

                try {
                    val platformValueClass =
                        checkNotNull(propertyConfig(manager, propertyId))
                            .propertyType
                    val platformValue = value.toPlatformValue(platformValueClass)
                    @Suppress("UNCHECKED_CAST")
                    manager.setProperty(
                        platformValueClass as Class<Any>,
                        propertyId,
                        areaId,
                        platformValue,
                    )
                    val localValue = RealCarPropertyValue.local(propertyId, areaId, value)
                    cache[key] = localValue
                    dispatch(localValue)
                    logDebug("write success ${localValue.describeForLog()}")
                    RealCarPropertyResult.Success(Unit, RealCarPropertyValueSource.REMOTE)
                } catch (error: RuntimeException) {
                    val mappedError = mapThrowable(RealCarPropertyOperation.WRITE, propertyId, areaId, error)
                    handlePotentialServiceFailure(mappedError)
                    logWarn("write failed property=${propertyLabel(propertyId)} area=$areaId", mappedError)
                    RealCarPropertyResult.Failure(mappedError, cache[key])
                }
            }

        private suspend fun getPropertyChunk(
            manager: CarPropertyManager,
            requests: List<IndexedReadRequest>,
            options: RealCarBatchOptions,
        ): List<RealCarBatchItem<RealCarPropertyValue>> {
            val immediateResults = mutableListOf<RealCarBatchItem<RealCarPropertyValue>>()
            val validRequests =
                requests.mapNotNull { indexed ->
                    val request = indexed.request
                    val validationError =
                        validatePropertyRequest(
                            manager = manager,
                            operation = RealCarPropertyOperation.READ,
                            propertyId = request.propertyId,
                            areaId = request.areaId,
                            expectedType = request.expectedType,
                        )
                    if (validationError == null) {
                        indexed
                    } else {
                        immediateResults += indexed.failure(validationError)
                        null
                    }
                }
            if (validRequests.isEmpty()) return immediateResults.sortedBy { it.requestIndex }

            val asyncResults =
                awaitGetPropertyChunk(
                    manager = manager,
                    requests = validRequests,
                    timeoutMillis = options.timeoutMillis,
                )
            val recoveredResults =
                if (options.fallbackToSynchronousRead) {
                    asyncResults.map { item ->
                        val error = (item.result as? RealCarPropertyResult.Failure)?.error
                        if (error is RealCarPropertyException.AsyncOperationFailed) {
                            val indexed = validRequests.first { it.index == item.requestIndex }
                            getPropertySynchronously(manager, indexed)
                        } else {
                            item
                        }
                    }
                } else {
                    asyncResults
                }
            return (immediateResults + recoveredResults).sortedBy { it.requestIndex }
        }

        private fun getPropertySynchronously(
            manager: CarPropertyManager,
            indexed: IndexedReadRequest,
        ): RealCarBatchItem<RealCarPropertyValue> {
            val request = indexed.request
            return try {
                val valueClass =
                    checkNotNull(propertyConfig(manager, request.propertyId))
                        .propertyType

                @Suppress("UNCHECKED_CAST")
                val platformValue =
                    manager.getProperty(
                        valueClass as Class<Any>,
                        request.propertyId,
                        request.areaId,
                    )
                val value = RealCarPropertyValue.from(platformValue)
                cache[PropertyKey(request.propertyId, request.areaId)] = value
                indexed.success(value)
            } catch (error: RuntimeException) {
                indexed.failure(
                    mapThrowable(
                        operation = RealCarPropertyOperation.READ,
                        propertyId = request.propertyId,
                        areaId = request.areaId,
                        error = error,
                    ),
                )
            }
        }

        private suspend fun awaitGetPropertyChunk(
            manager: CarPropertyManager,
            requests: List<IndexedReadRequest>,
            timeoutMillis: Long,
        ): List<RealCarBatchItem<RealCarPropertyValue>> =
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                val generatedRequests =
                    requests.map { indexed ->
                        manager.generateGetPropertyRequest(
                            indexed.request.propertyId,
                            indexed.request.areaId,
                        ) to indexed
                    }
                val platformRequests = generatedRequests.map { it.first }
                val indexedByRequestId =
                    generatedRequests.associate { (generated, indexed) ->
                        generated.requestId to indexed
                    }
                val completed = ConcurrentHashMap<Int, RealCarBatchItem<RealCarPropertyValue>>()
                val resumed = AtomicBoolean(false)

                fun complete(
                    requestId: Int,
                    item: RealCarBatchItem<RealCarPropertyValue>,
                ) {
                    completed.putIfAbsent(requestId, item)
                    if (
                        completed.size == generatedRequests.size &&
                        resumed.compareAndSet(false, true) &&
                        continuation.isActive
                    ) {
                        continuation.resume(completed.values.sortedBy { it.requestIndex })
                    }
                }

                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                }

                try {
                    manager.getPropertiesAsync(
                        platformRequests,
                        timeoutMillis,
                        cancellationSignal,
                        callbackExecutor,
                        object : CarPropertyManager.GetPropertyCallback {
                            override fun onSuccess(result: CarPropertyManager.GetPropertyResult<*>) {
                                val indexed = indexedByRequestId[result.requestId] ?: return
                                val value =
                                    RealCarPropertyValue.remote(
                                        propertyId = result.propertyId,
                                        areaId = result.areaId,
                                        value = result.value,
                                        timestampNanos = result.timestampNanos,
                                    )
                                cache[PropertyKey(result.propertyId, result.areaId)] = value
                                complete(
                                    result.requestId,
                                    indexed.success(value),
                                )
                            }

                            override fun onFailure(error: CarPropertyManager.PropertyAsyncError) {
                                val indexed = indexedByRequestId[error.requestId] ?: return
                                complete(
                                    error.requestId,
                                    indexed.failure(
                                        asyncError(
                                            operation = RealCarPropertyOperation.READ,
                                            propertyId = error.propertyId,
                                            areaId = error.areaId,
                                            errorCode = error.errorCode,
                                            detailedErrorCode = error.detailedErrorCode,
                                            timeoutMillis = timeoutMillis,
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                } catch (error: RuntimeException) {
                    if (resumed.compareAndSet(false, true) && continuation.isActive) {
                        val mapped =
                            mapThrowable(
                                operation = RealCarPropertyOperation.READ,
                                propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                                areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                                error = error,
                            )
                        continuation.resume(requests.map { it.failure(mapped) })
                    }
                }
            }

        private suspend fun setPropertyChunk(
            manager: CarPropertyManager,
            requests: List<IndexedWriteRequest>,
            timeoutMillis: Long,
        ): List<RealCarBatchItem<Unit>> {
            val immediateResults = mutableListOf<RealCarBatchItem<Unit>>()
            val validRequests =
                requests.mapNotNull { indexed ->
                    val request = indexed.request
                    val rateError =
                        validateUpdateRate(
                            propertyId = request.propertyId,
                            areaId = request.areaId,
                            updateRateHz = request.updateRateHz,
                        )
                    val validationError =
                        rateError
                            ?: validatePropertyRequest(
                                manager = manager,
                                operation = RealCarPropertyOperation.WRITE,
                                propertyId = request.propertyId,
                                areaId = request.areaId,
                                expectedType = request.expectedType ?: request.value.javaClass,
                            )
                    if (validationError == null) {
                        indexed
                    } else {
                        immediateResults += indexed.failure(validationError)
                        null
                    }
                }
            if (validRequests.isEmpty()) return immediateResults.sortedBy { it.requestIndex }

            val asyncResults =
                awaitSetPropertyChunk(
                    manager = manager,
                    requests = validRequests,
                    timeoutMillis = timeoutMillis,
                )
            return (immediateResults + asyncResults).sortedBy { it.requestIndex }
        }

        private suspend fun awaitSetPropertyChunk(
            manager: CarPropertyManager,
            requests: List<IndexedWriteRequest>,
            timeoutMillis: Long,
        ): List<RealCarBatchItem<Unit>> =
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                val requestsById = mutableMapOf<Int, IndexedWriteRequest>()
                val platformRequests =
                    requests.map { indexed ->
                        val request = indexed.request
                        val platformValueClass =
                            checkNotNull(propertyConfig(manager, request.propertyId))
                                .propertyType
                        manager
                            .generateSetPropertyRequest(
                                request.propertyId,
                                request.areaId,
                                request.value.toPlatformValue(platformValueClass),
                            ).apply {
                                isWaitForPropertyUpdate = request.waitForPropertyUpdate
                                updateRateHz = request.updateRateHz
                                requestsById[requestId] = indexed
                            }
                    }
                val completed = ConcurrentHashMap<Int, RealCarBatchItem<Unit>>()
                val resumed = AtomicBoolean(false)

                fun complete(
                    requestId: Int,
                    item: RealCarBatchItem<Unit>,
                ) {
                    completed.putIfAbsent(requestId, item)
                    if (
                        completed.size == platformRequests.size &&
                        resumed.compareAndSet(false, true) &&
                        continuation.isActive
                    ) {
                        continuation.resume(completed.values.sortedBy { it.requestIndex })
                    }
                }

                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                }

                try {
                    manager.setPropertiesAsync(
                        platformRequests,
                        timeoutMillis,
                        cancellationSignal,
                        callbackExecutor,
                        object : CarPropertyManager.SetPropertyCallback {
                            override fun onSuccess(result: CarPropertyManager.SetPropertyResult) {
                                val indexed = requestsById[result.requestId] ?: return
                                val request = indexed.request
                                val value =
                                    RealCarPropertyValue.remote(
                                        propertyId = result.propertyId,
                                        areaId = result.areaId,
                                        value = request.value,
                                        timestampNanos = result.updateTimestampNanos,
                                    )
                                cache[PropertyKey(result.propertyId, result.areaId)] = value
                                dispatch(value)
                                complete(
                                    result.requestId,
                                    indexed.success(Unit),
                                )
                            }

                            override fun onFailure(error: CarPropertyManager.PropertyAsyncError) {
                                val indexed = requestsById[error.requestId] ?: return
                                complete(
                                    error.requestId,
                                    indexed.failure(
                                        asyncError(
                                            operation = RealCarPropertyOperation.WRITE,
                                            propertyId = error.propertyId,
                                            areaId = error.areaId,
                                            errorCode = error.errorCode,
                                            detailedErrorCode = error.detailedErrorCode,
                                            timeoutMillis = timeoutMillis,
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                } catch (error: RuntimeException) {
                    if (resumed.compareAndSet(false, true) && continuation.isActive) {
                        val mapped =
                            mapThrowable(
                                operation = RealCarPropertyOperation.WRITE,
                                propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                                areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                                error = error,
                            )
                        continuation.resume(requests.map { it.failure(mapped) })
                    }
                }
            }

        private fun asyncError(
            operation: RealCarPropertyOperation,
            propertyId: Int,
            areaId: Int,
            errorCode: Int,
            detailedErrorCode: Int,
            timeoutMillis: Long,
        ): RealCarPropertyException =
            when (errorCode) {
                CarPropertyManager.STATUS_ERROR_TIMEOUT ->
                    RealCarPropertyException.Timeout(
                        operation = operation,
                        propertyId = propertyId,
                        areaId = areaId,
                        timeoutMillis = timeoutMillis,
                    )

                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE ->
                    RealCarPropertyException.PropertyUnavailable(
                        propertyId = propertyId,
                        areaId = areaId,
                        detailedErrorCode = detailedErrorCode,
                        retryable = true,
                    )

                else ->
                    RealCarPropertyException.AsyncOperationFailed(
                        operation = operation,
                        propertyId = propertyId,
                        areaId = areaId,
                        errorCode = errorCode,
                        detailedErrorCode = detailedErrorCode,
                        retryable = errorCode == CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                    )
            }

        private suspend fun managerOrConnect(): CarPropertyManager? {
            carPropertyManager?.let { return it }
            connect()
            return carPropertyManager
        }

        private fun validateUpdateRate(
            propertyId: Int,
            areaId: Int,
            updateRateHz: Float,
        ): RealCarPropertyException? =
            if (!updateRateHz.isFinite() || updateRateHz < 0f) {
                RealCarPropertyException.InvalidUpdateRate(propertyId, areaId, updateRateHz)
            } else {
                null
            }

        private fun validatePropertyAccess(
            manager: CarPropertyManager,
            operation: RealCarPropertyOperation,
            propertyId: Int,
            areaId: Int,
        ): RealCarPropertyException? =
            try {
                val config = propertyConfig(manager, propertyId)
                if (config == null) {
                    RealCarPropertyException.UnsupportedProperty(propertyId)
                } else {
                    validateConfigAccess(config, operation, areaId)
                }
            } catch (error: RuntimeException) {
                mapThrowable(operation, propertyId, areaId, error)
            }

        private fun validatePropertyRequest(
            manager: CarPropertyManager,
            operation: RealCarPropertyOperation,
            propertyId: Int,
            areaId: Int,
            expectedType: Class<*>?,
        ): RealCarPropertyException? {
            val config =
                try {
                    propertyConfig(manager, propertyId)
                } catch (error: RuntimeException) {
                    return mapThrowable(operation, propertyId, areaId, error)
                } ?: return RealCarPropertyException.UnsupportedProperty(propertyId)

            validateConfigAccess(config, operation, areaId)?.let { return it }

            val boxedExpectedType = expectedType?.let(::boxedClassUntyped)
            val boxedActualType = boxedClassUntyped(config.propertyType)
            if (
                boxedExpectedType != null &&
                boxedExpectedType != Any::class.java &&
                !boxedExpectedType.isAssignableFrom(boxedActualType) &&
                !boxedActualType.isAssignableFrom(boxedExpectedType)
            ) {
                return RealCarPropertyException.TypeMismatch(
                    propertyId = propertyId,
                    areaId = areaId,
                    expectedType = boxedExpectedType.simpleName,
                    actualType = boxedActualType.simpleName,
                )
            }
            return null
        }

        private fun propertyConfig(
            manager: CarPropertyManager,
            propertyId: Int,
        ): CarPropertyConfig<*>? =
            propertyConfigCache[propertyId]
                ?: manager.getCarPropertyConfig(propertyId)?.also { config ->
                    propertyConfigCache[propertyId] = config
                }

        private fun validateConfigAccess(
            config: CarPropertyConfig<*>,
            operation: RealCarPropertyOperation,
            areaId: Int,
        ): RealCarPropertyException? {
            val propertyId = config.propertyId
            val supportedAreas = supportedAreaIds(config)
            if (areaId !in supportedAreas) {
                return RealCarPropertyException.UnsupportedArea(propertyId, areaId, supportedAreas)
            }

            val access = config.getAreaIdConfig(areaId)?.access ?: config.access
            val canRead =
                access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ ||
                    access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE
            val canWrite =
                access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE ||
                    access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE

            return when (operation) {
                RealCarPropertyOperation.READ,
                RealCarPropertyOperation.OBSERVE,
                -> if (!canRead) RealCarPropertyException.PermissionDenied(operation, propertyId, areaId) else null

                RealCarPropertyOperation.WRITE ->
                    if (!canWrite) RealCarPropertyException.PermissionDenied(operation, propertyId, areaId) else null
            }
        }

        private fun CarPropertyConfig<*>.toPropertyInfo(): RealCarPropertyInfo {
            val normalizedAreaIds = supportedAreaIds(this).sorted()
            return RealCarPropertyInfo(
                propertyId = propertyId,
                name = RealVehiclePropertyIds.nameOf(propertyId),
                valueClass = propertyType,
                access = access.toRealCarPropertyAccess(),
                changeMode =
                    when (changeMode) {
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC -> RealCarPropertyChangeMode.STATIC
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE -> RealCarPropertyChangeMode.ON_CHANGE
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS -> RealCarPropertyChangeMode.CONTINUOUS
                        else -> RealCarPropertyChangeMode.UNKNOWN
                    },
                areaType = areaType,
                areas =
                    normalizedAreaIds.map { areaId ->
                        val areaConfig = getAreaIdConfig(areaId)
                        RealCarPropertyAreaInfo(
                            areaId = areaId,
                            access = (areaConfig?.access ?: access).toRealCarPropertyAccess(),
                            minimumValue = RealCarPropertyValue.defensiveCopy(areaConfig?.minValue),
                            maximumValue = RealCarPropertyValue.defensiveCopy(areaConfig?.maxValue),
                        )
                    },
                minimumSampleRateHz = minSampleRate,
                maximumSampleRateHz = maxSampleRate,
                configArray = configArray.toList(),
            )
        }

        private fun Int.toRealCarPropertyAccess(): RealCarPropertyAccess =
            when (this) {
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ -> RealCarPropertyAccess.READ
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE -> RealCarPropertyAccess.WRITE
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE -> RealCarPropertyAccess.READ_WRITE
                else -> RealCarPropertyAccess.NONE
            }

        private fun supportedAreaIds(config: CarPropertyConfig<*>): Set<Int> {
            val areaIds = config.areaIds
            return if (areaIds.isEmpty()) {
                setOf(RealCarPropertyValue.GLOBAL_AREA_ID)
            } else {
                areaIds.toSet()
            }
        }

        private fun attachPropertyManager(platformCar: Car): RealCarPropertyResult<Unit> {
            val manager =
                platformCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    ?: return RealCarPropertyResult.Failure(
                        RealCarPropertyException.ServiceUnavailable(
                            operation = "attach",
                            propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                            areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
                        ),
                    )

            carPropertyManager = manager
            propertyConfigCache.clear()
            _connectionState.value = RealCarConnectionState.CONNECTED
            logInfo("platform CarPropertyManager attached")
            reconcileSubscriptionsAsync()
            return RealCarPropertyResult.Success(Unit, RealCarPropertyValueSource.REMOTE)
        }

        private fun reconcileSubscriptionsAsync() {
            scope.launch {
                reconcileSubscriptions()
            }
        }

        private suspend fun reconcileSubscriptions() =
            subscriptionMutex.withLock {
                val manager =
                    carPropertyManager ?: run {
                        connectAsync()
                        return@withLock
                    }
                val desiredSubscriptions = desiredSubscriptions()
                val desiredPropertyIds = desiredSubscriptions.keys
                val currentlySubscribed = subscribedRates.keys.toSet()

                (currentlySubscribed - desiredPropertyIds).forEach { propertyId ->
                    runCatching {
                        manager.unsubscribePropertyEvents(propertyId, platformCallback)
                        subscribedRates.remove(propertyId)
                        subscribedAreas.remove(propertyId)
                        logInfo("unsubscribed property=${propertyLabel(propertyId)}")
                    }.onFailure { error ->
                        logWarn("failed to unsubscribe property=${propertyLabel(propertyId)}", error)
                    }
                }

                desiredSubscriptions.forEach { (propertyId, desired) ->
                    val currentRate = subscribedRates[propertyId]
                    val currentAreas = subscribedAreas[propertyId].orEmpty()
                    if (currentRate != null && currentRate >= desired.updateRateHz && currentAreas == desired.areaIds) {
                        return@forEach
                    }

                    desired.validationAreaIds.forEach { areaId ->
                        validatePropertyAccess(
                            manager = manager,
                            operation = RealCarPropertyOperation.OBSERVE,
                            propertyId = propertyId,
                            areaId = areaId,
                        )?.let { error ->
                            reportError(error)
                            return@forEach
                        }
                    }

                    subscribePlatform(manager, desired)?.let { error ->
                        reportError(error)
                    }
                }
            }

        private fun desiredSubscriptions(): Map<Int, DesiredSubscription> =
            callbacksByKey
                .filterValues { registrations -> registrations.isNotEmpty() }
                .entries
                .groupBy { entry -> entry.key.propertyId }
                .mapValues { (propertyId, entries) ->
                    val registrations = entries.flatMap { entry -> entry.value }
                    val rate =
                        registrations.maxOfOrNull { registration -> registration.updateRateHz }
                            ?: SENSOR_RATE_ONCHANGE
                    val areaIds = entries.map { entry -> entry.key.areaId }.toSet()
                    DesiredSubscription(
                        propertyId = propertyId,
                        areaIds = areaIds,
                        updateRateHz = rate,
                    )
                }

        private fun subscribePlatform(
            manager: CarPropertyManager,
            desired: DesiredSubscription,
        ): RealCarPropertyException? =
            try {
                manager.unsubscribePropertyEvents(desired.propertyId, platformCallback)
                val subscriptionBuilder =
                    Subscription
                        .Builder(desired.propertyId)
                        .setUpdateRateHz(desired.updateRateHz)
                        .setVariableUpdateRateEnabled(true)
                desired.areaIds.forEach(subscriptionBuilder::addAreaId)

                val registered =
                    manager.subscribePropertyEvents(
                        listOf(subscriptionBuilder.build()),
                        callbackExecutor,
                        platformCallback,
                    )
                if (registered) {
                    subscribedRates[desired.propertyId] = desired.updateRateHz
                    subscribedAreas[desired.propertyId] = desired.areaIds
                    logInfo("subscribed property=${propertyLabel(desired.propertyId)} rate=${desired.updateRateHz}")
                    null
                } else {
                    RealCarPropertyException.CallbackRegistrationFailed(
                        propertyId = desired.propertyId,
                        areaId = desired.areaIds.firstOrNull() ?: RealCarPropertyValue.GLOBAL_AREA_ID,
                    )
                }
            } catch (error: RuntimeException) {
                mapThrowable(
                    operation = RealCarPropertyOperation.OBSERVE,
                    propertyId = desired.propertyId,
                    areaId = desired.areaIds.firstOrNull() ?: RealCarPropertyValue.GLOBAL_AREA_ID,
                    error = error,
                )
            }

        private fun addCallbackRegistration(registration: CallbackRegistration) {
            val key = PropertyKey(registration.propertyId, registration.areaId)
            callbacksByKey.getOrPut(key) { CopyOnWriteArrayList() } += registration
            callbackKeysByProperty
                .getOrPut(registration.propertyId) { ConcurrentHashMap.newKeySet() }
                .add(key)
        }

        private fun removeCallbackRegistration(registration: CallbackRegistration) {
            val key = PropertyKey(registration.propertyId, registration.areaId)
            callbacksByKey[key]?.remove(registration)
            cleanupEmptyCallbackKeys()
        }

        private fun cleanupEmptyCallbackKeys() {
            callbacksByKey.entries.removeIf { entry -> entry.value.isEmpty() }
            callbackKeysByProperty.entries.removeIf { entry ->
                entry.value.removeIf { key -> !callbacksByKey.containsKey(key) }
                entry.value.isEmpty()
            }
        }

        private fun dispatch(value: RealCarPropertyValue) {
            callbacksByKey[PropertyKey(value.propertyId, value.areaId)]
                ?.forEach { registration -> dispatchToRegistration(registration, value) }
        }

        private fun dispatchToRegistration(
            registration: CallbackRegistration,
            value: RealCarPropertyValue,
        ) {
            try {
                registration.callback.onChangeEvent(value)
            } catch (error: RuntimeException) {
                notifySingleCallbackError(
                    callback = registration.callback,
                    error =
                        RealCarPropertyException.CallbackDispatchFailed(
                            propertyId = value.propertyId,
                            areaId = value.areaId,
                            cause = error,
                        ),
                )
            }
        }

        private fun reportError(error: RealCarPropertyException) {
            logWarn("reporting error property=${propertyLabel(error.propertyId)} area=${error.areaId}", error)
            val targetRegistrations =
                if (error.propertyId == RealCarPropertyException.UNKNOWN_PROPERTY_ID) {
                    callbacksByKey.values.flatten()
                } else {
                    callbackKeysByProperty[error.propertyId]
                        ?.flatMap { key -> callbacksByKey[key].orEmpty() }
                        .orEmpty()
                }
            targetRegistrations.forEach { registration ->
                notifySingleCallbackError(registration.callback, error)
            }
        }

        private fun notifySingleCallbackError(
            callback: CarPropertyEventCallback,
            error: RealCarPropertyException,
        ) {
            runCatching {
                callback.onErrorEvent(error.propertyId, error.areaId, error)
            }.onFailure { callbackError ->
                logWarn("callback error handler failed property=${propertyLabel(error.propertyId)}", callbackError)
            }
        }

        private fun serviceClosedError(operation: String): RealCarPropertyException =
            RealCarPropertyException.ServiceUnavailable(
                operation = operation,
                propertyId = RealCarPropertyException.UNKNOWN_PROPERTY_ID,
                areaId = RealCarPropertyValue.GLOBAL_AREA_ID,
            )

        private fun serviceNotReadyError(
            operation: String,
            propertyId: Int,
            areaId: Int,
        ): RealCarPropertyException =
            if (_connectionState.value == RealCarConnectionState.CONNECTING) {
                RealCarPropertyException.ServiceNotReady(operation, propertyId, areaId)
            } else {
                RealCarPropertyException.ServiceUnavailable(operation, propertyId, areaId)
            }

        private fun handlePotentialServiceFailure(error: RealCarPropertyException) {
            if (error is RealCarPropertyException.ServiceUnavailable) {
                markServiceUnavailable(error, retry = true)
            }
        }

        private fun markServiceUnavailable(
            error: RealCarPropertyException,
            retry: Boolean,
        ) {
            carPropertyManager = null
            subscribedRates.clear()
            subscribedAreas.clear()
            propertyConfigCache.clear()
            if (_connectionState.value != RealCarConnectionState.CLOSED) {
                _connectionState.value = RealCarConnectionState.DISCONNECTED
            }
            reportError(error)
            if (retry && !closed) {
                scheduleReconnect()
            }
        }

        private fun scheduleReconnect() {
            val runningReconnect = reconnectJob
            if (runningReconnect?.isActive == true) return

            reconnectJob =
                scope.launch {
                    delay(RECONNECT_DELAY_MS)
                    connect()
                }
        }

        private fun platformErrorFromCode(
            propertyId: Int,
            areaId: Int,
            errorCode: Int,
        ): RealCarPropertyException =
            when (errorCode) {
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED ->
                    RealCarPropertyException.PermissionDenied(RealCarPropertyOperation.WRITE, propertyId, areaId)

                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG ->
                    RealCarPropertyException.InvalidValue(propertyId, areaId, "framework errorCode=$errorCode")

                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE ->
                    RealCarPropertyException.PropertyUnavailable(
                        propertyId = propertyId,
                        areaId = areaId,
                        detailedErrorCode = errorCode,
                    )

                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN ->
                    RealCarPropertyException.PropertyUnavailable(
                        propertyId = propertyId,
                        areaId = areaId,
                        detailedErrorCode = errorCode,
                        retryable = true,
                    )

                else -> RealCarPropertyException.PlatformErrorCode(propertyId, areaId, errorCode)
            }

        private fun mapThrowable(
            operation: RealCarPropertyOperation,
            propertyId: Int,
            areaId: Int,
            error: RuntimeException,
        ): RealCarPropertyException =
            when (error) {
                is RealCarPropertyException -> error
                is PropertyAccessDeniedSecurityException ->
                    RealCarPropertyException.PermissionDenied(operation, propertyId, areaId, error)
                is SecurityException -> RealCarPropertyException.PermissionDenied(operation, propertyId, areaId, error)
                is CarNotConnectedException ->
                    RealCarPropertyException.ServiceUnavailable(operation.name.lowercase(), propertyId, areaId, error)
                is PropertyNotAvailableAndRetryException ->
                    RealCarPropertyException.PropertyUnavailable(
                        propertyId = propertyId,
                        areaId = areaId,
                        retryable = true,
                        cause = error,
                    )
                is PropertyNotAvailableException ->
                    RealCarPropertyException.PropertyUnavailable(
                        propertyId = propertyId,
                        areaId = areaId,
                        detailedErrorCode = error.detailedErrorCode,
                        cause = error,
                    )
                is CarInternalErrorException ->
                    RealCarPropertyException.InternalError(propertyId, areaId, error)
                is IllegalArgumentException ->
                    RealCarPropertyException.InvalidValue(
                        propertyId = propertyId,
                        areaId = areaId,
                        reason = error.message ?: "framework rejected argument",
                        cause = error,
                    )
                is IllegalStateException ->
                    RealCarPropertyException.PlatformFailure(operation, propertyId, areaId, error)
                else -> RealCarPropertyException.PlatformFailure(operation, propertyId, areaId, error)
            }

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> boxedClass(valueClass: Class<T>): Class<T> =
            when (valueClass) {
                Float::class.javaPrimitiveType -> Float::class.javaObjectType as Class<T>
                Int::class.javaPrimitiveType -> Int::class.javaObjectType as Class<T>
                Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType as Class<T>
                Long::class.javaPrimitiveType -> Long::class.javaObjectType as Class<T>
                else -> valueClass
            }

        private fun boxedClassUntyped(valueClass: Class<*>): Class<*> =
            when (valueClass) {
                Float::class.javaPrimitiveType -> Float::class.javaObjectType
                Int::class.javaPrimitiveType -> Int::class.javaObjectType
                Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
                Long::class.javaPrimitiveType -> Long::class.javaObjectType
                IntArray::class.java -> Array<Int>::class.java
                FloatArray::class.java -> Array<Float>::class.java
                LongArray::class.java -> Array<Long>::class.java
                ByteArray::class.java -> Array<Byte>::class.java
                else -> valueClass
            }

        private fun Any.toPlatformValue(platformValueClass: Class<*>): Any =
            when {
                this is IntArray && platformValueClass == Array<Int>::class.java -> toTypedArray()
                this is FloatArray && platformValueClass == Array<Float>::class.java -> toTypedArray()
                this is LongArray && platformValueClass == Array<Long>::class.java -> toTypedArray()
                this is ByteArray && platformValueClass == Array<Byte>::class.java -> toTypedArray()
                else -> RealCarPropertyValue.defensiveCopy(this) ?: this
            }

        private fun <T : Any> RealCarPropertyValue.castValue(valueClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            when (valueClass) {
                IntArray::class.java -> return asIntArray() as T
                FloatArray::class.java -> return asFloatArray() as T
                LongArray::class.java -> return asLongArray() as T
                ByteArray::class.java -> return asByteArray() as T
            }

            val rawValue = requireAvailable().value
            if (rawValue == null || !valueClass.isInstance(rawValue)) {
                throw RealCarPropertyException.TypeMismatch(
                    propertyId = propertyId,
                    areaId = areaId,
                    expectedType = valueClass.simpleName,
                    actualType = valueTypeName(),
                )
            }
            return valueClass.cast(rawValue)
                ?: throw RealCarPropertyException.TypeMismatch(
                    propertyId = propertyId,
                    areaId = areaId,
                    expectedType = valueClass.simpleName,
                    actualType = valueTypeName(),
                )
        }

        private fun RealCarPropertyValue.describeForLog(): String =
            "property=${propertyLabel(propertyId)} area=$areaId type=${valueTypeName()} value=$value status=$status ts=$timestampNanos"

        private fun propertyLabel(propertyId: Int): String = "${RealVehiclePropertyIds.nameOf(propertyId)}($propertyId)"

        private fun logDebug(message: String) {
            if (traceLoggingEnabled) {
                Timber.tag(TAG).d(message)
            }
        }

        private fun logInfo(message: String) {
            if (traceLoggingEnabled) {
                Timber.tag(TAG).i(message)
            }
        }

        private fun logWarn(
            message: String,
            throwable: Throwable? = null,
        ) {
            if (!traceLoggingEnabled) return

            if (throwable == null) {
                Timber.tag(TAG).w(message)
            } else {
                Timber.tag(TAG).w(throwable, message)
            }
        }

        interface CarPropertyEventCallback {
            fun onChangeEvent(value: RealCarPropertyValue)

            fun onErrorEvent(
                propertyId: Int,
                areaId: Int,
            ) = Unit

            fun onErrorEvent(
                propertyId: Int,
                areaId: Int,
                error: RealCarPropertyException,
            ) {
                onErrorEvent(propertyId, areaId)
            }
        }

        private data class PropertyKey(
            val propertyId: Int,
            val areaId: Int,
        )

        private data class IndexedReadRequest(
            val index: Int,
            val request: RealCarPropertyReadRequest,
        )

        private data class ReadRequestIdentity(
            val propertyId: Int,
            val areaId: Int,
            val expectedType: Class<*>?,
        )

        private data class IndexedWriteRequest(
            val index: Int,
            val request: RealCarPropertyWriteRequest,
        )

        private data class WriteRequestIdentity(
            val propertyId: Int,
            val areaId: Int,
        )

        /**
         * Chia batch ghi thành các lượt không trùng key.
         *
         * Splits writes into conflict-free waves. This preserves input order for repeated
         * property/area pairs while retaining parallelism across independent properties.
         */
        private fun buildWriteWaves(requests: List<IndexedWriteRequest>): List<List<IndexedWriteRequest>> {
            val remaining = requests.toMutableList()
            val waves = mutableListOf<List<IndexedWriteRequest>>()
            while (remaining.isNotEmpty()) {
                val identities = mutableSetOf<WriteRequestIdentity>()
                val wave = mutableListOf<IndexedWriteRequest>()
                val iterator = remaining.listIterator()
                while (iterator.hasNext()) {
                    val indexed = iterator.next()
                    val identity =
                        WriteRequestIdentity(
                            propertyId = indexed.request.propertyId,
                            areaId = indexed.request.areaId,
                        )
                    if (identities.add(identity)) {
                        wave += indexed
                        iterator.remove()
                    }
                }
                waves += wave
            }
            return waves
        }

        private fun IndexedReadRequest.success(value: RealCarPropertyValue) =
            RealCarBatchItem(
                requestIndex = index,
                propertyId = request.propertyId,
                areaId = request.areaId,
                result = RealCarPropertyResult.Success(value, RealCarPropertyValueSource.REMOTE),
            )

        private fun IndexedReadRequest.failure(error: RealCarPropertyException) =
            RealCarBatchItem<RealCarPropertyValue>(
                requestIndex = index,
                propertyId = request.propertyId,
                areaId = request.areaId,
                result =
                    RealCarPropertyResult.Failure(
                        error = error,
                        staleValue = cache[PropertyKey(request.propertyId, request.areaId)],
                    ),
            )

        private fun IndexedWriteRequest.success(value: Unit) =
            RealCarBatchItem(
                requestIndex = index,
                propertyId = request.propertyId,
                areaId = request.areaId,
                result = RealCarPropertyResult.Success(value, RealCarPropertyValueSource.REMOTE),
            )

        private fun IndexedWriteRequest.failure(error: RealCarPropertyException) =
            RealCarBatchItem<Unit>(
                requestIndex = index,
                propertyId = request.propertyId,
                areaId = request.areaId,
                result =
                    RealCarPropertyResult.Failure(
                        error = error,
                        staleValue = cache[PropertyKey(request.propertyId, request.areaId)],
                    ),
            )

        private data class CallbackRegistration(
            val callback: CarPropertyEventCallback,
            val propertyId: Int,
            val areaId: Int,
            val updateRateHz: Float,
        )

        private data class DesiredSubscription(
            val propertyId: Int,
            val areaIds: Set<Int>,
            val updateRateHz: Float,
        ) {
            val validationAreaIds: Set<Int>
                get() = areaIds.ifEmpty { setOf(RealCarPropertyValue.GLOBAL_AREA_ID) }
        }

        companion object {
            private const val TAG = "RealCarPropertyManager"
            const val SENSOR_RATE_ONCHANGE = CarPropertyManager.SENSOR_RATE_ONCHANGE
            const val SENSOR_RATE_NORMAL = CarPropertyManager.SENSOR_RATE_NORMAL
            const val SENSOR_RATE_UI = CarPropertyManager.SENSOR_RATE_UI
            const val SENSOR_RATE_FAST = CarPropertyManager.SENSOR_RATE_FAST
            const val SENSOR_RATE_FASTEST = CarPropertyManager.SENSOR_RATE_FASTEST
            private const val RECONNECT_DELAY_MS = 1_000L
        }
    }

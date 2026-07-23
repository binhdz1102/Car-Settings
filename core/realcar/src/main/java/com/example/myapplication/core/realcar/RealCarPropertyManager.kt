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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

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

        private val callbacksByKey = ConcurrentHashMap<PropertyKey, CopyOnWriteArrayList<CallbackRegistration>>()
        private val callbackKeysByProperty = ConcurrentHashMap<Int, MutableSet<PropertyKey>>()
        private val cache = ConcurrentHashMap<PropertyKey, RealCarPropertyValue>()
        private val subscribedRates = ConcurrentHashMap<Int, Float>()
        private val subscribedAreas = ConcurrentHashMap<Int, Set<Int>>()

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

                String::class.java -> tryGetStringProperty(propertyId, areaId) as RealCarPropertyResult<T>
                IntArray::class.java -> tryGetIntArrayProperty(propertyId, areaId) as RealCarPropertyResult<T>
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

                val accessError = validatePropertyAccess(manager, RealCarPropertyOperation.READ, propertyId, areaId)
                if (accessError != null) {
                    return@withContext RealCarPropertyResult.Failure(accessError, cache[key])
                }

                try {
                    val platformValue = manager.getProperty(valueClass, propertyId, areaId)
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

                val accessError = validatePropertyAccess(manager, RealCarPropertyOperation.WRITE, propertyId, areaId)
                if (accessError != null) {
                    return@withContext RealCarPropertyResult.Failure(accessError, cache[key])
                }

                try {
                    manager.setProperty(valueClass, propertyId, areaId, value)
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
            if (updateRateHz < 0f) {
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
                val config = manager.getCarPropertyConfig(propertyId)
                if (config == null) {
                    RealCarPropertyException.UnsupportedProperty(propertyId)
                } else {
                    validateConfigAccess(config, operation, areaId)
                }
            } catch (error: RuntimeException) {
                mapThrowable(operation, propertyId, areaId, error)
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

            val access = config.access
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

        private suspend fun reconcileSubscriptions() {
            val manager =
                carPropertyManager ?: run {
                    connectAsync()
                    return
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
                    RealCarPropertyException.ServiceUnavailable(operation.name.lowercase(), propertyId, areaId, error)
                is IllegalArgumentException ->
                    RealCarPropertyException.InvalidValue(
                        propertyId = propertyId,
                        areaId = areaId,
                        reason = error.message ?: "framework rejected argument",
                        cause = error,
                    )
                is IllegalStateException ->
                    RealCarPropertyException.ServiceUnavailable(operation.name.lowercase(), propertyId, areaId, error)
                else ->
                    RealCarPropertyException.ServiceUnavailable(
                        operation.name.lowercase(),
                        propertyId,
                        areaId,
                        error,
                    )
            }

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> boxedClass(valueClass: Class<T>): Class<T> =
            when (valueClass) {
                Float::class.javaPrimitiveType -> Float::class.javaObjectType as Class<T>
                Int::class.javaPrimitiveType -> Int::class.javaObjectType as Class<T>
                Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType as Class<T>
                else -> valueClass
            }

        private fun <T : Any> RealCarPropertyValue.castValue(valueClass: Class<T>): T {
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

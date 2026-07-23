# `core:realcar` guide

## 1. Purpose

The module provides one API for connecting to CarService and reading, writing, observing,
and bulk-processing vehicle properties. Binder calls run away from the main thread.
Platform and vendor exceptions are normalized as `RealCarPropertyException`.

The library intentionally declares no vehicle permissions. The host application must request
only the permissions needed by its properties:

```xml
<uses-permission android:name="android.car.permission.CONTROL_CAR_CLIMATE" />
<uses-permission android:name="android.car.permission.CAR_INFO" />
<uses-permission android:name="android.car.permission.CAR_SPEED" />
```

Control permissions are commonly signature/privileged. A manifest declaration does not grant
them to a regular APK.

## 2. Construction and lifecycle

Inject the singleton with Hilt:

```kotlin
class ClimateRepository @Inject constructor(
    private val properties: RealCarPropertyManager,
)
```

Without DI:

```kotlin
val properties = RealCarPropertyManager(applicationContext)
lifecycleScope.launch { properties.connect() }

// Close only an instance that your component owns.
properties.close()
```

`connectionState` is a `StateFlow<RealCarConnectionState>`. Concurrent connects are
serialized, subscriptions are restored after reconnect, and stale writes are never replayed.

## 3. Recommended type-safe API

```kotlin
val driverTemperature = RealCarProperty.float(
    propertyId = VehiclePropertyIds.HVAC_TEMPERATURE_SET,
    areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
)

when (val result = properties.read(driverTemperature)) {
    is RealCarPropertyResult.Success -> render(result.value)
    is RealCarPropertyResult.Failure -> {
        renderStale(result.staleValue)
        showError(result.error)
    }
}
```

Writes wait for VHAL confirmation by default:

```kotlin
properties.write(
    property = driverTemperature,
    value = 22.5f,
    waitForPropertyUpdate = true,
    timeoutMillis = 5_000,
)
```

Typed observation:

```kotlin
properties.observe(driverTemperature).collect { result ->
    result.fold(
        onSuccess = { value, source -> render(value, source) },
        onFailure = { error, stale -> renderError(error, stale) },
    )
}
```

## 4. Supported value shapes

| VHAL type | Typed key | Convenience API |
|---|---|---|
| `FLOAT` | `RealCarProperty.float(...)` | `tryGetFloatProperty` |
| `INT32` | `RealCarProperty.int(...)` | `tryGetIntProperty` |
| `BOOLEAN` | `RealCarProperty.boolean(...)` | `tryGetBooleanProperty` |
| `INT64` | `RealCarProperty.long(...)` | `tryGetLongProperty` |
| `STRING` | `RealCarProperty.string(...)` | `tryGetStringProperty` |
| `INT32_VEC` | `RealCarProperty.intArray(...)` | `tryGetIntArrayProperty` |
| `FLOAT_VEC` | `RealCarProperty.floatArray(...)` | `tryGetFloatArrayProperty` |
| `INT64_VEC` | `RealCarProperty.longArray(...)` | `tryGetLongArrayProperty` |
| `BYTES` | `RealCarProperty.byteArray(...)` | `tryGetByteArrayProperty` |
| `MIXED` | `RealCarProperty.mixed(...)` | generic `getProperty` |

Array values are defensively copied into and out of the cache.

## 5. Metadata

```kotlin
val info = properties
    .getPropertyInfo(VehiclePropertyIds.HVAC_TEMPERATURE_SET)
    .getOrThrow()

val driverArea = info.areas.first { it.areaId == VehicleAreaSeat.SEAT_ROW_1_LEFT }
```

`RealCarPropertyInfo` exposes type, access, change mode, areas, min/max, sample rates, and
the config array. Prefer metadata over hard-coded ranges.

Discover the capabilities visible to the current process:

```kotlin
val visibleProperties = properties.getPropertyInfos().getOrThrow()
val writableAreas = visibleProperties.flatMap { it.writableAreas }
```

CarService permission-filters this list. `readableAreas` and `writableAreas` reflect per-area
access overrides instead of relying only on the property-level access flag.

## 6. High-throughput batch reads

```kotlin
val batch = properties.tryGetProperties(
    requests = listOf(
        driverTemperature.asReadRequest(),
        RealCarProperty.string(VehiclePropertyIds.INFO_MAKE).asReadRequest(),
        RealCarProperty.intArray(VehiclePropertyIds.INFO_FUEL_TYPE).asReadRequest(),
    ),
    options = RealCarBatchOptions(
        timeoutMillis = 10_000,
        maxRequestsPerChunk = 100,
        maxConcurrentChunks = 4,
    ),
)
```

Guarantees:

- one ordered result for every input request;
- heterogeneous types in a single batch;
- partial success without discarding successful items;
- bounded chunking to reduce Binder transaction pressure;
- bounded concurrent chunks to protect CarService;
- framework async APIs instead of hundreds of blocking coroutines;
- duplicate property/area/type reads are coalesced into one VHAL call while retaining every ordered result;
- a safe per-item synchronous retry is enabled when a VHAL advertises async support but returns an internal async error;
- structured cancellation through `CancellationSignal`.

Keep concurrency bounded. The default of four concurrent chunks is a deliberate throughput
versus service-load trade-off. Set `fallbackToSynchronousRead=false` when the caller needs the
raw async VHAL failure instead of a retry.

## 7. Confirmed batch writes

```kotlin
val batch = properties.trySetProperties(
    listOf(
        driverTemperature.writeRequest(21.5f),
        passengerTemperature.writeRequest(22.0f),
        acEnabled.writeRequest(true),
    ),
)
```

`waitForPropertyUpdate` defaults to true. A successful item means the framework received a
matching property update from VHAL, not merely that a Binder call returned.

A batch is not an atomic transaction. Always inspect each item and never assume rollback.
Writes for independent property/area pairs may run concurrently. Repeated writes targeting the
same pair are automatically split into waves and serialized in input order, preventing an older
in-flight Binder call from overwriting the final requested value.

## 8. Callbacks and lifecycle

```kotlin
val handle = properties.registerCallbackSafely(
    callback = callback,
    propertyId = VehiclePropertyIds.HVAC_FAN_SPEED,
    areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
).getOrThrow()

handle.close() // idempotent
```

The `LifecycleOwner` overload closes automatically at `onDestroy`. Multiple local listeners
share one platform subscription. Subscription reconciliation is mutex-protected and uses the
highest requested update rate.

## 9. Error handling

Important normalized errors:

- `UnsupportedProperty`;
- `UnsupportedArea`;
- `PermissionDenied`;
- `TypeMismatch`;
- `InvalidValue`;
- `PropertyUnavailable`;
- `Timeout`;
- `ServiceNotReady` and `ServiceUnavailable`;
- `InternalError` for a per-property VHAL internal failure without disconnecting CarService;
- `PlatformFailure` for an unmapped vendor/framework runtime failure;
- callback registration/dispatch failures;
- `AsyncOperationFailed` with platform error codes.

Use `error.isRetryable` as a hint, apply backoff, and re-read current state before retrying a
write. Writes are never automatically replayed after reconnect. `Failure.staleValue` can keep
the UI populated while explicitly marked stale.

## 10. Logging

```kotlin
properties.setTraceLoggingEnabled(BuildConfig.DEBUG)
```

Trace logs may contain property values. Disable them in production when handling identity,
location, or diagnostic data.

## 11. AOSP emulator validation

```powershell
adb shell cmd car_service get-carpropertyconfig HVAC_TEMPERATURE_SET
adb shell cmd car_service get-property-value HVAC_TEMPERATURE_SET 1
.\gradlew.bat :app:connectedDebugAndroidTest
```

The project demo app covers real read/write/Flow usage, all supported value shapes, a
240-request batch, and controlled error scenarios. A VHAL may advertise `BYTES`, `FLOAT_VEC`,
or `INT64` while temporarily providing no value; that condition is surfaced as a typed error.

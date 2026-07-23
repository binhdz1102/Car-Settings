# Hướng dẫn `core:realcar`

## 1. Mục tiêu

Module cung cấp một API thống nhất cho việc kết nối CarService, đọc, ghi, theo dõi và xử lý
số lượng lớn vehicle property. Mọi lời gọi Binder được thực hiện ngoài main thread. Lỗi
platform được chuẩn hóa thành `RealCarPropertyException`, vì vậy UI không cần phụ thuộc vào
exception riêng của từng phiên bản Android hoặc vendor.

Thư viện không tự khai báo quyền trong manifest. Ứng dụng phải yêu cầu đúng quyền của từng
property, ví dụ:

```xml
<uses-permission android:name="android.car.permission.CONTROL_CAR_CLIMATE" />
<uses-permission android:name="android.car.permission.CAR_INFO" />
<uses-permission android:name="android.car.permission.CAR_SPEED" />
```

Các quyền điều khiển thường là signature/privileged. Việc khai báo manifest không tự cấp
quyền cho một APK thông thường.

## 2. Khởi tạo và vòng đời

Với Hilt, inject singleton:

```kotlin
class ClimateRepository @Inject constructor(
    private val properties: RealCarPropertyManager,
)
```

Không dùng DI:

```kotlin
val properties = RealCarPropertyManager(applicationContext)

lifecycleScope.launch {
    when (val result = properties.connect()) {
        is RealCarPropertyResult.Success -> Unit
        is RealCarPropertyResult.Failure -> showError(result.error)
    }
}

// Chỉ đóng instance do chính bạn tạo.
properties.close()
```

`connectionState` là `StateFlow<RealCarConnectionState>`. Manager tự serialize các lần
connect, tự đăng ký lại subscription sau reconnect và không queue lại lệnh ghi cũ.

## 3. API typed được khuyến nghị

Định nghĩa khóa một lần:

```kotlin
val driverTemperature = RealCarProperty.float(
    propertyId = VehiclePropertyIds.HVAC_TEMPERATURE_SET,
    areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
)
```

Đọc không ném exception:

```kotlin
when (val result = properties.read(driverTemperature)) {
    is RealCarPropertyResult.Success -> render(result.value)
    is RealCarPropertyResult.Failure -> {
        renderStale(result.staleValue)
        showError(result.error)
    }
}
```

Ghi an toàn, mặc định chờ VHAL xác nhận:

```kotlin
val result = properties.write(
    property = driverTemperature,
    value = 22.5f,
    waitForPropertyUpdate = true,
    timeoutMillis = 5_000,
)
```

Theo dõi:

```kotlin
properties.observe(driverTemperature)
    .collect { result ->
        result.fold(
            onSuccess = { value, source -> render(value, source) },
            onFailure = { error, stale -> renderError(error, stale) },
        )
    }
```

## 4. Kiểu dữ liệu

| VHAL type | Khóa typed | API tiện ích |
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
| `MIXED` | `RealCarProperty.mixed(...)` | `getProperty(Array::class.java, ...)` |

Mọi mảng được sao chép phòng vệ khi đi vào và ra cache. Caller không thể vô tình thay đổi
snapshot dùng chung.

## 5. Metadata và UI động

```kotlin
val info = properties
    .getPropertyInfo(VehiclePropertyIds.HVAC_TEMPERATURE_SET)
    .getOrThrow()

val driverArea = info.areas.first { it.areaId == VehicleAreaSeat.SEAT_ROW_1_LEFT }
val minimum = driverArea.minimumValue as Float
val maximum = driverArea.maximumValue as Float
```

`RealCarPropertyInfo` chứa type, access, change mode, area, min/max, sample rate và
`configArray`. Không hard-code range nếu VHAL có metadata.

Để dựng màn hình capability/diagnostic theo xe hiện tại:

```kotlin
val visibleProperties = properties.getPropertyInfos().getOrThrow()
val writableAreas = visibleProperties.flatMap { it.writableAreas }
```

Danh sách được CarService lọc theo quyền của process. `readableAreas` và `writableAreas` dùng
access theo từng area, không chỉ access chung của property.

## 6. Batch read hiệu năng cao

```kotlin
val requests = listOf(
    driverTemperature.asReadRequest(),
    RealCarProperty.string(VehiclePropertyIds.INFO_MAKE).asReadRequest(),
    RealCarProperty.intArray(VehiclePropertyIds.INFO_FUEL_TYPE).asReadRequest(),
)

val batch = properties.tryGetProperties(
    requests = requests,
    options = RealCarBatchOptions(
        timeoutMillis = 10_000,
        maxRequestsPerChunk = 100,
        maxConcurrentChunks = 4,
    ),
)

batch.items.forEach { item ->
    when (val result = item.result) {
        is RealCarPropertyResult.Success -> consume(result.value)
        is RealCarPropertyResult.Failure -> log(item.requestIndex, result.error)
    }
}
```

Đặc tính:

- kết quả luôn có một item cho mỗi request và giữ nguyên thứ tự đầu vào;
- property khác kiểu có thể nằm trong cùng batch;
- partial failure không hủy kết quả thành công;
- request lớn được chia chunk để giảm nguy cơ vượt Binder transaction;
- số chunk đồng thời bị giới hạn để không làm nghẽn CarService;
- framework async API được dùng thay vì tạo hàng trăm blocking coroutine;
- request đọc trùng property/area/type chỉ gọi VHAL một lần nhưng vẫn trả đủ item theo thứ tự;
- nếu VHAL quảng bá async nhưng trả lỗi nội bộ, mặc định thư viện thử lại từng item bằng API đồng bộ an toàn;
- coroutine cancellation sẽ hủy `CancellationSignal`.

Không đặt `maxConcurrentChunks` quá cao. Giá trị mặc định 4 cân bằng throughput và tải
CarService trên phần lớn head unit. Có thể đặt `fallbackToSynchronousRead=false` nếu ứng dụng
muốn nhận nguyên trạng lỗi async của VHAL thay vì retry.

## 7. Batch write có xác nhận

```kotlin
val batch = properties.trySetProperties(
    listOf(
        driverTemperature.writeRequest(21.5f),
        passengerTemperature.writeRequest(22.0f),
        acEnabled.writeRequest(true),
    ),
)
```

`waitForPropertyUpdate=true` là mặc định. Success nghĩa là framework đã nhận update phù hợp
từ VHAL, không chỉ gửi xong Binder call. Với telemetry fire-and-forget, caller có thể tắt
tùy chọn này một cách rõ ràng.

Batch không phải transaction nguyên tử. Một số item có thể thành công trong khi item khác
thất bại. Không dùng batch này để giả lập rollback.

Các request ghi khác property/area được chạy song song. Nếu nhiều request cùng nhắm một
property/area, thư viện tự chia thành các lượt và ghi tuần tự đúng thứ tự đầu vào; nhờ vậy giá
trị cuối không bị một Binder call cũ đang chạy ghi đè.

## 8. Callback truyền thống và lifecycle

```kotlin
val handle = properties.registerCallbackSafely(
    callback = callback,
    propertyId = VehiclePropertyIds.HVAC_FAN_SPEED,
    areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
).getOrThrow()

handle.close() // idempotent
```

Hoặc overload nhận `LifecycleOwner` sẽ tự đóng ở `onDestroy`.

Nhiều listener cùng property dùng chung một platform subscription. Manager chọn update rate
cao nhất đang được yêu cầu và reconcile subscription bằng mutex để tránh race.

## 9. Xử lý lỗi

Các nhánh thường gặp:

- `UnsupportedProperty`: xe không có property;
- `UnsupportedArea`: area không nằm trong config;
- `PermissionDenied`: thiếu quyền hoặc access mode không cho phép;
- `TypeMismatch`: type yêu cầu khác `CarPropertyConfig.propertyType`;
- `InvalidValue`: giá trị/range bị framework từ chối;
- `PropertyUnavailable`: ECU/VHAL chưa có dữ liệu; xem `isRetryable`;
- `Timeout`: async request quá hạn;
- `ServiceNotReady`/`ServiceUnavailable`: CarService đang connect hoặc mất kết nối;
- `InternalError`: VHAL lỗi xử lý một property nhưng CarService vẫn kết nối; có thể retry;
- `PlatformFailure`: runtime vendor/framework không thuộc taxonomy chuẩn, không tự reconnect;
- `CallbackRegistrationFailed`/`CallbackDispatchFailed`;
- `AsyncOperationFailed`: lỗi async kèm platform error code.

Quy tắc retry:

```kotlin
if (failure.error.isRetryable) {
    // Dùng backoff và đọc lại trạng thái hiện tại trước khi retry write.
}
```

Không tự động phát lại write sau reconnect vì lệnh cũ có thể không còn đúng với trạng thái
xe hiện tại. Read có thể dùng `staleValue` để tiếp tục hiển thị dữ liệu cũ với nhãn stale.

## 10. Logging và dữ liệu nhạy cảm

```kotlin
properties.setTraceLoggingEnabled(BuildConfig.DEBUG)
```

Trace log có thể chứa giá trị property. Tắt trong production nếu property mang dữ liệu định
danh, vị trí hoặc chẩn đoán.

## 11. Kiểm thử trên AOSP emulator

```powershell
adb shell cmd car_service get-carpropertyconfig HVAC_TEMPERATURE_SET
adb shell cmd car_service get-property-value HVAC_TEMPERATURE_SET 1
.\gradlew.bat :app:connectedDebugAndroidTest
```

App demo trong project minh họa read/write/Flow, mọi kiểu dữ liệu, batch 240 request và ma trận
lỗi. Một số kiểu như `BYTES`, `FLOAT_VEC` hoặc `INT64` có thể được VHAL khai báo nhưng chưa có
giá trị; UI hiển thị typed error thay vì crash.

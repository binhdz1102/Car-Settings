# Kiểm chứng LocalCallback với system API trên AAOS

## Phạm vi đã triển khai

Demo dùng API thật của máy ảo, không dùng fake source, cho 7 nhóm thường gặp trong Android
Automotive:

| API | Callback được bọc | Tình huống automotive |
|---|---|---|
| `CarPropertyManager` | `CarPropertyEventCallback` cho `PERF_VEHICLE_SPEED` 10 Hz | VHAL/vehicle telemetry |
| `SensorManager` | `SensorEventListener` cho accelerometer 50 Hz | motion/orientation/driving context |
| `ConnectivityManager` | default `NetworkCallback` | trạng thái mạng mặc định |
| `AudioManager` | `AudioDeviceCallback` | thay đổi USB/Bluetooth audio route |
| `WifiManager` | `ScanResultsCallback` | refresh danh sách Wi-Fi |
| `BluetoothManager` | receiver cho adapter state | pairing/media UI |
| `DisplayManager` | `DisplayListener` | cluster/passenger display |

Adapter nằm trong `SystemCallbackSources.kt`. `AutomotiveLocalCallbackHub` trong cùng file là mẫu
owner application/repository scope, gồm key ổn định, lựa chọn `state`/`events`, buffer và `close()`.
`SystemCallbackBenchmark.kt` là runner dùng chung cho UI và instrumentation test.

## Cách dùng trong ViewModel hoặc lifecycle owner

Khởi tạo `AutomotiveLocalCallbackHub` đúng một lần trong application-scoped DI component. Không tạo
mỗi ViewModel một hub.

```kotlin
val job = callbackHub.carSpeed.subscribe(
    scope = viewModelScope,
    onEvent = { update -> render(update.detail) },
    onFailure = { error -> report(error) },
)

// Chỉ hủy consumer cục bộ; các consumer khác tiếp tục dùng registration chung.
job.cancel()
```

Hoặc collect Flow:

```kotlin
viewModelScope.launch {
    callbackHub.defaultNetwork.events.collect(::renderNetwork)
}
```

Trong production nên thay `SystemCallbackEvent.detail` dùng cho demo bằng model typed riêng của
từng domain. Đặc biệt phải copy dữ liệu callback-owned trước khi emit; adapter Sensor và Audio trong
demo đã làm điều này.

## Phương pháp benchmark

Mỗi API chạy một warm-up và 5 vòng đo:

1. Baseline trực tiếp tạo 32 callback object và gọi API đăng ký hệ thống 32 lần.
2. Nhánh LocalCallback tạo 32 collector nhưng chỉ gọi API đăng ký hệ thống một lần.
3. Mỗi vòng quan sát callback thật trong 300 ms.
4. Thứ tự direct/local được đảo ở từng vòng để giảm bias do tải/thermal drift.
5. Callback initial-state được drain sau attach; snapshot LocalCallback đợi fan-out worker account
   đủ source event trước khi chốt delivery.
6. Báo attach p50/p95, detach p50, callback platform, delivery consumer, process CPU và drop.

Số “system API registrations” là số lần code gọi API manager. Một số Android manager có thể tự
coalesce Binder/native work bên trong framework; app không thể suy ra số Binder transaction chỉ từ
callback count, vì vậy tài liệu không gọi số này là “Binder calls”.

Các API event-driven có thể hợp lệ khi báo 0 callback nếu thiết bị không đổi trạng thái trong cửa
sổ 300 ms. `CarPropertyManager` và `SensorManager` là hai nguồn continuous dùng để xác minh delivery
thật. Instrumentation test bắt buộc hai nguồn này có event và bắt buộc mọi API đăng ký được, có tỷ
lệ registration 32:1 và không drop.

## Kết quả trên emulator hiện tại

Thiết bị: `sdk_car_x86_64`, Android API 37, build
`CP2A.260605.016`, 32 consumer, 5 vòng, cửa sổ 300 ms.

| API | Attach p50 direct | Attach p50 Local | Tỷ lệ direct/Local | Callback platform direct/Local | Delivery direct/Local | CPU direct/Local |
|---|---:|---:|---:|---:|---:|---:|
| CarProperty | 173.388 ms | 11.247 ms | 15.42x | 96 / 3 | 96 / 96 | 17 / 42 ms |
| Sensor | 117.642 ms | 6.398 ms | 18.39x | 512 / 10 | 512 / 320 | 34 / 100 ms |
| Connectivity | 60.226 ms | 5.744 ms | 10.48x | 0 / 0 | 0 / 0 | 2 / 2 ms |
| Audio | 2.647 ms | 5.627 ms | 0.47x | 0 / 0 | 0 / 0 | 1 / 2 ms |
| Wi-Fi | 50.656 ms | 5.544 ms | 9.14x | 0 / 0 | 0 / 0 | 2 / 1 ms |
| Bluetooth | 40.153 ms | 3.889 ms | 10.33x | 0 / 0 | 0 / 0 | 2 / 1 ms |
| Display | 1.825 ms | 3.855 ms | 0.47x | 0 / 0 | 0 / 0 | 1 / 1 ms |

Mọi nhánh Local đều dùng 1 thay vì 32 lời gọi đăng ký và tổng drop của 5 vòng là 0. Đối với
CarProperty, 3 callback nguồn được fan-out thành 96 delivery, đúng `3 × 32`. Với Sensor, 10 callback
nguồn thành 320 delivery, đúng `10 × 32`.

Kết luận cần đọc thận trọng:

- Lợi ích chắc chắn là giảm registration ownership/churn và số callback object từ `N` xuống 1.
- Attach cải thiện lớn với CarProperty, Sensor, Connectivity, Wi-Fi và Bluetooth trên máy ảo này.
- Audio và Display có API registration nội bộ rất rẻ; attach LocalCallback chậm hơn do chi phí
  coroutine/mailbox. Không nên dùng LocalCallback chỉ để tối ưu tốc độ nếu nguồn vốn chỉ có một
  consumer.
- CPU trong cửa sổ Sensor cao hơn ở nhánh Local (100 ms so với 34 ms). Fan-out qua mailbox độc lập
  đổi lấy isolation/backpressure và một registration duy nhất, không phải phép tối ưu CPU miễn phí.
- Số 0 của API event-driven chỉ có nghĩa là emulator không đổi trạng thái trong 300 ms; không phải
  callback không hoạt động. Test đã xác minh register/unregister thật nhưng cần kịch bản chủ động
  toggle Wi-Fi/Bluetooth, đổi audio/display hoặc kết nối mạng để benchmark event delivery của chúng.
- Đây là số đo emulator/debug build, không thay cho Macrobenchmark trên target head unit/release
  build. Nên chạy lại với số consumer, rate, buffer và workload giống sản phẩm.

## Chạy lại

```powershell
.\gradlew.bat :runtime:localcallback:testDebugUnitTest
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w -r `
  -e class com.android.car.settings.SystemCallbackBenchmarkInstrumentedTest `
  com.android.car.settings.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s SystemCallbackBenchmark:I SystemCallbackBenchmarkTest:I "*:S"
```

Có thể mở trực tiếp màn hình:

```powershell
adb shell am start -n `
  com.android.car.settings/.LocalCallbackDemoActivity
```

Sau đó chọn `Run real system API suite`. Kết quả chi tiết cũng được ghi với tag
`SystemCallbackBenchmark`.

## Cách quyết định cho các manager còn lại

Không phải mọi tên `*Manager` đều có callback thích hợp để fan-out:

- Rất phù hợp khi nhiều consumer dùng cùng registration/options: `AppOpsManager`,
  `CameraManager`, `ClipboardManager`, `LocationManager`, `MediaRouter`, `SensorManager`,
  `StorageManager`, `TelephonyManager`, `WifiManager`, `WallpaperManager`.
- Phù hợp nếu key chứa đầy đủ filter/rate/user/session: `BluetoothManager`, `ConnectivityManager`,
  `DisplayManager`, `InputManager`, `NfcManager`, `PackageManager`, `PowerManager`, `UsbManager`.
- Thường là command/query hoặc callback có vòng đời riêng, không nên bọc máy móc:
  `ActivityManager`, `AlarmManager`, `BatteryManager`, `DevicePolicyManager`, `DownloadManager`,
  `KeyguardManager`, `NotificationManager`, `SearchManager`, `ShortcutManager`, `UiModeManager`,
  `UserManager`, `VibratorManager`, `WindowManager`.
- `FingerprintManager`, `FaceManager`, `MediaProjectionManager` và nhiều API Telecom là session,
  authentication hoặc consent callback. Không chia sẻ callback giữa các request độc lập; chỉ dùng
  LocalCallback cho availability/state observer thật sự tương đương.

Nguyên tắc quan trọng: key phải biểu diễn registration equivalence (property/area/rate, sensor/rate,
network request, scan filter, user hoặc display). Nếu consumer cần option khác nhau, dùng key khác
hoặc thiết kế cơ chế aggregate/renegotiate rõ ràng.

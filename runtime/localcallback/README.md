# LocalCallback

`runtime-localcallback` turns one expensive external callback registration into a locally shared,
type-safe `Flow`. It is intended for platform/service APIs such as `CarPropertyManager`,
`AudioManager`, `SensorManager`, Bluetooth, connectivity callbacks, WebSockets, or a proprietary
SDK where many screens, repositories, or ViewModels need the same source at the same time.

Artifact for release 1.2.2:

```kotlin
implementation("com.b231001.bmaterial:runtime-localcallback:1.2.2")
```

## English

### Why this exists

Without sharing, `N` consumers commonly create `N` platform callback objects, execute `N`
registrations, receive `N` binder/native calls per source event, and perform `N` unregister calls.
With `LocalCallback`, the external cost is one registration while it is needed, followed by local
fan-out:

```text
expensive manager / SDK
          |
          | one callback registration
          v
   bounded ingress channel  <-- callback thread only does a non-blocking enqueue
          |
          v
       fan-out worker
       /    |    \
 bounded bounded bounded    <-- one independent mailbox per consumer
    A       B       C
```

Local delivery is still `O(N)` because every interested consumer must receive the value, but the
expensive binder/native/SDK registration and callback boundary are no longer duplicated. A slow
consumer cannot block the source callback thread or another consumer.

### Recommended ownership

- Create one `LocalCallbackRegistry` per physical manager/source scope, usually in an
  application-scoped DI component, long-lived service, or feature owner.
- Create every `LocalCallbackKey` once and reuse that exact instance. Keys are identity-based:
  two keys with the same display name are deliberately different.
- Close a feature-scoped registry when the feature is permanently destroyed. An application-scoped
  registry may live until application/process shutdown.
- Sharing is in-process. Separate Android processes need their own registry or an IPC service that
  owns the real callback.

Creating a registry inside each ViewModel defeats the sharing optimization.

### Quick start

```kotlin
object AppCallbacks : AutoCloseable {
    private val registry = LocalCallbackRegistry()
    private val temperatureKey = LocalCallbackKey.create<Float>("cabin-temperature")

    val cabinTemperature: LocalCallback<Float> = registry.getOrCreate(
        key = temperatureKey,
        config = LocalCallbackConfig.state(
            stopTimeoutMillis = 5_000L,
        ),
    ) {
        localCallbackSource(
            callbackFactory = { emitter ->
                ExpensiveManager.Callback { value -> emitter.emit(value) }
            },
            register = expensiveManager::registerCallback,
            unregister = expensiveManager::unregisterCallback,
        )
    }

    override fun close() = registry.close()
}
```

Flow-oriented consumers collect `events`:

```kotlin
viewModelScope.launch {
    AppCallbacks.cabinTemperature.events.collect { temperature ->
        updateUi(temperature)
    }
}
```

Callback-oriented consumers can use the convenience API and cancel only their local subscription:

```kotlin
val subscription = AppCallbacks.cabinTemperature.subscribe(
    scope = lifecycleScope,
    onEvent = ::renderTemperature,
    onFailure = ::reportFatalSourceFailure,
)

subscription.cancel()
```

The first local collector starts the external registration. Concurrent collectors share it. When
the final collector leaves, unregister happens immediately or after `stopTimeoutMillis`. The delay
prevents registration churn during short configuration/navigation gaps.

### State versus events

Use `LocalCallbackConfig.state()` for current data such as speed, temperature, volume, or device
connection state. It caches and replays the most recent value to a later consumer.

Use `LocalCallbackConfig.events()` for one-time occurrences such as a button signal, route change,
or an audio-device added/removed notification. It does not replay an old occurrence and defaults to
immediate unregistration.

Do not use state replay for commands or one-time effects. Do not use event mode when a newly opened
screen must immediately know the current value.

### Backpressure and delivery guarantees

The external callback thread never suspends. `sourceBufferCapacity` bounds the source-to-worker
queue; `subscriberBufferCapacity` bounds each consumer mailbox:

- `DROP_OLDEST` keeps newer real-time data and discards the oldest queued item.
- `DROP_LATEST` preserves already queued items and discards the new item.
- `snapshot()` exposes `droppedSourceEvents` and `droppedDeliveries`; monitor both in stress tests
  and production telemetry.

Larger buffers absorb bounded bursts but are not an end-to-end lossless guarantee. If every event
must be persisted exactly once, use an application protocol with sequence IDs, acknowledgement,
retry, and durable storage instead of relying only on callback buffers.

### Failures and retry behavior

- Throwing from `LocalCallbackSource.register` terminates the current collectors. A later collection
  makes a new registration attempt.
- `emitter.fail(cause)` means the active source generation is fatally unusable. Current collectors
  terminate, the external registration is closed, and a later collector can start a new generation.
- Recoverable domain errors should be values in the event type, for example
  `sealed interface ResultEvent`, rather than `emitter.fail`.
- Late events from an old registration generation are rejected after unregister/restart.
- `remove(key)` and `registry.close()` terminate attached collectors with
  `LocalCallbackClosedException`.

Automatic retry is intentionally not hidden inside the registry. Put the retry/backoff policy at
the repository layer so the application controls eligibility, delay, logging, and cancellation.

### Key design for real systems

A key must represent physical registration equivalence, not just a friendly name. For an automotive
property, include the property ID, area/zone policy, and sampling policy. For sensors, include sensor
type and sampling latency. For a WebSocket, include endpoint and authenticated session.

Do not let the first consumer silently choose a sample rate for all later consumers. Either:

1. create separate keys for genuinely different rates;
2. register once at the highest required rate and downsample locally; or
3. build a source adapter that explicitly renegotiates the aggregate rate.

Reusing the same key with a different `LocalCallbackConfig` fails fast.

### CarPropertyManager recipe

Keep `android.car` integration in the automotive application/module so this generic artifact does
not require the car SDK. The following adapter illustrates a state stream:

```kotlin
data class CarPropertyUpdate(
    val propertyId: Int,
    val areaId: Int,
    val value: Any?,
)

fun carPropertySource(
    manager: CarPropertyManager,
    propertyId: Int,
    sampleRate: Float,
): LocalCallbackSource<CarPropertyUpdate> = localCallbackSource(
    callbackFactory = { emitter ->
        object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                emitter.emit(
                    CarPropertyUpdate(
                        propertyId = value.propertyId,
                        areaId = value.areaId,
                        value = value.value,
                    ),
                )
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                // Prefer a typed recoverable event if this is not fatal.
            }
        }
    },
    register = { callback ->
        check(manager.registerCallback(callback, propertyId, sampleRate))
    },
    unregister = manager::unregisterCallback,
)
```

Create the key and callback once in an automotive service/repository:

```kotlin
val cabinTemperatureKey =
    LocalCallbackKey.create<CarPropertyUpdate>("car-property:$propertyId:all-areas:5hz")

val cabinTemperature = registry.getOrCreate(
    key = cabinTemperatureKey,
    config = LocalCallbackConfig.state(),
) {
    carPropertySource(carPropertyManager, propertyId, sampleRate = 5f)
}
```

The app must still declare the permissions required by that vehicle property. Some properties are
privileged and cannot be exercised by an ordinary emulator application; use a deterministic fake
source for library acceptance tests and an appropriately signed automotive app for vehicle-specific
integration.

### AudioManager recipe

Use a typed event because added and removed device notifications are occurrences, not current state.
Copy callback-owned arrays before crossing the asynchronous boundary.

```kotlin
sealed interface AudioDeviceEvent {
    data class Added(val devices: List<AudioDeviceInfo>) : AudioDeviceEvent
    data class Removed(val devices: List<AudioDeviceInfo>) : AudioDeviceEvent
}

fun audioDeviceSource(
    audioManager: AudioManager,
    handler: Handler,
): LocalCallbackSource<AudioDeviceEvent> = localCallbackSource(
    callbackFactory = { emitter ->
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                emitter.emit(AudioDeviceEvent.Added(addedDevices.toList()))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                emitter.emit(AudioDeviceEvent.Removed(removedDevices.toList()))
            }
        }
    },
    register = { callback ->
        audioManager.registerAudioDeviceCallback(callback, handler)
    },
    unregister = audioManager::unregisterAudioDeviceCallback,
)

val audioDevices = registry.getOrCreate(
    key = LocalCallbackKey.create("audio-device-events"),
    config = LocalCallbackConfig.events(),
) {
    audioDeviceSource(audioManager, callbackHandler)
}
```

Use one key per callback family. A volume observer, playback callback, recording callback, and
device callback have different semantics and should not be merged merely because they all come from
`AudioManager`.

### Other suitable scenarios

| Source | Suggested mode | Key dimensions |
|---|---|---|
| `SensorManager` | state for latest reading; events for steps/triggers | sensor, rate, latency |
| `ConnectivityManager` | state | network request/capabilities |
| Bluetooth scan | events or app-aggregated state | filters, scan settings, session |
| location provider | state | provider, accuracy, interval |
| WebSocket/SSE | events | endpoint, auth/session, protocol |
| proprietary SDK listener | state or events | physical resource and listener options |

Avoid `LocalCallback` when registration is already cheap and isolated, consumers require physically
different source options, data volume is better handled by a database/queue, or cross-process
sharing is required without an IPC owner.

### Diagnostics and verification

`lifecycle` is a low-frequency `StateFlow` for status, subscriber count, and registration state.
`snapshot()` reads high-frequency counters without triggering UI updates for every event:

```kotlin
val metrics = callback.snapshot()
check(metrics.successfulRegistrations == 1L)
check(metrics.droppedSourceEvents == 0L)
check(metrics.droppedDeliveries == 0L)
```

The module includes deterministic JVM tests for lifecycle, retry, replay, generation fencing, both
overflow policies, 10,000 simultaneous subscribers, 200,000 deliveries, and 2,048,000 concurrent
producer deliveries. Its device test performs 1,024,000 deliveries. The debug app includes an
interactive bilingual demo and an instrumentation test for the complete registration lifecycle.

```powershell
.\gradlew.bat :runtime:localcallback:testDebugUnitTest
.\gradlew.bat :runtime:localcallback:connectedDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Tiếng Việt

### Mục tiêu và lợi ích

Nếu `N` màn hình/repository tự đăng ký cùng một callback đắt đỏ, hệ thống phải tạo `N` callback,
thực hiện `N` lần đăng ký, nhận `N` binder/native call cho mỗi dữ liệu và hủy `N` lần.
`LocalCallback` chỉ đăng ký nguồn bên ngoài một lần khi có nhu cầu, sau đó phân phối nội bộ đến từng
consumer bằng mailbox riêng có giới hạn.

Chi phí phân phối nội bộ vẫn là `O(N)` vì mọi consumer cần nhận dữ liệu, nhưng chi phí qua biên
binder/native/SDK chỉ còn một lần. Callback thread chỉ enqueue không chờ; consumer chậm không chặn
nguồn hoặc consumer khác.

### Cách tổ chức trong chương trình lớn

- Đặt `LocalCallbackRegistry` ở application scope, service sống lâu, hoặc feature scope tương ứng
  với vòng đời thật của manager.
- Tạo `LocalCallbackKey` một lần trong DI/repository và dùng lại đúng object đó. Hai key trùng tên
  vẫn khác nhau vì key dùng identity.
- Không tạo registry riêng cho từng ViewModel, nếu không sẽ mất lợi ích dùng chung.
- Cơ chế chỉ dùng chung trong một process. Nhiều process cần một IPC service đứng ra sở hữu callback
  hoặc mỗi process có registry riêng.
- Gọi `close()` khi owner bị hủy vĩnh viễn; `close()` và handle unregister đều idempotent.

### Chọn state hay event

- `LocalCallbackConfig.state()` dùng cho dữ liệu hiện tại như nhiệt độ, tốc độ, volume, kết nối.
  Giá trị mới nhất được replay cho consumer đến sau; timeout mặc định 5 giây giúp tránh
  register/unregister liên tục khi chuyển màn hình ngắn.
- `LocalCallbackConfig.events()` dùng cho sự kiện một lần như thiết bị audio được thêm/xóa hoặc
  trigger. Không replay sự kiện cũ và mặc định unregister ngay khi consumer cuối rời đi.

Không dùng state replay cho command/side effect một lần. Không dùng event nếu màn hình mới cần biết
ngay trạng thái hiện tại.

### Buffer, mất dữ liệu và lỗi

`sourceBufferCapacity` giới hạn hàng đợi từ callback nguồn; `subscriberBufferCapacity` giới hạn
mailbox của từng consumer. `DROP_OLDEST` ưu tiên dữ liệu thời gian thực mới, còn `DROP_LATEST` giữ
dữ liệu đã xếp hàng. Theo dõi `droppedSourceEvents` và `droppedDeliveries` bằng `snapshot()`.

Tăng buffer chỉ hấp thụ burst có giới hạn, không bảo đảm lossless từ đầu đến cuối. Dữ liệu bắt buộc
exactly-once cần sequence ID, acknowledgement, retry và storage bền vững ở tầng ứng dụng.

Exception khi register hoặc `emitter.fail()` sẽ kết thúc các collector hiện tại và đóng registration.
Collector mới có thể tạo generation mới. Lỗi nghiệp vụ có thể phục hồi nên được emit dưới dạng một
event typed thay vì gọi `fail`. Event đến trễ từ generation cũ bị loại bỏ.

Thư viện không tự retry ngầm. Repository của ứng dụng nên quyết định backoff, điều kiện retry, log
và cancellation.

### Thiết kế key và sample rate

Key phải mô tả các tham số làm cho hai registration thực sự tương đương: property/sensor ID,
area/zone, rate, latency, filter, endpoint hoặc session. Không để consumer đầu tiên âm thầm chọn
sample rate cho toàn hệ thống. Hãy dùng key riêng, đăng ký rate cao nhất rồi downsample nội bộ, hoặc
viết adapter có cơ chế tổng hợp/đàm phán rate rõ ràng.

Recipe `CarPropertyManager` và `AudioManager` ở phần tiếng Anh phía trên có thể đặt trực tiếp trong
module ứng dụng tương ứng. Artifact này cố ý không phụ thuộc `android.car`, nên vẫn dùng được cho app
Android thường và dễ mock test. Permission/privileged property của AAOS vẫn do ứng dụng tích hợp
chịu trách nhiệm.

### Demo và kiểm thử

Trong debug catalog, chọn thẻ `LocalCallback` để mở demo. Demo hiển thị ba consumer dùng chung đúng
một registration, phát từng event/burst, hủy từng consumer, số lần unregister và stress test
`256 × 4.000 = 1.024.000` lượt phân phối trên thiết bị.

Test JVM bao phủ vòng đời, đăng ký đồng thời, lỗi/retry, state replay, event không replay,
generation cũ, hai overflow policy, 10.000 subscriber và hàng triệu lượt phân phối. Instrumentation
test xác minh lại vòng đời UI và stress test trên emulator/thiết bị thật.

### Checklist tích hợp

1. Xác định owner scope thật của manager và tạo một registry trong scope đó.
2. Xác định state/event và đầy đủ chiều của key.
3. Adapter phải trả đúng handle unregister cho chính callback đã register.
4. Callback nguồn không làm việc nặng; copy dữ liệu mutable rồi `emit`.
5. Collector gắn với lifecycle/scope và hủy Job khi không dùng.
6. Theo dõi lifecycle/snapshot, đặc biệt drop và registration count.
7. Stress test với số subscriber, burst và tốc độ consumer giống production.
8. `close()` registry khi owner kết thúc vĩnh viễn.

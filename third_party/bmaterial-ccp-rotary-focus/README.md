# CCP Rotary Focus

`ccp-rotary-focus` is B-Material's experimental Android Automotive rotary bridge. It gives a
Compose-style API while keeping the Android View focus hierarchy as the only source of truth.

`ccp-rotary-focus` là cầu nối rotary thử nghiệm của B-Material dành cho Android Automotive. Module
cung cấp DSL gần với Compose nhưng Android View focus hierarchy vẫn là source of truth duy nhất.

> Development status / Trạng thái: the View protocol is implemented and builds successfully, but
> the behavior still needs validation on the target AAOS device/OEM RotaryService. Detailed logging
> is enabled by default for this phase.

## Why this architecture? / Vì sao dùng kiến trúc này?

Compose `focusable`, `focusRequester`, and focus modifiers do not become the View nodes expected by
AAOS RotaryService. This module instead creates this real hierarchy:

```text
Window decor
└── RotaryFocusHostView
    ├── com.android.car.ui.FocusParkingView  (first focusable View)
    └── render-only Compose host
        └── com.android.car.ui.FocusArea      (real ViewGroup)
            └── area Compose layout host
                └── arbitrary Column / Row / Box / LazyColumn
                    └── AndroidView holder
                        └── FocusItemView      (real focusable View)
                            └── ComposeView    (render-only visual)
```

The `FocusItemView` blocks Android focus and accessibility focus from entering its nested
`ComposeView`. Accessibility semantics are copied to the real wrapper through `FocusItemSemantics`.
No Compose focus API is used internally.

`FocusItemView` chặn Android focus/accessibility focus đi vào `ComposeView` con. Semantics được đưa
lên View thật qua `FocusItemSemantics`. Module không dùng Compose focus API ở bên trong.

## Add the module / Thêm module

In this repository:

```kotlin
dependencies {
    implementation(projects.ccpRotaryFocus)
}
```

Published artifact coordinates follow the rest of B-Material:

```kotlin
implementation("com.b231001.bmaterial:ccp-rotary-focus:<version>")
```

The module includes standalone Apache-2.0-compatible implementations with the exact accessibility
class contracts `com.android.car.ui.FocusArea` and `com.android.car.ui.FocusParkingView`. Do not add
a second statically linked `car-ui-lib` containing the same classes to the same application; that
would create duplicate classes. OEM apps that already link a customized Car UI Library should use
one implementation only.

Module tự đóng gói implementation tương thích với đúng class contract của Car UI. Không link thêm
một `car-ui-lib` khác chứa cùng class vào cùng ứng dụng vì sẽ gây duplicate class.

## Recommended Activity setup / Thiết lập Activity khuyến nghị

Use `setRotaryContent` instead of the normal `setContent` for the rotary window:

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var rotaryController: RotaryFocusController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rotaryController = setRotaryContent(hostId = "main-window") {
            AppTheme {
                AutomotiveScreen()
            }
        }
    }
}
```

This installs `FocusParkingView` as the first focusable View and changes the render host's
accessibility class so RotaryService can use the real View `focusSearch()` chain, including
`nextFocusForwardId`. `RotaryFocusHost` is also available inside an existing Compose tree, but the
Activity integration is preferred: some AAOS releases treat a standard outer `ComposeView` as a
virtual hierarchy and use depth-first traversal instead of View focus links.

Cách này đặt `FocusParkingView` đúng vị trí đầu tiên trong window và giữ `focusSearch()` của View
hoạt động. `RotaryFocusHost` vẫn dùng được khi tích hợp vào cây Compose có sẵn, nhưng
`setRotaryContent` là đường production được khuyến nghị.

## FocusArea and FocusItem DSL

```kotlin
@Composable
fun AutomotiveScreen() {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        FocusArea(
            id = FocusAreaId("navigation"),
            modifier = Modifier.width(240.dp),
            firstFocusAt = FocusItemId("media"),
            wrapAround = true,
            layout = FocusAreaLayout(
                orientation = FocusAreaOrientation.Vertical,
                itemSpacing = 8.dp,
            ),
        ) {
            FocusItem(
                id = FocusItemId("media"),
                nextFocusItem = FocusItemId("climate"),
                onClick = ::openMedia,
                semantics = FocusItemSemantics(
                    label = "Media",
                    role = FocusItemRole.Button,
                ),
            ) { state ->
                NavigationTile(
                    label = "Media",
                    focused = state.isFocused,
                    enabled = state.isEnabled,
                )
            }

            FocusItem(
                id = FocusItemId("climate"),
                previousFocusItem = FocusItemId("media"),
                onClick = ::openClimate,
                semantics = FocusItemSemantics("Climate"),
            ) { state ->
                NavigationTile("Climate", state.isFocused, state.isEnabled)
            }
        }
    }
}
```

`FocusItem` is a top-level composable backed by the current area owner. It can be declared at any
depth below a `FocusArea`, including in an extracted composable:

```kotlin
@Composable
fun ActionGroup() {
    Column {
        Row {
            FocusItem(
                id = FocusItemId("save"),
                modifier = Modifier.weight(1f),
                layout = FocusItemLayout(fillCrossAxis = false),
                onClick = ::save,
            ) { state -> SaveVisual(state.isFocused) }
        }
    }
}

FocusArea(id = FocusAreaId("settings")) {
    Column {
        Text("Non-focusable heading")
        ActionGroup()
    }
}
```

`FocusItem` hiện có thể nằm ở bất kỳ vị trí nào trong cây Compose con của `FocusArea`, kể cả trong
composable được tách riêng. View bên dưới vẫn là descendant thật của `com.android.car.ui.FocusArea`.
Không đặt `FocusArea` trong `FocusArea`, và không đặt item của area cha vào Compose `Dialog`/`Popup`;
hãy dùng `RotaryFocusDialog` với một area riêng cho window mới.

### Explicit focus order / Thứ tự focus chỉ định

Layout order and rotary order may be different:

```kotlin
val button1 = FocusItemId("button-1")
val button2 = FocusItemId("button-2")
val button3 = FocusItemId("button-3")

FocusArea(
    id = FocusAreaId("ordered-buttons"),
    focusOrder = listOf(button1, button3, button2),
    wrapAround = true,
) {
    OrderedButtons() // Physical layout may still be Button 1, Button 2, Button 3.
}
```

The rotary sequence is `Button 1 -> Button 3 -> Button 2 -> Button 1`; backward traversal is the
reverse. `nextFocusItem`/`previousFocusItem` remain available for edge-specific overrides.

Thứ tự trong `focusOrder` là logical source of truth, không phụ thuộc thứ tự attach/recompose của
View. Đây là cách khuyến nghị cho layout phức tạp và cần dùng cho danh sách lazy dài.

To connect otherwise independent areas, declare each direction explicitly:

```kotlin
FocusArea(
    id = FocusAreaId("navigation"),
    wrapAround = false,
    nextFocusArea = FocusAreaId("controls"),
) { /* navigation items */ }

FocusArea(
    id = FocusAreaId("controls"),
    wrapAround = false,
    previousFocusArea = FocusAreaId("navigation"),
) { /* control items */ }
```

Khai báo liên kết theo từng chiều như trên để phân biệt các vùng nối tiếp với các vùng độc lập.
`wrapAround` luôn được ưu tiên hơn liên kết sang vùng khác.

Behavior / Hành vi:

- Rotate advances between real `FocusItemView` nodes in `focusOrder`, then appends live items not
  explicitly listed. Without `focusOrder`, initial View registration/declaration order is used.
  `nextFocusItem`/`previousFocusItem` can override individual edges.
- Tilt/nudge searches another real `FocusArea` geometrically, like XML Car UI.
- `firstFocusAt` applies the first time the area is entered. For a lazy area, its logical ID is
  revealed even when it is outside the viewport. Later entries restore the last valid item; an
  unavailable/disabled target advances to the next enabled logical item.
- `isEnabled = false` removes one item from the rotary chain.
- `isFocusAllowed = false` keeps all Compose rendering visible but makes every item in the area
  unfocusable. If the area currently owns focus, focus moves to another area or is parked safely.
- `wrapAround = true` maps last-to-first and first-to-last rotation inside the same area. With
  `wrapAround = false`, a boundary holds focus unless `nextFocusArea`/`previousFocusArea` declares
  an explicit directional link. Linked forward entry selects the first focusable item; linked
  backward entry selects the last focusable item. Disabled items and disallowed areas are skipped.
- Tiếng Việt: `wrapAround = true` cho phép xoay từ item cuối về item đầu và ngược lại trong cùng
  vùng. Khi tắt, chỉ `nextFocusArea`/`previousFocusArea` mới cho phép xoay qua vùng được nối; vùng
  độc lập sẽ giữ focus tại biên.
- FocusAreas must not be nested or overlap. The controller validates both cases at runtime.

- Rotate chuyển giữa các `FocusItemView` thật; tilt/nudge chuyển vùng `FocusArea`.
- `firstFocusAt` chỉ áp dụng lần đầu, các lần sau khôi phục item hợp lệ gần nhất.
- `isFocusAllowed = false` không ẩn UI, chỉ loại toàn bộ vùng khỏi focus rotary và chuyển focus an
  toàn nếu vùng đang được focus.
- Không nest hoặc đặt các FocusArea đè lên nhau.

For responsive layouts, size both `FocusArea` and `FocusItem` with normal Compose `Modifier`s.
`FocusItemLayout` still supplies minimum/explicit dimensions and direct-child cross-axis behavior.
For a nested `Row`/`Column`, set `fillCrossAxis = false` and apply `Modifier.weight()` at the call
site because weight belongs to the immediate Compose parent scope. The legacy
`FocusItemLayout.weight` property is deprecated and ignored by the nested-layout implementation.
Always use stable IDs.

Với layout lồng nhau, dùng `Modifier` để đặt kích thước; đặt `fillCrossAxis = false` nếu parent gần
nhất không cùng hướng với `FocusArea`. `Modifier.weight()` phải được áp dụng tại scope `Row` hoặc
`Column` thực tế.

## Touch mode / Chế độ chạm

Every composition below an activity or dialog rotary host can observe Android View touch mode:

```kotlin
val isInTouchMode: Boolean = LocalIsInTouchMode.current

if (!isInTouchMode && state.isFocused) {
    RotaryFocusRing()
}
```

`true` means Android is in touch mode. `false` means non-touch navigation mode (rotary, d-pad, or
keyboard); it does not claim that Compose focus owns anything. The host listens to
`ViewTreeObserver.OnTouchModeChangeListener` separately for every window.

Giá trị này dùng để ẩn/hiện focus ring hoặc thay đổi hướng dẫn UI từ bất kỳ composable nào. Nó chỉ
phản ánh touch mode của Android View và không biến Compose focus thành source of truth.

## Scrolling and lazy layouts / Cuộn và danh sách lazy

By default, a real View focus gain triggers both:

- `View.requestRectangleOnScreen()` for Android View scroll ancestors;
- Compose `BringIntoViewRequester` for `verticalScroll`, `horizontalScroll`, and already-composed
  lazy items.

While a lazy item owns real View focus, its `PinnableContainer` handle is retained. This prevents
the focused Android View from being disposed while the bridge scrolls toward the next logical item.

Disable this per item with `bringIntoView = FocusItemBringIntoViewBehavior.Disabled` when the app
owns scrolling.

A `LazyColumn` only composes items needed for its viewport. Therefore an offscreen item may not yet
have a `FocusItemView` or accessibility node and cannot receive View focus immediately. Supply the
full logical order and a lazy reveal handler:

```kotlin
val ids = remember(data) { data.map { FocusItemId(it.id) } }
val listState = rememberLazyListState()
val revealHandler = rememberLazyListFocusHandler(
    state = listState,
    itemIds = ids,
    scrollBehavior = LazyFocusScrollBehavior.Animated,
)

FocusArea(
    id = FocusAreaId("results"),
    firstFocusAt = ids.firstOrNull(),
    focusOrder = ids,
    onFocusItemUnavailable = revealHandler,
) {
    LazyColumn(state = listState) {
        items(data, key = { it.id }) { model ->
            FocusItem(id = FocusItemId(model.id)) { state -> ResultRow(model, state) }
        }
    }
}
```

The bridge performs `animateScrollToItem(index)` by default, waits for the `AndroidView` to register
and receive a non-zero layout size, then completes the queued real-View `requestFocus`. Use
`LazyFocusScrollBehavior.Immediate` for non-visual restoration. Keep identical stable order/keys
and `firstItemIndex` when the lazy list has leading headers. If no handler is supplied, only
currently composed lazy items are guaranteed to participate.

For separators, ads, section headers, or other non-focusable rows mixed between items, pass an
absolute index map instead of assuming contiguous indices:

```kotlin
val lazyIndexByFocusId: Map<FocusItemId, Int> = buildMap {
    data.forEachIndexed { lazyIndex, row ->
        if (row is FocusableRow) put(FocusItemId(row.id), lazyIndex)
    }
}
val revealHandler = rememberLazyListFocusHandler(
    state = listState,
    itemIndexById = lazyIndexByFocusId,
    scrollBehavior = LazyFocusScrollBehavior.Animated,
)
```

Disabled offscreen items are materialized only long enough to confirm their state, then traversal
continues to the next logical item; known invalid/stale indices are rejected instead of leaving a
pending focus request permanently blocked. If an accepted lazy scroll later fails because the data
set changed, the bridge clears that request and continues to the logical next item or restore
fallback.

`LazyColumn` chỉ compose item trong/gần viewport, nên item ở xa chưa có View thật. Bridge lazy mặc
định dùng `animateScrollToItem` để materialize item có animation, sau đó đợi View đăng ký/layout rồi
mới request focus. Dùng `LazyFocusScrollBehavior.Immediate` nếu cần khôi phục trạng thái tức thời.

## Direct manipulation mode / Chế độ điều khiển trực tiếp

Advanced direct manipulation sends the same accessibility event protocol used by Car UI Library.
Center enters DM, rotary/nudge events are delivered to callbacks, and Back exits DM. DM also exits
when the View loses focus, the window loses focus, the Activity becomes invisible, or the View is
detached, preventing a stuck RotaryService state.

```kotlin
var volume by remember { mutableFloatStateOf(0.5f) }
val rotaryStep = 0.05f

val volumeDirectMode = remember {
    DirectManipulationConfig(
        onRotary = { event ->
            // `detents` is signed: every rotary click changes the Slider by one step.
            volume = (volume + event.detents * rotaryStep).coerceIn(0f, 1f)
        },
        onNudge = { event ->
            // Optional: pan, change axis, or handle tilt while DM is active.
        },
        onModeChanged = { enabled -> analytics.dmChanged(enabled) },
    )
}

FocusArea(id = FocusAreaId("controls")) {
    FocusItem(
        id = FocusItemId("volume"),
        directManipulation = volumeDirectMode,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics = FocusItemSemantics(
            label = "Volume",
            stateDescription = "${(volume * 100).toInt()} percent",
            role = FocusItemRole.Adjustable,
        ),
    ) { state ->
        Column {
            Text("Volume ${(volume * 100).toInt()}%")
            Slider(
                value = volume,
                onValueChange = { volume = it },
            )
        }
    }
}
```

Press Center while the `FocusItem` is focused to enter Direct Mode. Rotate to invoke `onRotary`,
which updates `volume` and recomposes the `Slider`; Back exits Direct Mode. Trong Direct Mode, xoay
núm gọi `onRotary` để đổi giá trị `volume`, sau đó `Slider` tự vẽ lại; nhấn Back để thoát.

For app-controlled Direct Mode, AAOS normally injects `MotionEvent.ACTION_SCROLL` into the focused
window. `RotaryFocusHostView` routes that event to the currently focused real `FocusItemView`, so it
is not lost inside the Compose/AndroidView bridge. The item also exposes and handles accessibility
`ACTION_SCROLL_FORWARD`/`ACTION_SCROLL_BACKWARD` as an OEM compatibility fallback.

Với Direct Mode do ứng dụng điều khiển, module bắt rotary ngay tại host rồi chuyển tới
`FocusItemView` thật đang focus. Module cũng hỗ trợ hai accessibility scroll action để tương thích
với các bản RotaryService/OEM dùng cơ chế dispatch khác.

When `enterOnCenter = false`, an app can explicitly enter/exit DM after focusing the item:

```kotlin
controller.setDirectManipulationMode(
    RotaryFocusTarget(FocusAreaId("controls"), FocusItemId("volume")),
    enabled = true,
)
```

Compose content should render the focus and DM visual states passed by the wrapper. For predictable
touch behavior, the wrapper owns touch/click by default. Use
`FocusItemTouchBehavior.ComposeContent` only when nested Compose content must process touch; rotary
focus still belongs to the View wrapper.

## Navigation and dialogs / Chuyển màn hình và dialog

Save and restore a destination's real View focus:

```kotlin
RotaryFocusDestination(
    destinationKey = "vehicle-settings",
    fallback = RotaryFocusTarget(
        FocusAreaId("settings-categories"),
        FocusItemId("display"),
    ),
) {
    VehicleSettingsScreen()
}
```

With Navigation Compose, wrap every route in a stable destination key. The controller records the
last focused real View when focus changes, so returning with a focusable Back button or the system
Back key restores that exact item rather than capturing whichever screen happens to dispose last:

```kotlin
NavHost(navController, startDestination = "home") {
    composable("home") {
        RotaryFocusDestination(
            destinationKey = "home",
            fallback = RotaryFocusTarget(homeArea, openListButton),
        ) {
            HomeScreen(onNext = { navController.navigate("list") })
        }
    }
    composable("list") {
        RotaryFocusDestination(
            destinationKey = "list",
            fallback = RotaryFocusTarget(listArea, firstItem),
        ) {
            ListScreen(onBack = navController::popBackStack)
        }
    }
}
```

Khi pop màn hình bằng button hoặc Back key trong non-touch mode, target cuối của destination trước
được request lại. Trong touch mode thư viện không ép focus ring xuất hiện.

Use `RotaryFocusDialog`, not a standard Compose `Dialog`, for rotary dialogs. It creates a separate
Android window with its own first-child `FocusParkingView`, supports the RotaryService popup-dismiss
action, exits DM safely, and restores the exact parent item on dismissal. Its area/destination
CompositionLocals are isolated from the parent route so dialog focus cannot overwrite navigation
focus history.

```kotlin
if (showDialog) {
    RotaryFocusDialog(
        dialogKey = "audio-balance",
        initialFocus = RotaryFocusTarget(
            FocusAreaId("audio-dialog"),
            FocusItemId("confirm"),
        ),
        onDismissRequest = { showDialog = false },
    ) {
        AudioBalanceDialogContent()
    }
}
```

Dùng `RotaryFocusDestination` để lưu/khôi phục focus khi đổi composable screen. Dùng
`RotaryFocusDialog` để mỗi dialog window có `FocusParkingView` riêng và trả focus đúng item của
window cha khi đóng.

`initialFocus` is requested only when the parent opened the dialog in non-touch mode. Dialog close
is one-shot: Center/Back restores the opener; a touch dismissal does not force the app back into
focus mode.

## Run the focus preview / Chạy màn hình mẫu

The debug catalog now uses the package
`com.b231001.bmaterial.ccp.rotaryfocus.preview` inside this module. `RotaryFocusDemo` contains:

1. Screen 1: nested/extracted `FocusItem`, Navigation Compose, dialog, and Slider Direct Mode.
2. Screen 2: physical order `1, 2, 3`, rotary order `1, 3, 2, 1`, auto-scroll, Back and dialog.
3. Screen 3: 40-item `LazyColumn` using `rememberLazyListFocusHandler`.

Build/install `:app:assembleDebug`, then test rotate, Center, Back and nudge on the target AAOS
device. Màn hình sample hiển thị trực tiếp `TOUCH MODE` hoặc `ROTARY / FOCUS MODE` từ
`LocalIsInTouchMode`.

## Focus permission example / Ví dụ khóa một vùng

```kotlin
FocusArea(
    id = FocusAreaId("passenger-controls"),
    isFocusAllowed = passengerControlsAvailable,
) {
    // Content remains visible in both states.
    FocusItem(id = FocusItemId("temperature"), onClick = ::adjustTemperature) { state ->
        TemperatureVisual(enabled = state.isEnabled, focused = state.isFocused)
    }
}
```

An area should contain at least one focusable item when enabled. The library intentionally does not
invent advanced behavior for an empty focus area.

## Logging and hardware test / Log và kiểm thử trên xe

Logging is enabled by default with tag `BMaterialRotary`:

```bash
adb logcat -s BMaterialRotary:V
```

Logs include:

- View attach/detach and generated View IDs;
- every global and item View focus transition (`SOURCE_OF_TRUTH View focus`);
- area entry, `firstFocusAt`, focus permission, and safe focus movement;
- local wrapping, linked-area boundary resolution, held boundaries, and rejected link cycles;
- key code/action/repeat count for Center, Back, DPAD, and system-navigation keys;
- generic motion source, axis, detents, and event time;
- Direct Mode transport (`host`, `focused-view`, or `accessibility-action`) and callback delivery;
- DM enter/exit reason and accessibility-event result;
- screen/dialog save, park, and restore operations;
- host touch-mode changes and destination-scoped restoration;
- bring-into-view requests plus lazy item materialization start/completion;
- nested/duplicate/overlapping area validation.

Dump the currently registered hierarchy when capturing an issue:

```kotlin
Log.d("RotaryDump", controller.dumpHierarchy())
```

AAOS test commands documented by AOSP:

```bash
# Counter-clockwise / clockwise
adb shell cmd car_service inject-rotary
adb shell cmd car_service inject-rotary -c true

# Nudge left / right / up / down
adb shell cmd car_service inject-key 282
adb shell cmd car_service inject-key 283
adb shell cmd car_service inject-key 280
adb shell cmd car_service inject-key 281

# Center and Back
adb shell cmd car_service inject-key 23
adb shell input keyevent 4
```

For this development phase, reproduce on the actual automotive device and provide:

1. device/OEM and Android version;
2. expected and actual focus item;
3. action sequence (rotate/tilt/Center/Back/touch/dialog/navigation);
4. `BMaterialRotary` log plus `controller.dumpHierarchy()`;
5. whether DM was active.

After hardware validation, production apps can disable verbose library logging:

```kotlin
RotaryFocusLogger.enabled = false
```

References / Tài liệu chuẩn:

- [AOSP rotary controller behavior](https://source.android.com/docs/automotive/hmi/rotary_controller)
- [AOSP app developer guide](https://source.android.com/docs/automotive/hmi/rotary_controller/app_developers)
- [Apps without Car UI Library](https://source.android.com/docs/automotive/hmi/rotary_controller/app_developers_no_carui)
- [AOSP FocusArea source](https://android.googlesource.com/platform/packages/apps/Car/libs/+/refs/heads/main/car-ui-lib/car-rotary-lib/src/main/java/com/android/car/ui/FocusArea.java)
- [Compose lazy lists](https://developer.android.com/develop/ui/compose/lists)
- [Compose bringIntoViewRequester](https://developer.android.com/develop/ui/compose/modifiers-list)
- [Android View touch-mode listener](https://developer.android.com/reference/android/view/ViewTreeObserver.OnTouchModeChangeListener)

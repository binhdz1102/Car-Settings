# Audit và nâng cấp VehicleSettings UI

## Phạm vi và kiến trúc

Đợt audit ngày 2026-08-23 tập trung vào shell ứng dụng, năm feature Vehicle và
đường tích hợp VHAL. Cây nguồn AOSP tham chiếu `Settings/` không tham gia Gradle
graph và được bảo vệ bởi rule `/Settings/` trong `.gitignore`.

Kiến trúc hiện tại giữ một Gradle module cho mỗi feature. Bên trong mỗi module,
code được phân lớp bằng ba package `presentation`, `domain` và `data`. Shell
`app` điều phối navigation/category; `core:settings-api` giữ registry và route;
`core:ui` cung cấp renderer/model dùng chung; `core:vehicle` cô lập API VHAL.
Dependency flow của nhóm Vehicle là:

`app -> feature -> core:ui/core:vehicle -> Android Car API`

Không còn project con kiểu `:feature:<name>:presentation`, `:data` hoặc
`:domain`. Việc nâng cấp UI không thay đổi property ID, permission, route,
deep-link hay semantics đọc/ghi VHAL.

## Kết quả audit trước thay đổi

- Vehicle đã có đủ năm feature nhưng category nằm gần cuối rail và cold start
  mở Connected devices.
- `app/src/main/res/drawable/car.png` đã tồn tại nhưng launcher vẫn resolve tới
  icon Android mặc định.
- Preview dùng ảnh guide bị crop và giảm alpha xuống 16–22%, sau đó đặt overlay
  tổng quát lên một hệ tọa độ khác. Kết quả là ảnh bị mờ và chuyển động không bám
  đúng chi tiết xe; Climate chưa có visualization riêng.
- Tất cả numeric control dùng cùng một slider, không phân biệt nhiệt độ, level,
  position hay offset có tâm.
- Switch/enum/status dùng baseline sát mép section hơn slider; Mirror lock và
  Fold mirror là hai trường hợp dễ thấy nhất.

## Thay đổi đã triển khai

### Icon, thứ tự và navigation

- Adaptive icon thường, round và monochrome dùng `@drawable/car` qua foreground
  có inset 16% và nền xanh nhạt. Manifest vẫn giữ contract
  `@mipmap/ic_launcher`/`@mipmap/ic_launcher_round`.
- Vehicle đứng đầu category rail và là destination mặc định của normal launch,
  HOME và fallback. Các intent Android Settings cụ thể vẫn dùng mapping cũ.
- Danh sách card Vehicle được cấu hình tập trung theo thứ tự cố định: Climate,
  Driver Assistance, Seats and steering wheel, Doors/windows/mirrors, Vehicle
  lighting.

### Preview và animation

Năm asset PNG `drawable-nodpi` dùng cùng phong cách technical graphite/cyan,
viewport 16:9, nền alpha thật, không có chữ/logo/mũi tên hay blur baked-in:

| Feature | Asset |
|---|---|
| Climate | `vehicle_preview_climate.png` |
| Driver Assistance | `vehicle_preview_driver_assistance.png` |
| Seats and steering wheel | `vehicle_preview_seat_steering.png` |
| Doors/windows/mirrors | `vehicle_preview_doors.png` |
| Vehicle lighting | `vehicle_preview_lighting.png` |

Preview decode bằng ARGB, hiển thị với `ContentScale.Fit` và dùng anchor chuẩn
hóa 0..1. Giá trị quan sát được clamp trước khi nội suy; motion vị trí dùng
360–420 ms, ánh sáng/nhiệt dùng 240 ms. ADAS chỉ chạy one-shot emphasis khi
control đổi và luôn được mô tả là illustration. Command cửa/kính không tạo trạng
thái giả: overlay chỉ dịch chuyển khi position/state phản hồi từ VHAL. Toàn bộ
90 ảnh guide cũ vẫn được giữ cho dialog “View illustrated guide”.

### Slider và spacing

- `VehicleSliderUiKind` gồm `THERMAL`, `LEVEL`, `POSITION`, `OFFSET`;
  `VehicleSliderUiSpec` chứa endpoint label, tick/value-label policy và center
  marker. Metadata/model có default để không phá caller cũ.
- BSlider 1.3.1 được style theo semantics: thermal chuyển xanh–cam; level có
  discrete ticks; position có nhãn hướng và marker 0 khi phù hợp; offset nhấn
  mạnh tâm 0 và giữ dấu âm/dương.
- Clamp, step, float/int conversion, rotary coalescing và VHAL write behavior
  được giữ nguyên.
- Mọi focus surface Vehicle có inset ngoài 8 dp và content baseline tổng cộng
  24 dp. Divider cũng inset 24 dp, áp dụng đồng nhất cho switch, slider, enum,
  status và info button.

## Nghiệm thu

### Build và kiểm thử tự động

| Check | Kết quả |
|---|---|
| Targeted unit tests cho `core:ui` và năm feature Vehicle | PASS |
| `testDebugUnitTest` | PASS |
| `:app:lintDebug` | PASS, 0 errors; còn 104 warnings không chặn build |
| `:app:assembleDebug` | PASS |
| `git diff --check` | PASS |

Unit tests mới kiểm tra thứ tự/default route, đủ năm card, slider classification,
tick/center marker, normalized geometry và animation bounds. Các test hiện hữu
cho unsupported, read-only và UX restriction tiếp tục pass.

### Emulator `emulator-5554`

- APK debug được cài bằng `adb install -r -d -g`: PASS.
- Cold start mở Vehicle landing và Vehicle đứng đầu rail: PASS.
- Adaptive launcher icon hiển thị silhouette xe trên taskbar/resolver: PASS.
- Cả năm detail pane được kiểm tra ở 1920×1080: asset sắc nét, không crop/blur,
  overlay nằm trong bounds và spacing row đồng nhất.
- Rotary focus/direct manipulation hoạt động; Climate temperature đổi
  `21.0 -> 21.5 -> 21.0` và được phục hồi.
- UX restriction được bật ở driving state rồi phục hồi về park; app không crash
  và policy disable vẫn giữ nguyên.
- `android.settings.HVAC_SETTINGS` mở Climate. `DISPLAY_SETTINGS` vẫn mở Display
  sau resolver AAOS do emulator còn cài một handler Settings khác.
- Logcat sau smoke test không có `FATAL EXCEPTION` hoặc ANR của
  `com.android.car.settings`.

| Feature | Control smoke-tested | Chuyển đổi và phục hồi |
|---|---|---|
| Climate | HVAC power / temperature | `true -> false -> true`; `21.0 -> 21.5 -> 21.0` |
| Driver Assistance | Lane departure warning | `true -> false -> true` |
| Seats and steering wheel | Seat fore/aft | `-10 -> -1 -> -10` |
| Doors/windows/mirrors | Mirror fold | `true -> false -> true` |
| Vehicle lighting | Headlight mode | `AUTOMATIC -> DAYTIME_RUNNING -> AUTOMATIC` |

Khi HVAC power được tắt, emulator tạm báo property unavailable nên giá trị được
phục hồi bằng lệnh diagnostic CarService; đây không phải crash và giá trị cuối đã
được xác nhận là `true`.

Ảnh nghiệm thu cục bộ nằm trong `captures/vehicle-ui-current/`; toàn bộ
`captures/` bị ignore và không thuộc nội dung commit.

# Báo cáo migration Car-Settings ← My-System-App

- **Ngày hoàn tất:** 2026-08-23
- **Nhánh:** `migrate-from-my-system-app`
- **Môi trường kiểm thử:** AVD `Automotive_Device_FullHD` (image `sdk_car_mysystemapp_x86_64`, Android 17 / SDK 37), emulator `emulator-5554`, app cài dạng data-partition update ký bằng platform key (AOSP dev key).
- **Nguồn tham chiếu:** bản sao `My-System-App/` trong repo (bỏ qua khỏi git).

## 1. Phạm vi

Đưa Car-Settings trở thành ứng dụng VehicleSettings/Car-Settings hoàn chỉnh với toàn bộ logic
Settings của My-System-App:

- **19 feature module** (12 module cũ nâng lên revision mới + 7 module mới).
- **4 module core mới**: `core:vehicle` (lớp VHAL dùng chung), `core:settings-api`
  (SettingsRegistry định tuyến/UX class), vehicle UI components + B-Material design stack
  trong `core:ui`, và `third_party:bmaterial-ccp-rotary-focus`.
- **Không port theo thiết kế** (README gốc định nghĩa là app riêng): `feature:launcher`,
  `feature:systemuicockpit`, `feature:systemuinotification`, `feature:systemuicatalog`,
  SystemUiPanelActivity, RRO overlay, VHAL config/Soong của image. Các destination này
  trong search được đánh dấu "Not included in the Car-Settings build".

## 2. Tình trạng port từng feature

| Feature | Trạng thái | Ghi chú |
|---|---|---|
| wifi | ✅ Thay bằng bản MSA | Thêm Network & internet hub, Mobile network (telephony/SubscriptionManager), Wi-Fi QR join/share (zxing), persistent tethering |
| bluetooth | ✅ Thay bằng bản MSA | Thêm BluetoothHiddenApiBridge, UWB, ProfileProxyLeaseManager; bỏ reflection inline |
| sound | ✅ Thay bằng bản MSA | Thêm volume-group mute + CarVolumeGroupEventCallback |
| display | ✅ Thay bằng bản MSA | Theme persistence fallback, idempotent timezone |
| applications | ✅ Thay bằng bản MSA | Thêm 6 màn hình: permissions hub, permission group, per-app permissions, unused apps, default apps, opening links (+13 bridge methods) |
| profileaccounts | ✅ Thay bằng bản MSA | Thêm logout current user |
| system | ✅ Thay bằng bản MSA | Full set (language/keyboard/autofill/TTS/units/legal/hardware); đã cắt hàng "System UI" (không port catalog) |
| notifications / privacy / security | ✅ Thay bằng bản MSA | Parity đầy đủ |
| search | ✅ Thay bằng bản MSA | Registry-driven (DefaultSettingsRegistry), gating theo UX restriction + capability; tách khỏi systemuicatalog/cockpit |
| hvac | ✅ Thay bằng bản MSA | Chuyển sang lớp `core:vehicle` dùng chung |
| **accessibility** | 🆕 Port mới | Screen reader, captions, accessibility services |
| **location** | 🆕 Port mới | Master toggle, ADAS location, per-app permission, recent access (+bridge) |
| **assistantvoice** | 🆕 Port mới | Default assistant, voice input, recognition service (+bridge) |
| **doorcontrol** | 🆕 Port mới | Cửa/kính/gương qua VHAL (vendor props) |
| **seatcontrol** | 🆕 Port mới | Ghế/vô lăng: position, lumbar, memory |
| **vehiclelighting** | 🆕 Port mới | Đèn ngoài/trong, hazard |
| **driverassistance** | 🆕 Port mới | ADAS: AEB, FCW, lane keep, cruise... |

App module: thêm `VehicleLandingScreen` + card "Vehicle" trên HomeScreen, 3 card mới
(Accessibility, Location, Assistant & voice), điều hướng qua `DefaultSettingsRegistry`,
`CarSettingsSearchIndexablesProvider` (Settings Intelligence), 24 permission mới
(vehicle/role/UWB/network history) + alias `AccessibilitySettingsActivity`.

## 3. Package naming

- Toàn bộ code port sang `com.android.car.settings.*`; `applicationId` giữ
  `com.android.car.settings` (thay thế AAOS Settings, `sharedUserId=android.uid.system`).
- Thư mục nguồn đã dời hết về `com/android/car/settings/**` (Phase naming refactor).
- Tất cả tham chiếu `com.android.mysystemapp` trong code build đều được đổi tên
  (bản copy `My-System-App/` nằm ngoài git, chỉ làm tham chiếu).

## 4. Timber logging

- Plant sẵn `AppTimberTree`; lần này bổ sung: (1) mọi `ActionResult` failure log tập trung
  tại `toActionFailure()`, (2) Wi-Fi/Bluetooth platform log lỗi khi convert, (3) router log
  quyết định định tuyến theo component, (4) MainActivity log destination khởi động,
  (5) release build tự tắt log DEBUG/VERBOSE (`isLoggable`).

## 5. Git history cleanup

- `git filter-repo` đã xóa `keystore.properties` + `keystore/platform.p12` khỏi **toàn bộ
  12 commit, mọi nhánh**; force-push tất cả nhánh lên `github.com/binhdz1102/Car-Settings`.
- File keystore được khôi phục về working tree (untracked) để ký debug; thêm
  `keystore.properties.example`; `.gitignore` bổ sung `/My-System-App/` và `.zcode/`.
- Lưu ý: GitHub có thể còn cache object cũ cho đến khi GC; key này là AOSP dev key công khai
  nên rủi ro chỉ mang tính vệ sinh.

## 6. Kết quả kiểm thử

### 6.1 Build & unit test (host)
- `testDebugUnitTest`: **172 tests, 0 failed** (58 class test qua 20 module).
- `:app:assembleDebug`, `:app:lintDebug`: PASS.

### 6.2 Deep-link matrix trên AVD — **53/53 PASS**
- 31 intent action `android.settings.*` (SETTINGS, WIFI, BLUETOOTH, SOUND, DISPLAY, DATE,
  TIMEZONE, APPLICATION, ACCESSIBILITY, LOCATION_SOURCE, VOICE_INPUT, SECURITY,
  DEVICE_ADMIN, NOTIFICATION, PRIVACY, SYNC, DEVICE_INFO, STORAGE, RESET, SYSTEM_UPDATE,
  INPUT_METHOD, MEASUREMENT_SYSTEM, LEGAL, HVAC, NETWORK_OPERATOR, WIFI_TETHER, WIFI_IP,
  MANAGE_ALL_APPLICATIONS, MANAGE_WRITE, USAGE_ACCESS, MANAGE_OVERLAY).
- 22 legacy alias `CarSettingActivities$*` (Homepage, Display, Wifi, WifiTether, Bluetooth,
  Sound, Datetime, Location, Accessibility, AssistantAndVoice, MobileNetwork, Apps, System,
  Privacy, Notifications, Security, Storage, About, ResetOptions, Units, LegalInformation,
  SpecialAccess).
- Mỗi case: cold start, xác nhận process sống + màn hình đúng nội dung qua uiautomator.

### 6.3 Kiểm thử chức năng trên AVD
| Kiểm tra | Kết quả |
|---|---|
| Wi-Fi state propagation (svc wifi disable/enable ↔ màn hình Disabled/Enabled) | ✅ |
| Wi-Fi write-path (bật/tắt toggle trong app ↔ `dumpsys wifi`) | ✅ |
| Bluetooth state propagation (cmd bluetooth_manager ↔ On/Off) | ✅ |
| DND row hiển thị trên Sound screen | ✅ |
| Vehicle landing + ADAS + Lighting render | ✅ |
| HVAC hiển thị dữ liệu VHAL sống (nhiệt độ set 18.5°, cabin 19.1°, fan 3, 5 zone) | ✅ |
| In-app search: gõ "wifi" trả về Wi-Fi/Network & internet/Saved networks | ✅ |
| SearchIndexablesProvider: đăng ký đúng, chặn truy cập thiếu permission (chuẩn AAOS) | ✅ |
| So sánh với app mặc định: `android.settings.*` implicit intent resolve về app khi gọi trực tiếp; app tham chiếu MySystemApp vẫn chạy bình thường | ✅ |

### 6.4 Lỗi phát hiện & đã sửa trong quá trình test
1. **Crash `ClassNotFoundException: ImmutableMap`** — code sinh bởi Hilt tham chiếu Guava
   runtime → thêm `guava-android` vào app.
2. **Crash `B-Material tokens are missing`** — theme cũ không bọc `BTheme` → thay
   `MySystemTheme` bằng bản MSA (BTheme + automotive preset + typography scale).
3. **MANAGE_OVERLAY/WRITE_SETTINGS rơi về Home** — image này định nghĩa lại constant với
   infix `.action.` khác chuẩn AOSP → router chấp nhận cả hai spelling (+unit test).
4. Trong quá trình test, system_server từng restart (môi trường bẩn) → emulator được khởi
   động lại để lấy môi trường sạch trước khi chạy matrix cuối.

## 7. Hạn chế đã biết

- **QC provider**: SystemUI vẫn truy vấn `com.android.car.settings.qc` (Quick Controls của
  Settings gốc) — hiện chưa port; log `Failed to find provider info` là vô hại.
- **Implicit intent với 2 app Settings cùng cài**: khi Car-Settings và MySystemApp cùng
  tồn tại, intent `android.settings.*` không chỉ định component sẽ hiện chooser — hành vi
  đúng của Android; test dùng explicit component để xác định.
- **Localization**: UI strings vẫn hardcode tiếng Anh (kế thừa từ trước); values-vi chỉ có
  ở core/ui components.
- **Hai spelling action** đã xử lý cho 2 action ảnh hưởng; các action hiếm khác của image
  custom (nếu có) cần kiểm tra thêm khi gặp.
- Room/benchmark/baselineprofile/Soong của MSA không port (không cần cho app thay thế).

## 9. Bổ sung: UI parity với My-System-App (shell + CCP)

Sau báo cáo ban đầu, phần shell của app đã được đưa về trùng khớp 100% với
My-System-App:

- `MainActivity` cài đặt `setSafeRotaryContent` (host CCP "main-window" +
  `RotaryFocusController`, back handling cho Direct Manipulation +
  `VehicleDialogBackGuard`), render `SettingsAppShell` hai pane (rail 340dp +
  detail) với 14 category từ `DefaultSettingsRegistry`, theme follow
  `DisplayRepository.ThemeMode`, gating UX restriction bằng `VehicleUxPolicy`
  (+dialog `ux_restricted`), và permission request theo capability từng
  destination. Card-grid HomeScreen cũ đã nghỉ hưu (route `home` chỉ còn là
  deep link tương thích, redirect về Connected devices) — đúng hành vi MSA.
- `VehicleLandingScreen` dùng string resources; app res có đủ EN + VI (74
  strings/ngôn ngữ) port từ MSA.
- Deep-link matrix đổi sang verify bằng Timber route logs (shell dùng native
  CCP views nên uiautomator không thấy text): **53/53 PASS**.
- So sánh screenshot với MSA trên cùng emulator: shell rail + detail pane
  hiển thị giống hệt (đã đối chiếu từng khu vực).
- Thiết kế chi tiết (tokens, components, focus/CCP contract, ràng buộc
  automotive) được ghi lại tại `DESIGN.md` (port từ DESIGN.md của MSA, điều
  chỉnh khác biệt về destination count và router contract).

## 9. Danh sách commit (nhánh migrate-from-my-system-app)

```
f287000 chore: ignore My-System-App reference copy and add keystore template
5a8f10b feat(core): port core:vehicle VHAL layer from My-System-App
3b8209c feat(core): port vehicle UI components and B-Material design stack
dd6e13a feat: port accessibility, location and assistant/voice settings modules
6046bd9 feat: port vehicle control features and bring all settings modules to My-System-App parity
93efee7 feat(app): add AAOS Settings Intelligence search index provider
529dc13 refactor(core): move remaining ui sources to com/android/car/settings layout
04b5224 chore(logging): add Timber coverage at failure chokepoints
31640c4 chore: add missing consumer-rules.pro placeholders
6a9ae5d fix: runtime crash fixes discovered during AVD verification
87efb36 fix(router): accept custom-image spellings of special access actions
```

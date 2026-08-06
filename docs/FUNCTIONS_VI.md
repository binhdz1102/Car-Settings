# Chức năng của Car-Settings

Tài liệu này mô tả các chức năng hiện đang có trong APK thay thế được viết
bằng Jetpack Compose.

## Các khu vực Settings

- Trang Home: Display, Wi-Fi, Bluetooth, Sound & vibration, Climate, Apps,
  Notifications, Privacy, Security, Profiles & accounts, System và Search.
- Wi-Fi: trạng thái radio, mạng khả dụng, mạng đã lưu/đang kết nối, thêm mạng,
  cấu hình hotspot và Wi-Fi preferences.
- Bluetooth: trạng thái radio, thiết bị đã ghép đôi, discoverability, quét thiết
  bị và luồng pairing.
- Sound: các nhóm âm lượng, rung/Do Not Disturb và chọn ringtone điện thoại,
  notification, alarm.
- Display: brightness theo gamma, adaptive brightness, chế độ ngày/đêm,
  date/time và time zone.
- Applications: ứng dụng gần đây, toàn bộ ứng dụng, chi tiết ứng dụng,
  storage/cache, tóm tắt permission/notification, special access và ứng dụng
  ảnh hưởng hiệu năng xe.
- Profiles & accounts: profile hiện tại, danh sách profile, account provider,
  master sync và chi tiết đồng bộ account.
- Notifications: trạng thái notification theo ứng dụng và các điểm vào
  notification access.
- Privacy: điều khiển microphone, camera, location của xe và lịch sử truy cập
  gần đây.
- Security: lựa chọn screen lock, credential storage và device admin đang hoạt
  động.
- Search: tìm kiếm nội bộ Settings bằng Flow có debounce.
- System: language/input, vehicle units, date/time, storage, thông tin thiết
  bị, legal information và reset options.
- Climate: zone HVAC, climate power, automatic climate, đơn vị nhiệt độ,
  nhiệt độ và quạt thông qua car service/VHAL của AAOS.

## Tương thích contract

`SettingsIntentRouter` xử lý các Android/AAOS Settings action thông dụng và các
component AOSP cũ dưới `com.android.car.settings.common.CarSettingActivities`.
Intent chi tiết ứng dụng có package data và extra ringtone type được bảo toàn.

Toàn bộ view mới dùng Compose. Cấu trúc ngữ nghĩa và nhãn bám theo mô hình
Settings của AOSP/My-System-App; tuy nhiên đây là bản triển khai lại bằng
Compose, chưa được chứng nhận giống từng pixel với mọi XML preference resource
nguyên bản.

## Chức năng không áp dụng

CarSettings không có triển khai TaskView trong source AOSP đã kiểm tra hoặc
trong My-System-App. TaskView thuộc bề mặt Launcher/task-host, vì vậy không có
case TaskView cần kiểm thử trong APK này.

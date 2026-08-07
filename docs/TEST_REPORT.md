# Car-Settings test report

## Latest post-refactor test session

- Date: 2026-08-07 (Asia/Bangkok)
- Target: `emulator-5554`, `sdk_car_mysystemapp_x86_64`, Android 17/Baklava
- User: Android user 10
- Service: `mysystemapp-avd-20260802.service`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Package: `com.android.car.settings`

This session was run after merging each feature's `presentation`, `data` and
`domain` Gradle modules into one feature module. The package-level separation and
the AAOS runtime contract were kept unchanged.

The AVD was started with `-wipe-data -no-snapshot`. Boot completed with
`sys.boot_completed=1`. A first-boot CarService notice from
`com.google.android.car.kitchensink` was displayed and dismissed; it was not a
Car-Settings crash. The new APK was then installed with
`adb install -r -d -g` and returned `Success`.

## Build and automated tests

| Check | Result |
|---|---|
| Gradle project graph | PASS; 12 feature modules, no `:feature:*:data/domain/presentation` projects |
| `./gradlew testDebugUnitTest --console=plain --max-workers=2` | PASS; all unit tests passed |
| `./gradlew :app:assembleDebug --console=plain --max-workers=2` | PASS; debug APK produced |
| `./gradlew :app:lintDebug --console=plain --max-workers=2` | PASS; no lint errors (dependency-update/resource warnings remain) |
| APK installation on the wiped AVD | PASS |
| Launch via `.Settings_Launcher_Homepage` | PASS |
| Timber lifecycle/deep-link logging | PASS; `MainActivity` records creation, new intents and routing |

## Deep-link smoke tests

Every row below resolved to `com.android.car.settings/.MainActivity` (the
legacy explicit display alias also resolved to the replacement target) and
rendered the listed screen content without an application fatal exception.
The 36-entry action loop was rerun after installation of the refactored APK;
all 36 routes kept the app process alive and the follow-up UI inspection
confirmed the expected screen for each route.

| Intent/action | Screen verified |
|---|---|
| `android.settings.SETTINGS` | Home |
| `android.settings.DISPLAY_SETTINGS` | Display |
| `android.settings.WIFI_SETTINGS` | Wi-Fi |
| `android.settings.panel.action.INTERNET_CONNECTIVITY` / `WIRELESS_SETTINGS` | Wi-Fi |
| `android.settings.WIFI_ADD_NETWORKS` | Wi-Fi add-network entry |
| `android.settings.WIFI_IP_SETTINGS` | Wi-Fi preferences |
| `com.android.settings.WIFI_TETHER_SETTINGS` | Wi-Fi hotspot |
| `android.settings.BLUETOOTH_SETTINGS` | Bluetooth |
| `android.settings.BLUETOOTH_PAIRING_SETTINGS` | Bluetooth pairing/scanning state |
| `android.settings.SOUND_SETTINGS` | Sound & vibration |
| `android.intent.action.RINGTONE_PICKER`, alarm type | Alarm ringtone list after selecting CarSettings |
| `android.settings.DATE_SETTINGS` | Date & time |
| `android.settings.TIMEZONE_SETTINGS` | Time zone list |
| `android.settings.APPLICATION_SETTINGS` | Applications |
| `android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS` | All apps |
| `android.settings.APPLICATION_DETAILS_SETTINGS` with `package:` data | Application details |
| `android.settings.USAGE_ACCESS_SETTINGS` | Special app access |
| `android.settings.NOTIFICATION_SETTINGS` | Notifications |
| `android.settings.VOICE_CONTROL_DO_NOT_DISTURB_MODE` | Notifications |
| `android.settings.PRIVACY_SETTINGS` | Privacy |
| `android.settings.LOCATION_SOURCE_SETTINGS` | Location |
| `android.settings.SECURITY_SETTINGS` | Security |
| `android.settings.DEVICE_ADMIN_SETTINGS` | Device admins |
| `android.settings.USER_SETTINGS` | Profiles |
| `android.settings.SYNC_SETTINGS` | Profiles & accounts |
| `android.settings.DEVICE_INFO_SETTINGS` | About/device information |
| `android.settings.INTERNAL_STORAGE_SETTINGS` | Internal storage |
| `android.settings.REGIONAL_PREFERENCES_SETTINGS` | Vehicle units |
| `android.settings.LOCALE_SETTINGS` | Languages & input |
| `android.settings.REQUEST_SET_AUTOFILL_SERVICE` | Languages & input / Autofill |
| `android.settings.SHOW_REGULATORY_INFO` | Legal information |
| `android.settings.RESET_SETTINGS` | Reset options |
| `android.settings.SYSTEM_UPDATE_SETTINGS` | System |
| `android.settings.HVAC_SETTINGS` | Climate/HVAC |
| explicit `CarSettingActivities$DisplaySettingsActivity` | Display compatibility alias |

`android.intent.action.RINGTONE_PICKER` also has the AVD
`com.android.soundpicker` handler. `android.settings.APP_SEARCH_SETTINGS` also
has `Settings Suggestions`. The clean AVD therefore showed the normal
`CarResolverActivity`; selecting `CarSettings` and choosing “Just once” opened
the replacement screen successfully. The source APK no longer registers
duplicate Settings actions from My-System-App, so the common Settings actions
resolve directly to Car-Settings.

## Interactive checks

- Wi-Fi: switch changed `global/wifi_on` from `1` to `0` and back to `1`; UI
  changed Enabled → Disabled → Enabled.
- Bluetooth: switch changed `global/bluetooth_on` from `1` to `0` and back to
  `1`; UI changed On → Off → On.
- Display: brightness slider changed the device value and UI percentage, then
  restored the original brightness setting (`102`).
- Bluetooth pairing: “Pair new device” entered the scanning state and returned
  without a crash.
- Applications: opened Maps application details and navigated to Storage &
  cache; no destructive clear operation was issued.
- Search: entered `wifi`, observed Wi-Fi, Saved networks and Wi-Fi preferences
  results, then opened the Wi-Fi screen.
- Climate: toggled driver climate power and automatic climate and restored both
  controls; Driver, passenger/rear zones, temperature units and VHAL-backed
  temperature controls were visible.
- Reset/factory reset: screen was opened, but destructive reset actions were
  intentionally not executed.

## Remaining limitations

1. The APK is installed as a data-partition update of the AOSP
   `com.android.car.settings` package. A wipe removes that update, so the APK
   must be installed again. It is not yet integrated into the AOSP product
   image as the replacement `/system/priv-app` artifact.
2. The port covers the My-System-App Settings feature set. AOSP areas without
   a corresponding My-System-App implementation remain incomplete, including
   mobile-network/APN/data-usage screens, accessibility/captions, several
   enterprise/admin flows, remote bugreport, app-widget approval and some
   profile/account management sub-flows.
3. Compose screens preserve the tested Settings semantics and labels but are
   not a pixel-perfect XML resource copy of every AOSP CarSettings preference
   screen. The project follows the requested Compose UI direction.
4. AOSP external Search Indexables provider/trampoline parity is not included;
   the in-app Settings search is implemented and tested.
5. Room is not used because Settings state is read from framework/car services;
   Room support is retained in build-logic for future local persistence.
   Platform callback APIs still require small `Handler`/`Executor` adapters,
   but state propagation and business operations use coroutines and Flow.
6. No TaskView code exists in this Settings surface, so TaskView testing is
   not applicable; it belongs to CarLauncher/task hosting.

The aggressive first smoke loop also hit Android's `UiAutomationService already
registered` message when multiple `uiautomator dump` processes were launched
back-to-back. That was a test-harness timing issue from the shell dumper, not a
`com.android.car.settings` process crash; rerunning with a delay produced the
successful results above.

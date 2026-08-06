# Car-Settings

`Car-Settings` is a platform-signed, Compose-based replacement APK for the
AAOS package `com.android.car.settings`. It ports the Settings feature set from
`custom-system-apps/My-System-App` and preserves the public AAOS intent and
legacy `CarSettingActivities$...` entry-point contract where the ported feature
exists.

The Launcher and SystemUI features are intentionally not included in this
project. They remain separate applications.

## Architecture

The project follows a Now In Android-style multi-module layout:

- `app`: platform entry point, Hilt application, Compose navigation, AAOS
  deep-link routing and compatibility aliases.
- `core/common`: coroutine dispatchers and shared platform result types.
- `core/ui`: Compose theme and reusable Settings components.
- `feature/<name>/{domain,data,presentation}`: use cases/contracts, Android
  platform data sources, repositories, ViewModels and Compose screens.
- `build-logic`: convention plugins for Android, Compose, Hilt, Navigation,
  Room schema support, Ktlint, Detekt and Jacoco.

ViewModels expose `StateFlow` and platform callbacks are bridged into coroutine
scopes. Hilt is enabled. Room support is available in build-logic, but the
ported Settings state is owned by Android framework services and therefore does
not need a local database.

## Build and install

```bash
cd /home/binh/Desktop/aosp/custom-system-apps/Car-Settings
./gradlew testDebugUnitTest --console=plain --max-workers=2
./gradlew :app:assembleDebug --console=plain --max-workers=2
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.android.car.settings
adb shell am start -n com.android.car.settings/.Settings_Launcher_Homepage
```

Public AAOS callers can also launch the replacement through the normal
contract, for example:

```bash
adb shell am start -a android.settings.SETTINGS
adb shell am start -a android.settings.WIFI_SETTINGS
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS \
  -d package:com.android.car.settings
```

The debug APK uses the development platform certificate configured in
`keystore.properties`. Do not use that key for a production image.

## Wipe-data AVD test

The tested AVD can be started from a clean data partition with:

```bash
systemd-run --user \
  --unit=mysystemapp-avd-20260802.service \
  --collect \
  --property=Restart=on-failure \
  --setenv=DISPLAY="$DISPLAY" \
  --setenv=XDG_RUNTIME_DIR="$XDG_RUNTIME_DIR" \
  --setenv=ANDROID_AVD_HOME="$HOME/.aaos-mysystemapp-20260802/.android/avd" \
  --setenv=ANDROID_SDK_ROOT="$HOME/Android/Sdk" \
  "$HOME/Android/Sdk/emulator/emulator" \
  -avd my_car_avd_mysystemapp_20260802 \
  -wipe-data -no-snapshot
```

Wait for `sys.boot_completed=1`, install the APK again, and then run the
smoke tests. A wipe removes the data-partition APK update, so a fresh install
is required after every wipe unless the APK is integrated into the system
image itself.

See:

- [Functions (English)](docs/FUNCTIONS_EN.md)
- [Chức năng (Tiếng Việt)](docs/FUNCTIONS_VI.md)
- [Test report](docs/TEST_REPORT.md)

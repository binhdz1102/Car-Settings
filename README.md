# Car Settings for Android Automotive

Car Settings is a modular Jetpack Compose reference application for Android Automotive OS
(AAOS). It keeps the platform package and public Settings intent contracts while presenting a
modern, rotary-aware settings experience across connectivity, display, sound, privacy, accounts,
applications, accessibility, and vehicle controls.

This repository publishes the application architecture and product logic for review. Reusable UI
primitives and rotary-focus infrastructure are consumed as private GitHub Packages; their source
and binaries are intentionally not included. A clone therefore requires authorized package access
and platform-signing material before it can produce an installable system APK.

## Highlights

- 19 independently buildable feature modules with domain, data, and presentation boundaries.
- Compose navigation with AAOS Settings actions and legacy activity aliases.
- Hilt dependency injection and StateFlow-driven screen state.
- Real vehicle-property adapters with explicit unavailable/read-only/failure states.
- Touch, rotary, dialog-focus, Direct Manipulation, and lazy-list focus integration.
- Automotive scrollbars with a reserved 48 dp interaction gutter.
- Compile-only AOSP framework and system-server APIs; no platform implementation is packaged.

## Demo

<img src="demo/Recording%202026-08-27%20231204.gif" width="100%"/>

| | |
| :---: | :---: |
| <img src="demo/Screenshot%202026-08-27%20183722.png" width="100%"/> | <img src="demo/Screenshot%202026-08-27%20183740.png" width="100%"/> |
| <img src="demo/Screenshot%202026-08-27%20183800.png" width="100%"/> | <img src="demo/Screenshot%202026-08-27%20183825.png" width="100%"/> |
| <img src="demo/Screenshot%202026-08-27%20184016.png" width="100%"/> | <img src="demo/Screenshot%202026-08-27%20184202.png" width="100%"/> |

## Project structure

```text
app/                 System application, intent routing, navigation, signing
core/common/         Dispatchers and shared result types
core/settings-api/   Cross-feature settings contracts
core/ui/             App composition, visual policy, and thin design-system adapters
core/vehicle/        Vehicle-property abstractions and AAOS integration
feature/*/           Domain, data, presentation, and feature tests
build-logic/         Android, Compose, Hilt, quality, and coverage conventions
libs/platform/       Compile-only AOSP framework stubs
libs/system-server/  Compile-only AOSP system-server stubs
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for dependency boundaries and runtime data flow.

## Prerequisites

- JDK 17
- Android SDK 36 and platform tools
- An AAOS AVD or compatible development target
- Read access to the private design-system GitHub Packages repository
- A development platform keystore compatible with the target image

## Private package access

The committed Gradle configuration resolves the design system only from GitHub Packages. Put
credentials in your user-level `~/.gradle/gradle.properties` file:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PACKAGE_READ_TOKEN
```

Alternatively, export `GITHUB_ACTOR` and `GITHUB_TOKEN`. The token needs package-read permission;
never commit it to this repository.

## Signing configuration

Create an ignored `keystore.properties` file at the repository root:

```properties
storeFile=C:/absolute/path/to/platform.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
storeType=JKS
```

Use development keys only. Production platform keys must remain in the product build system.

## Build, test, and install

```bash
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.android.car.settings
adb shell am start -a android.settings.SETTINGS
```

Useful compatibility routes include:

```bash
adb shell am start -a android.settings.WIFI_SETTINGS
adb shell am start -a android.settings.BLUETOOTH_SETTINGS
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS \
  -d package:com.android.car.settings
```

Wait for `adb shell getprop sys.boot_completed` to return `1` before installing after an AVD wipe.
A wipe removes a data-partition APK update, so install the debug APK again before smoke testing.

## Repository boundary

The Apache-2.0 license applies to the source in this repository. It does not grant access to, or a
license for, private package dependencies, signing keys, Android platform code, or OEM assets.

## License

Apache License 2.0. See [LICENSE](LICENSE).

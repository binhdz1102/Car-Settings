# Car Settings for Android Automotive (AAOS)

<div align="center">

[![Android](https://img.shields.io/badge/Platform-Android%20Automotive%20OS%20(AAOS)-3DDC84.svg?style=for-the-badge&logo=android)](https://source.android.com/devices/automotive)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-19%20Feature%20Modules-FF6D00.svg?style=for-the-badge)](#-modular-architecture)
[![Design System](https://img.shields.io/badge/Design%20System-B--Material%20Suite-00C49F.svg?style=for-the-badge)](preview/b-material-banner.png)
[![Rotary](https://img.shields.io/badge/Input-Rotary%20%26%20Touch-8E24AA.svg?style=for-the-badge)](https://developer.android.com/training/cars/apps#rotary-controller)
[![Pre-configured AVD](https://img.shields.io/badge/AOSP%20Image-Pre--configured%20AVD-E91E63.svg?style=for-the-badge&logo=github)](https://github.com/binhdz1102/AOSP-Images/tree/sdk-repo-linux-system-images-automotive-mysystemapp-v4)

<p align="center">
  <b>A production-grade, modular reference implementation of the Android Automotive OS Settings application</b><br/>
  Built with 100% modern Jetpack Compose, physical Rotary Controller / Knob navigation DSL, 19 isolated feature modules, and real Vehicle HAL (VHAL) property bindings.
</p>

</div>

---

## 🎬 Preview

Below is the live execution of the Car Settings application running on an Android Automotive OS development environment, demonstrating instant responsiveness, rotary focus transitions, and smooth Compose animations:

<div align="center">
  <img src="preview/Recording%202026-08-27%20231204.gif" alt="Car Settings Live AAOS Demo" width="100%" style="border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.5);"/>
</div>

---

## 🏎️ Engineering & Architectural Highlights

This project serves as a showcase of senior Android & automotive software engineering practices, addressing real-world challenges in building in-vehicle infotainment (IVI) systems:

- **Jetpack Compose & Clean Architecture:** Complete reimagining of the standard AOSP XML/Preference-based Settings into a reactive, single-activity, multi-module Compose architecture.
- **Strict Intent Contract Compatibility:** Preserves public AOSP `android.settings.*` intent actions and legacy activity aliases (`WIFI_SETTINGS`, `BLUETOOTH_SETTINGS`, `APPLICATION_DETAILS_SETTINGS`, etc.), ensuring seamless drop-in system compatibility.
- **Rotary Focus Controller & Direct Manipulation:** First-class physical rotary knob navigation, custom focus rings, Direct Manipulation mode for sliders/dials, and Focus Parking View integration to comply with automotive driver safety standards.
- **19 Feature Modules:** High separation of concerns with domain, data, presentation, and feature tests isolated across 19 feature packages, governed by custom Gradle convention plugins (`build-logic`).
- **Vehicle HAL (VHAL) & CarPropertyManager Adapters:** Robust vehicle-property layer handling multi-zone temperature, seat kinematics, lighting, and ADAS telemetry with explicit state handling (`Available`, `Unavailable`, `Error`, `ReadOnly`).
- **Automotive Ergonomics:** Strict adherence to Driver Distraction Guidelines (DDG), high-contrast night/day visibility, 48+ dp interactive touch targets, and automotive scrollbar gutters.

---

## 🎨 UI Architecture: Powered by B-Material

<div align="center">

<img src="preview/b-material-banner.png" alt="Powered by B-Material UI Framework" width="100%" style="border-radius: 12px; margin-bottom: 12px;"/>

</div>

### Key capabilities provided by B-Material:
- **Automotive Rotary Focus DSL:** Declarative rotary focus modifiers, active outline states, and rotary-scroll synchronizers.
- **Semantic Tokens & Theming:** High-contrast Material color palettes optimized for cabin ambient lighting conditions (Day / Night / Calm modes) and typography (`Sour Gummy` display headings paired with `Inter` body text).
- **UI Components:** Pre-built components with rendering performance optimized for various screen types.
- **Data Visualizations & Specialized Controls:** Interactive multi-zone cabin overlays, Bezier curve charts, ergonomic sliders, and modal bottom sheets.

---

## 📸 Key Feature Showcase

### 1. Vehicle Cabin & Climate Controls

| Multi-Zone HVAC & Temperature | Seat & Steering Wheel Adjustment |
| :---: | :---: |
| <img src="preview/aaos_screenshot_20260913_234218.png" width="100%" style="border-radius: 8px;"/> | <img src="preview/aaos_screenshot_20260913_233900.png" width="100%" style="border-radius: 8px;"/> |
| *Interactive 5-zone cabin top-view (Dr, Ps, RL, RC, RR) based VHAL climate properties.* | *Kinematic reach and height configuration with zone awareness and visual limits.* |

| Intelligent Interior Lighting | Vehicle Settings Hub |
| :---: | :---: |
| <img src="preview/aaos_screenshot_20260913_233941.png" width="100%" style="border-radius: 8px;"/> | <img src="preview/aaos_screenshot_20260913_233701.png" width="100%" style="border-radius: 8px;"/> |
| *Cabin ambient lights, reading lights, and footwell illumination mode selector.* | *Top-level vehicle feature hub categorizing climate, ADAS, seats, doors, and lights.* |

---

### 2. Driver Assistance & Safety Systems (ADAS)

| Blind Spot Warning & Collision Avoidance | Lane Support & Centering Assist |
| :---: | :---: |
| <img src="preview/aaos_screenshot_20260913_233722.png" width="100%" style="border-radius: 8px;"/> | <img src="preview/aaos_screenshot_20260913_233748.png" width="100%" style="border-radius: 8px;"/> |
| *Contextual safety warning dialog with animated graphic illustration and quick action buttons.* | *Emergency lane departure assist, sensitivity thresholds, and haptic feedback toggles.* |

---

### 3. Connectivity, Display & System Management

| Network & Wi-Fi Configuration | Display Brightness & Dark Theme |
| :---: | :---: |
| <img src="preview/aaos_screenshot_20260913_234138.png" width="100%" style="border-radius: 8px;"/> | <img src="preview/aaos_screenshot_20260913_234034.png" width="100%" style="border-radius: 8px;"/> |
| *Wi-Fi modal dialog with security protocol dropdown and keyboard entry.* | *82% brightness slider, system automatic day/night theme sync, and Calm Mode.* |

| Default Applications & Rotary Highlight | In-App Real-Time Search |
| :---: | :---: |
| <img src="preview/aaos_screenshot_20260913_234302.png" width="100%" style="border-radius: 8px;"/> | <img src="preview/aaos_screenshot_20260913_234344.png" width="100%" style="border-radius: 8px;"/> |
| *High-visibility cyan rotary focus indicator highlighting active items for rotary controller selection.* | *Universal search indexing settings across all 19 feature domains with immediate query matching.* |

---

## 🏛️ Modular Architecture

The codebase follows a modular clean architecture designed for large engineering teams, enforcing clear dependency rules and fast incremental build times:

```mermaid
graph TD
    App[":app<br/>(Application Entrypoint, Intent Routing & Signing)"]
    
    subgraph FeatureLayer ["Feature Layer"]
        F1[":feature:hvac"]
        F2[":feature:driverassistance"]
        F3[":feature:display"]
        F4[":feature:wifi"]
        F5[":feature:search"]
        F6[":feature:seatcontrol"]
        F7[":feature:vehiclelighting"]
        F8["... 12 other features"]
    end
    
    subgraph CoreLayer ["Core Architectural Layer"]
        C_API[":core:settings-api<br/>(Cross-feature Navigation & Contracts)"]
        C_UI[":core:ui<br/>(Compose Adapters & Theme Bridges)"]
        C_VEHICLE[":core:vehicle<br/>(CarPropertyManager & VHAL Abstractions)"]
        C_COMMON[":core:common<br/>(Coroutines Dispatchers & Shared Results)"]
    end
    
    subgraph ExternalLayer ["External Infrastructure & System APIs"]
        BMAT["B-Material Design Framework<br/>(Private UI Primitives & Rotary DSL)"]
        AAOS["Android Automotive OS (AOSP)<br/>(CarPropertyManager / System Services)"]
    end

    App --> FeatureLayer
    FeatureLayer --> C_API
    FeatureLayer --> C_UI
    FeatureLayer --> C_VEHICLE
    FeatureLayer --> C_COMMON
    C_UI --> BMAT
    C_VEHICLE --> AAOS
```

---

## 🛠️ Tech Stack & Engineering Practices

- **Language & Runtime:** Kotlin 2.x, Coroutines & StateFlow for reactive unidirectional state management.
- **UI Toolkit:** Jetpack Compose for Android Automotive, Material 3, and B-Material Design System.
- **Dependency Injection:** Dagger Hilt with scoped ViewModels and feature-isolated bindings.
- **Navigation:** Jetpack Navigation Compose with type-safe arguments and AOSP Intent action interop.
- **Hardware Integration:** Android Car API (`android.car.*`), `CarPropertyManager`, and VHAL mock telemetry generators.
- **Build System:** Gradle Kotlin DSL, Version Catalogs (`libs.versions.toml`), and composite convention plugins (`build-logic`).
- **Code Quality:** Unit tests with MockK, Kotlinx Coroutines Test, Compose UI tests, and Android Lint.

---

## 🚀 Quick Start & Running

### ⚡ Pre-Configured AAOS System Image (Ready to Run)

This project can be tested and run directly on a pre-built, fully configured Android Automotive OS virtual device without needing to build AOSP or configure platform signature keys from scratch:

> 📦 **Download Pre-Built AAOS Image:**  
> [binhdz1102/AOSP-Images (Branch: `sdk-repo-linux-system-images-automotive-mysystemapp-v4`)](https://github.com/binhdz1102/AOSP-Images/tree/sdk-repo-linux-system-images-automotive-mysystemapp-v4)

**Pre-configured environment highlights:**
- **System App Privileges:** Pre-granted platform permissions required for system settings and cross-user interactions.
- **Matching Platform Keys:** Pre-aligned with the project's development platform certificate (`keystore/platform.p12`).
- **Vehicle HAL (VHAL) Support:** Pre-configured car property services for HVAC, seats, and ADAS controls.

### Prerequisites
- JDK 17
- Android SDK 36 (Android 16 / Baklava) and Platform Tools
- AAOS Android Virtual Device (using the pre-configured system image above) or compatible hardware head unit

### 1. Build and Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### 2. Assemble Debug APK
```bash
./gradlew :app:assembleDebug
```

### 3. Install to Connected Automotive Emulator/Device
```bash
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.android.car.settings
adb shell am start -a android.settings.SETTINGS
```

### 4. Direct Intent Navigation Testing
Verify system intent aliases and deep-links directly via ADB:
```bash
# Launch Wi-Fi Settings directly
adb shell am start -a android.settings.WIFI_SETTINGS

# Launch Bluetooth Settings directly
adb shell am start -a android.settings.BLUETOOTH_SETTINGS

# Launch App Info directly
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.android.car.settings
```

---

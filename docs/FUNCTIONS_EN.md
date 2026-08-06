# Car-Settings functions

This document describes the functionality currently implemented in the
Compose replacement APK.

## Settings areas

- Home: entry cards for Display, Wi-Fi, Bluetooth, Sound & vibration, Climate,
  Apps, Notifications, Privacy, Security, Profiles & accounts, System and
  Search.
- Wi-Fi: radio state, available networks, saved/connected network details,
  add-network entry, hotspot configuration and Wi-Fi preferences.
- Bluetooth: radio state, paired devices, discoverability, scanning and the
  pairing flow.
- Sound: audio-volume groups, vibration/Do Not Disturb state and phone,
  notification and alarm ringtone selection.
- Display: gamma-corrected brightness, adaptive brightness, day/night mode,
  date/time and time-zone settings.
- Applications: recently used applications, all applications, application
  details, storage/cache, permission/notification summaries, special access and
  performance-impacting applications.
- Profiles & accounts: current profile, profile list, account providers,
  master sync state and account sync details.
- Notifications: per-application notification states and notification access
  entry points.
- Privacy: microphone, camera and vehicle-location controls and recent access.
- Security: screen-lock choices, credential storage and active device admins.
- Search: debounced Flow-based in-app search over the implemented Settings
  destinations.
- System: language/input, vehicle units, date/time, storage, device
  information, legal information and reset options.
- Climate: HVAC zones, climate power, automatic climate, temperature units,
  temperature and fan controls through the AAOS car service/VHAL.

## Compatibility contract

`SettingsIntentRouter` handles the common Android/AAOS Settings actions and the
legacy AOSP components under `com.android.car.settings.common.CarSettingActivities`.
Package-data application-detail intents and ringtone type extras are preserved.

The APK uses Compose for all new UI. The semantic hierarchy and labels follow
the AOSP/My-System-App Settings model, while this is a Compose reimplementation
and is not a pixel-for-pixel copy of every original XML preference resource.

## Non-applicable functionality

CarSettings does not contain a TaskView implementation in the inspected AOSP
source or in My-System-App. TaskView behavior belongs to the Launcher/task-host
surface, so no TaskView case is applicable to this APK.

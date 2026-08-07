# Car-Settings architecture survey

## Scope and AOSP relationship

`Car-Settings` is a Compose reimplementation of the AAOS CarSettings surface. The
`Settings/` directory is kept as the AOSP reference for intent actions, legacy
`CarSettingActivities$...` entry points, platform services and feature semantics.
The application does not copy the AOSP XML preference hierarchy directly; it
keeps the public contract while rendering the ported screens with Compose.

## Application layers

- `app`: platform-signed entry point, `MainActivity`, Hilt application, Compose
  navigation and `SettingsIntentRouter`.
- `core/common`: shared coroutine dispatchers and result types.
- `core/ui`: shared Compose theme and Settings components.
- `feature/<name>`: one Android library per product feature. Its source packages
  are logically separated into `...domain`, `...data` and `...presentation`.
- `build-logic`: convention plugins for Android, Compose, Hilt, code quality and
  test/coverage tooling.

The feature package dependency direction remains:

```text
presentation -> domain <- data
       \          |        /
        \---- core/common

presentation -> core/ui
```

`data` contains Android/framework and AAOS service adapters plus repository
implementations. `domain` contains models, repository contracts and use cases.
`presentation` contains ViewModels, navigation and Compose screens. Hilt modules
bind the data implementations to the domain contracts.

## Refactor result

Before the refactor, every feature was three Gradle projects:

```text
:feature:wifi:domain
:feature:wifi:data
:feature:wifi:presentation
```

This pattern was repeated for all 12 features, resulting in 36 feature modules.
The current graph has 12 feature modules:

```text
:feature:wifi
:feature:bluetooth
:feature:sound
:feature:display
:feature:applications
:feature:profileaccounts
:feature:system
:feature:notifications
:feature:privacy
:feature:security
:feature:search
:feature:hvac
```

Each module now has the following source layout while retaining the existing
package names and imports:

```text
feature/<name>/
  build.gradle.kts
  src/main/kotlin/.../feature/<name>/domain/
  src/main/kotlin/.../feature/<name>/data/
  src/main/kotlin/.../feature/<name>/presentation/
  src/test/kotlin/.../feature/<name>/{domain,data,presentation}/
```

The Gradle configuration is the union of the former three configurations for
the feature. Project dependencies between the former child modules are removed
because all three package layers now compile in the same Android library. The
Bluetooth component manifest was merged into the feature manifest with fully
qualified class names so the namespace change cannot alter component resolution.

## Runtime invariants checked

- `app` still imports the same presentation/domain package APIs.
- Settings and AAOS deep-link actions still resolve to `MainActivity`.
- Legacy `CarSettingActivities$...` aliases still target `MainActivity`.
- Platform/system-server jars remain compile-only dependencies for the same
  feature data sources.
- Hilt/KSP, unit tests, lint and debug APK packaging complete successfully.

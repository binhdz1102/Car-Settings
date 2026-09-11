# Architecture

## System boundary

Car Settings is an AAOS system application. The repository owns feature composition, navigation,
state handling, vehicle-screen policy, and platform adapters. Reusable design-system components
and rotary-focus implementations are external private artifacts; `core/ui` adds only app styling,
focus identifiers, destination policy, and small composition façades.

The APK retains `com.android.car.settings`, public Android Settings actions, and compatibility
aliases required by AAOS callers. Platform and system-server APIs are provided at compile time
via the external `com.b231001.bmaterial.aosp-platform-stubs` plugin. Their classes resolve from
the device boot classpath at runtime and are not packaged into the APK.

## Module graph

```text
                         +--------------------+
Android/AAOS intents --->|        app         |
                         +---------+----------+
                                   |
                    +--------------+---------------+
                    |              |               |
                    v              v               v
              +-----------+  +-----------+   +------------+
              | feature/* |  |  core/ui  |   |core/vehicle|
              +-----+-----+  +-----+-----+   +-----+------+
                    |              |               |
                    +-------+------+-------+-------+
                            v              v
                     +-------------+  +-------------+
                     |core/settings|  | core/common |
                     |    -api     |  +-------------+
                     +-------------+
```

`app` wires the feature graph and owns the activity, application, navigation host, deep-link
router, and signing configuration. Feature modules do not depend on one another; shared contracts
belong in `core/settings-api`, shared runtime utilities in `core/common`, vehicle integration in
`core/vehicle`, and reusable app composition in `core/ui`.

## Feature boundaries

Each `feature/<name>` Android library keeps three package-level layers:

```text
presentation ---> domain <--- data
      |              |          |
      +---------- core modules --+
```

- `domain` defines immutable models, repository contracts, and use cases without UI concerns.
- `data` implements contracts with Android managers, AAOS services, callbacks, and vehicle
  properties.
- `presentation` exposes StateFlow-backed ViewModels and Compose routes.

Keeping the layers in one Gradle module avoids a fragmented build graph while the package and
dependency rules remain reviewable and testable.

## Runtime data flow

```text
Framework / car service callback
            |
            v
       data adapter ---- write result / availability
            |
            v
       repository contract
            |
            v
          use case
            |
            v
     ViewModel StateFlow
            |
            v
       Compose screen ---- user intent ----> ViewModel
```

Callbacks are converted into lifecycle-scoped flows. ViewModels reduce service values, permission
state, and write outcomes into stable screen models. Screens emit intent events and never reach
directly into framework services.

Vehicle properties use explicit availability and write-result types so an absent property is not
mistaken for a valid default. UI policy can then render unsupported, read-only, pending, and failed
states consistently.

## UI and rotary focus

`core/ui` is the application boundary around the private design system. It selects AAOS dimensions,
colors, scrollbar gutter width, focus-area identifiers, and navigation restoration policy. The
underlying components, scrollbar geometry/pointer handling, rotary controller, Direct
Manipulation, and dialog implementation come from versioned GitHub Package artifacts.

This separation keeps product-specific composition visible in this repository without copying
library source or maintaining divergent workarounds. Touch-to-rotary handoff, destination/dialog
restoration, compact focus areas, Back handling, and the first rotary detent are exercised at the
library boundary and consumed by the app.

## AAOS compatibility

- `SettingsIntentRouter` maps supported Android Settings actions to Compose destinations.
- Legacy `CarSettingActivities$...` aliases target the current activity for platform callers.
- Hidden APIs are isolated behind data adapters and compile-only stubs.
- System services remain the source of truth; settings state is not duplicated in a local database.
- The application is expected to be platform signed and installed on a compatible AAOS image.

## Test strategy

- Domain tests cover use-case and policy decisions.
- Data tests cover framework callback mapping, availability, and failure behavior.
- Presentation tests cover StateFlow reduction and user intents.
- Architecture tests enforce module/package boundaries.
- Instrumentation and AVD smoke tests cover routing, rotary focus, dialogs, Direct Manipulation,
  and scrollbar dragging.

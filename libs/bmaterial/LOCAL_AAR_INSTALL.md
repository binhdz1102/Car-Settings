# B-Material bundled local AAR repository

My-System-App resolves B-Material `1.3.1` from this directory. It does not use
GitHub Packages, and a developer machine does not need `GPR_USER`, `GPR_KEY`,
or a GitHub package token.

## Layout

- Top-level `*.aar` files are immutable archive-only artifacts that are not
  published to the local repository.
- `SHA256SUMS.txt` is the checksum source of truth for the complete 28-artifact
  input bundle; a published artifact's canonical binary exists only in `maven/`.
- `LOCAL_MAVEN_MANIFEST.json` records the coordinate, checksum, and publication
  status of every raw AAR.
- `maven/` is the self-contained Maven repository used by Gradle.

Only the 17 artifacts used by the current UI are published in `maven/`: the
three UI-core artifacts plus button, dialog, card, chip, indicator, layout,
list item, loading, slider, snackbar, scrollbar, switch, text, and text field.
The remaining 11 raw AARs are retained as `archive-only`; they are not
implicitly added to the app. Redundant top-level copies of the 17 published
artifacts are intentionally omitted to avoid storing large binaries twice.

`ccp-rotary-focus-1.3.1.aar` is deliberately archive-only. The application
uses the audited source module `:third_party:bmaterial-ccp-rotary-focus`, so
adding the AAR would duplicate `com.android.car.ui` and rotary classes.

## Gradle configuration

`settings.gradle.kts` registers `libs/bmaterial/maven` as `BundledBMaterial`
with `exclusiveContent` for `com.b231001.bmaterial`. Keep the version-catalog
coordinates unchanged; Gradle resolves those coordinates from this local
repository before consulting Google Maven or Maven Central.

Do not add a GitHub Packages repository or credentials back to this checkout.
Google Maven and Maven Central are still required for AndroidX, Compose,
Kotlin, and other non-B-Material dependencies.

## Verification

Run these commands after copying or updating the bundle:

```powershell
python scripts/verify_bmaterial_local.py
.\gradlew.bat --offline verifyBmaterialLocal
```

The first command validates archive-only and published SHA-256 values, rejects
redundant published copies, validates Maven layout,
POM/module identity, and the absence of a GitHub Packages resolver. The Gradle
task additionally resolves `:core:ui` and `:app` and verifies the exact
17-artifact B-Material graph with CCP supplied by the source project.

## Updating B-Material

Do not replace a single AAR in isolation. Update the input bundle, checksums,
manifest, and `maven/` metadata together, then remove top-level copies of
published artifacts. Run the verification commands above and the normal
debug/release build and Automotive AVD regression suite.

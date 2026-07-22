# Custom CarSetting

Minimal replacement for the `com.android.car.settings` system application in the
matching AAOS development emulator image.

The project is signed with the AOSP development platform certificate stored in
`keystore/platform.p12`. Do not use this key for a production image.

Build on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Install as an update to the CarSettings system application:

```powershell
adb install -r -d app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.android.car.settings
adb shell am start -a android.settings.SETTINGS
```

Expected platform certificate SHA-256:

```text
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
```

To restore the original system application, wipe the AVD data.

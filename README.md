# ATK Horizon

An Android app to configure ATK Horizon USB-C headphones: presets and an 8-band parametric EQ, microphone noise cancelling, gain offsets and soft reset. It is an unofficial alternative to ATK Hub, with a Russian and English UI.

## Supported device

- ATK Horizon, wired USB-C, USB VID `0x373B` / PID `0x120C` (TTGK variant).
- The KTMicro variant (PID `0x129F`) is not supported.

## Requirements

- Android 13 or newer (tested target: Android 16).
- A phone with USB host (OTG) support.

## Install from the APK

1. Build the APK (see below) or take a prebuilt `app-release.apk`.
2. Either allow installing from unknown sources for your file manager or browser and open the APK, or install over adb:
   ```
   adb install app/build/outputs/apk/release/app-release.apk
   ```
3. Plug in the headphones and grant USB access when asked.

The release build is signed with the debug key (personal build).

## Build

Requires JDK 21 and the Android SDK 36.

```
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Run the unit tests with `./gradlew :app:testDebugUnitTest`.

## Intentionally not supported

- Firmware updates
- The KTMicro variant
- Bluetooth
- iOS
- Other ATK devices

## Known behaviour

The in-line remote buttons may not work while the app is in the foreground, because the app claims the HID interface. They work again when the app goes to the background.

## Protocol

The protocol was reverse-engineered from the ATK Hub web app. No ATK code is included in this repository.

## Disclaimer

This is an unofficial project and is not affiliated with ATK. Use it at your own risk.

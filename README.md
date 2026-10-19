<div align="center">

<img src="docs/images/icon.png" width="128" height="128" alt="ATK Horizon app icon">

# ATK Horizon

**Tune your ATK Horizon headphones from an Android phone.**<br>
A free, open-source alternative to ATK Hub, with an 8-band parametric EQ and a Liquid Glass interface.

[![Latest release](https://img.shields.io/github/v/release/r415uk3/atk-horizon?label=release&color=ff6b1a)](https://github.com/r415uk3/atk-horizon/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/r415uk3/atk-horizon/total?color=ff6b1a)](https://github.com/r415uk3/atk-horizon/releases)
![Android 13+](https://img.shields.io/badge/Android-13%2B-555)
[![MIT License](https://img.shields.io/github/license/r415uk3/atk-horizon?color=555)](LICENSE)

### [⬇︎ Download for Android](https://github.com/r415uk3/atk-horizon/releases/latest)

<sub>ATK Horizon with USB-C · English and Russian · no internet permission</sub>

</div>

## Features

<table>
<tr>
<td width="50%" valign="top">

**Parametric equalizer**<br>
Drag points on the response curve to set frequency and gain, or open a band to pick the filter type and set Q precisely. 8 bands, live while you drag.

**Presets**<br>
All ATK Hub presets (CS2, Valorant, APEX, Delta, Rock, R&B, Hip-hop and more), plus your own: save, rename, delete.

**Import from ATK Hub**<br>
Open a `.eq` file exported from ATK Hub and it becomes a preset in the app.

</td>
<td width="50%" valign="top">

**Every change is verified**<br>
After each write the app reads the settings back from the headphones. If they didn't apply, you see an error and the values the headphones actually hold.

**Mic and device**<br>
AI noise cancelling for the mic, mic and playback gain offsets, firmware version, and a reset to defaults.

**Liquid Glass**<br>
Real refracting glass, light, dark and automatic themes, and your own photo as the background.

</td>
</tr>
</table>

## Install

1. Download **atk-horizon-x.y.z.apk** from [the latest release](https://github.com/r415uk3/atk-horizon/releases/latest) on your phone.
2. Open it and allow installing apps from this source when Android asks.
3. Plug the headphones into the phone's USB-C port, open **ATK Horizon** and allow USB access.

> [!NOTE]
> Release builds are signed with a personal key, so an APK you build yourself can't update the downloaded one. Uninstall first when switching.

**In-line buttons:** while the app is open it talks to the headphones directly, so the volume and play buttons on the cable may not respond. They work again as soon as the app goes to the background.

**Supported:** ATK Horizon wired USB-C (USB ID `373B:120C`). Not supported: the KTMicro variant (`373B:129F`), Bluetooth, other ATK devices, and firmware updates, which stay in the official app.

## Privacy

The app has no internet permission, analytics or telemetry. It talks only to the headphones over USB. Presets, settings and your background photo stay on the phone.

## По-русски

ATK Horizon — бесплатное приложение для Android, которое настраивает наушники ATK Horizon с USB-C вместо ATK Hub. Параметрический эквалайзер на 8 полос, все пресеты Hub и свои, импорт файлов `.eq`, шумоподавление микрофона, интерфейс на русском и Liquid Glass.

[**Скачать APK**](https://github.com/r415uk3/atk-horizon/releases/latest), разрешить установку из этого источника, подключить наушники в USB-C и разрешить доступ к USB. Пока приложение открыто, кнопки на проводе могут не работать: они возвращаются, когда приложение уходит в фон.

## For developers

<details>
<summary><b>Build from source</b></summary>

<br>

Requirements: JDK 21 and the Android SDK with platform 37 (the app targets API 36, min API 33).

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug       # debug build also logs raw USB reports and a coefficient check to logcat (tag "Horizon")
```

</details>

<details>
<summary><b>Tests</b></summary>

<br>

```bash
./gradlew :app:testDebugUnitTest
```

The protocol, the EQ math and the ViewModel are covered by JVM tests against a simulated headset (`FakeHorizon`), so no hardware is needed. [docs/hardware-notes.md](docs/hardware-notes.md) is the manual checklist for a real device.

</details>

<details>
<summary><b>How it works</b></summary>

<br>

The headset is a USB HID device. The app sends the same 63-byte reports as the ATK Hub web app (report ID `0x4B`) and computes the biquad coefficients on the phone, exactly as Hub does. The protocol was worked out from how ATK Hub talks to the headset; no ATK code is included in this repository. Commands that could brick the headset (flash erase, bootloader) have no code path in the app.

</details>

## License

ATK Horizon is released under the [MIT License](LICENSE).

The glass controls are adapted from [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) by Kyant and use its `backdrop` library, under the Apache License 2.0 ([third_party/AndroidLiquidGlass-LICENSE](third_party/AndroidLiquidGlass-LICENSE)).

This is an unofficial project, not affiliated with or endorsed by ATK. Use at your own risk.

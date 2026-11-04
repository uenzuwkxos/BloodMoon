# 🌙 Blood Moon

**Blue-light, eye-strain and screen-dimming filter** for Android and Android TV.
100% local: **no internet, no accounts, no ads, no data collection.**

---

## ✨ Features

- **Color filter** in 4 tones (Black, Red, Orange, Yellow) with **Intensity** and **Tone** sliders, a **Double filter** (two identical layers for even stronger colors) and a smooth fade when turning on/off.
- **Advanced features** (need ADB, Shizuku or Root): system **Extra Dim**, **Grayscale** and **System Filter** (Root; adjusts the system's RGB colors directly).
- **Profiles**: save the full state (filter, advanced features and TV features) and apply it with one tap.
- **Schedules**: automatic **sunset and sunrise** (based only on your location) or **Scheduled profiles** (start time + saved profile + optional end time + days of the week, with a switch per schedule and "Apply latest schedule on start").
- **Per-app exclusions**: the filter and/or grayscale stay off in the apps you choose (e.g. camera, gallery).
- **TV features**: *Timed power-off* and *Sleep timer*.
- Quick Settings **tile**, **widget** (power, colors, intensity, tone and shortcuts) and **notification** with controls.
- Light, Dark and Black (AMOLED) **themes**, 15 languages (pt, en, es, de, fr, zh, ja, ko, hi, ar, ru, id, it, nl, pl) and **D-pad** navigation with a focus indicator.
- **Backup**: export/import everything as a JSON file.

## 🤖 Made with AI

This app was built by AI (Claude, by Anthropic), guided by a human who defined the features, tested the app on real devices and reported the bugs. Translations are AI-generated and may contain mistakes; corrections are welcome.

## 📲 Installation

1. Download the latest APK from **[Releases](../../releases)** and install it.
2. Grant **"Display over other apps"** and enable the **accessibility service** when the app asks (recommended, see below).
3. *(Optional)* Advanced features: pick **one** of these accesses:
   - **ADB** (once, from a computer):
     ```bash
     adb shell pm grant com.bloodmoon.app android.permission.WRITE_SECURE_SETTINGS
     ```
   - **Shizuku** or **Root**: enable it in *Options* and tap *Grant permission*.

### Why the accessibility service?

It **does not read or collect screen content** (`canRetrieveWindowContent="false"`). It is used to:
- draw the filter on a layer above regular windows, covering the **whole system** (Settings, permission dialogs…), which a regular overlay cannot do;
- keep the filter from being killed by other apps/the system (some TVs, such as TCL, terminate apps with regular floating windows);
- know which app is in the foreground (exclusions) and, as a last resort, turn the TV screen off.

With ADB, Shizuku or Root the app enables the service by itself; without them, enable it in *Settings › Accessibility*.

## 🔒 Privacy and security

- **No internet permission**: the app never sends anything anywhere.
- All data (settings, profiles, schedules, exclusions) stays **on the device only**; `allowBackup` is off.
- Permissions and what they are for: `SYSTEM_ALERT_WINDOW` (draw the filter), `WRITE_SECURE_SETTINGS` (grayscale, extra dim, sleep timer; granted by you via ADB/Shizuku/Root), `PACKAGE_USAGE_STATS` (foreground app, alternative to accessibility), `ACCESS_COARSE_LOCATION` (compute sunset/sunrise **locally**), notifications, foreground service, `RECEIVE_BOOT_COMPLETED` and exact alarms (schedules).
- *Options › Diagnostics* keeps a local log (service events, device model). It only leaves the device if **you** tap *Share*.

## 🛠️ Building

Requirements: **JDK 21** (17+) and the Android SDK (compileSdk 37). `minSdk 28`.

```bash
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK
```

## 🤝 Contributing

Issues and PRs are welcome. When reporting a problem, attach the report from *Options › Diagnostics* (it contains no personal data).

## 📜 License and credits

Licensed under the **GNU GPL v3**: see [LICENSE.md](LICENSE.md).

Inspired by **[Red Moon](https://github.com/LibreShift/red-moon)** (LibreShift, GPLv3). The idea of keeping grayscale on through an accessibility service came from **[Grayscaler](https://github.com/C10udburst/Grayscaler)**. Third-party libraries and their licenses are listed in *Options › About › Open Source Licenses*. Thanks also to **[Shizuku](https://github.com/RikkaApps/Shizuku)** and **[libsu](https://github.com/topjohnwu/libsu)**.

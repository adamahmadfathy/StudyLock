# StudyLock Android project — prototype 0.2.0

This is a native Android Studio project (Kotlin + Jetpack Compose), not an APK. It is the first implementation scaffold for the agreed StudyLock behavior.

## Agreed timer rules
- Lock duration: minimum 30 minutes (30 is allowed).
- Cycle: locked → when the lock ends, StudyLock asks "Have you finished studying?". Yes opens the chosen apps until you leave them; "I'm having a break" opens them for the allowed-usage minutes (1–30) and then the lock starts again.
- Default early-deactivation challenge length: 100 characters.
- Any incorrect typed character immediately regenerates the challenge and clears the input.
- The user chooses the apps to protect and can uninstall StudyLock normally.

## Get the APK without Android Studio (GitHub Actions)
1. Create a free account at github.com and make a new repository.
2. Upload everything inside this folder (including the hidden `.github` folder) to the repository.
3. Open the repository's **Actions** tab, choose **Build StudyLock APK**, and wait about 5 minutes.
4. Open the finished run, download **StudyLock-APK** (a zip), unzip it and copy `app-debug.apk` to your phone.
5. On the phone, open the APK and allow "Install unknown apps" when asked.

## Build with Android Studio instead
Open this folder as a project, let Gradle sync, then Build > Build APK(s). Or run `./gradlew assembleDebug`; the file is `app/build/outputs/apk/debug/app-debug.apk`.

## First-run setup
1. Open StudyLock, choose apps and set the timers.
2. Enable StudyLock under Android Settings → Accessibility (the status card shows when it is on).
3. Press Start automatic cycle (it saves your settings first).

On Android 13+, a sideloaded app may show the Accessibility switch as greyed out. Open Settings → Apps → StudyLock → ⋮ menu → **Allow restricted settings**, then try again.

## Changes in 0.2.0
- Start automatic cycle now saves your settings first (before, unsaved apps were never blocked).
- A protected app that is already open is blocked when a lock period begins (checked every second).
- Back on the lock screen goes Home instead of revealing the app.
- Timers, app choices and Stop cycle are locked during a lock period, so they cannot be used to skip it.
- The main screen's clock updates live and shows whether app protection is on.
- App launcher icon and package-visibility declaration added.

## Important prototype limitations
- Android Accessibility access is required to detect selected apps. Android/OEM background restrictions can affect reliability; test on the target phone.
- This prototype uses the device wall clock for persisted cycle timing. A user who changes the device clock may affect timing; a reboot-resistant, tamper-resistant clock cannot be guaranteed by an ordinary app without a trusted external time source or device-owner privileges.
- Force-stop, disabling Accessibility, Safe Mode, uninstalling the app, and some OEM/system flows can bypass app-level blocking. Uninstall is intentionally allowed per the user's request.
- This is an early development scaffold and has not been compiled or tested on a physical Android device. Do not rely on it as a security boundary.
- Challenge entry is prefix-validated and regenerates on the first wrong character. System keyboards may expose clipboard features depending on Android version; clipboard prevention needs device-level testing and further hardening.

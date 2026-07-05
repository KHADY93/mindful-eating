# Mindful Eating

A Wear OS app that helps you pace your meals. Start a session, and a countdown ring around
the edge of the watch face depletes over a configurable interval (60 seconds by default).
When it hits zero, the watch gives a distinct double-pulse vibration to cue your next bite,
the bite counter increments, and a new countdown starts automatically — repeating until you
end the session.

Built for the Pixel Watch 4 (standalone Wear OS app, no phone companion required), using
Kotlin + Jetpack Compose for Wear OS.

## Features

- Perimeter progress ring that depletes over the countdown, tinted with your chosen accent color
- Distinct double-pulse vibration cue, separate from a single system buzz
- Auto-repeating cycle with a live bite counter for the current session
- Pause/resume without losing your place; long-press the button to end the session
- Settings: adjustable interval (10–180s in 5s steps), 10 preset accent colors
- Session history: date/time, bite count, and duration for each past session, with per-entry delete
- Ambient (always-on display) support — the countdown keeps ticking and can still vibrate when the screen dims, shown as a thinner monochrome ring in low-power mode

## Requirements

- Android Studio (2025.2 "Otter" or newer) with JDK 17
- Android SDK Platform 36.1 (Android 16 QPR2) and Build-Tools 36+ installed
- A Wear OS device or emulator running Wear OS 5+ (minSdk 30)

## Building

Open the project in Android Studio and let Gradle sync, or build from the command line:

```
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Installing on a physical watch over Wi-Fi

1. On the watch: **Settings > Developer options > Wireless debugging**, enable it, then tap
   **Pair new device** to get a pairing code and IP:port.
2. Pair once:
   ```
   adb pair <PAIRING_IP>:<PAIRING_PORT>
   ```
3. Connect to the watch's debug endpoint (a separate IP:port shown once paired):
   ```
   adb connect <WATCH_IP>:<DEBUG_PORT>
   ```
4. Install the APK:
   ```
   adb -s <WATCH_IP>:<DEBUG_PORT> install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## License

MIT — see [LICENSE](LICENSE).

# LAN Talkie (lantalk-ptt)

Offline push-to-talk walkie-talkie for Android over Wi-Fi / phone hotspot. No internet, no server.

- Live voice: UDP, 16 kHz mono PCM, 20 ms frames, jitter buffer.
- Coming: text messages, files and voice notes over TCP — see the [roadmap](docs/ROADMAP.md).

## Download
Latest APK: [Releases](../../releases/latest). Every push also produces an APK artifact (`file_apk`) in the
[Actions](../../actions) tab.

## Use
1. Put both phones on the same Wi-Fi or one phone's hotspot (no captive portal / client isolation).
2. Install the APK, allow microphone and notifications.
3. Enter the other phone's IP (shown on its screen), port 8888, tap Start.
4. Hold the button to talk.

## Docs
[Roadmap](docs/ROADMAP.md) · [Architecture](docs/ARCHITECTURE.md) · [Testing](docs/TESTING.md) ·
[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md)

## Build
`gradle assembleDebug` (JDK 17, Gradle 9.3.1, Android SDK 36). CI does this automatically.

# Rivora

<div align="center">

[![Build Release APK](https://github.com/sonu-999/RivoraV2/actions/workflows/release.yml/badge.svg)](https://github.com/sonu-999/RivoraV2/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/license-MIT-orange.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF.svg)](https://developer.android.com/compose)

**A native Android download manager, BitTorrent client, and privacy-focused web browser.**

[Features](#features) • [Getting Started](#getting-started) • [Building](#building-from-source) • [Privacy](#privacy) • [Contributing](#contributing)

</div>

## Overview

Rivora combines multi-network downloading, BitTorrent support, smart media detection, and an integrated browser in one Jetpack Compose application. It is built in Kotlin and designed for Android 7.0 (API 24) and newer.

> Rivora is a download and browsing tool. Use it only to access and distribute content you own or are authorized to use.

## Features

### Download management
- Accelerate downloads over available Wi-Fi and cellular connections
- Monitor live progress, speed, status, and chunk activity
- Continue work through foreground download notifications
- Open completed files through Android's secure file-provider integration
- Detect magnet links and downloadable files from visited pages

### BitTorrent
- Download magnet links with libtorrent4j
- Discover peers through DHT and public trackers
- Continue downloads through the foreground service
- Choose sequential downloading where supported

### Integrated browser
- Persistent tab management and navigation controls
- Download detection for archives, documents, APKs, and media
- Browser bookmarks and desktop-site switching
- Page-level media discovery
- Cookie and referer forwarding for recognized downloads

### Privacy and ad shielding
- Built-in EasyList, AdAway, and Peter Lowe-style filter support
- Subdomain and parent-domain block matching
- Popup, redirect, and malicious browser-scheme blocking
- Runtime protection for clickjacking overlays and cosmetic advertisements
- Per-site shield controls and custom allowlisting

## Screenshots

> Place polished app screenshots in `docs/screenshots/` and link them here before the public release.

## Getting started

### Download an APK

Download the latest signed or CI-generated APK from the [Releases page](https://github.com/sonu-999/RivoraV2/releases).

The release workflow also uploads `rivora-release-apk` as a GitHub Actions artifact for tagged and manually dispatched builds.

### Build requirements

- Android Studio with Android SDK 36
- JDK 21
- Gradle 9.3.1 or the Gradle wrapper
- Android 7.0 (API 24) or newer for installation

## Building from source

```bash
git clone https://github.com/sonu-999/RivoraV2.git
cd RivoraV2
./gradlew :app:assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run the unit tests with:

```bash
./gradlew :app:testDebugUnitTest
```

Build the release variant with:

```bash
./gradlew :app:assembleRelease
```

Release signing can be configured with `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables.

## Configuration

Copy `.env.example` to `.env` and add local secrets when an optional integration requires them. Never commit real API keys or signing credentials.

```dotenv
GEMINI_API_KEY=your_local_development_key
```

The Android project also supports the standard Gradle signing variables used by the release workflow:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_PASSWORD
```

## Architecture

```text
app/src/main/java/com/rivora/
├── backend/       Download services and network engines
├── browser/       Embedded browser and media detection
├── ui/            Jetpack Compose screens and components
└── MainActivity.kt
```

## Permissions

Rivora requests only permissions needed by its features:

- Internet and network access for downloads and browsing
- Notifications and foreground services for download progress
- Legacy storage access where required for public downloads
- Package installation access for APK downloads

## Privacy

Rivora includes privacy and ad-blocking features, but no privacy tool can guarantee complete anonymity. Downloaded files, browsing sessions, and network traffic may still be visible to websites and network operators according to their own policies.

## Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a descriptive feature branch.
3. Keep changes focused and follow Kotlin and Compose conventions.
4. Add or update tests where practical.
5. Open a pull request with screenshots or a clear test plan for UI changes.

Before reporting security-sensitive issues, review `SECURITY.md` when available.

## License

Rivora is released under the [MIT License](LICENSE).

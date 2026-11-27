# Rivora 🚀

<div align="center">

[![Build & Publish Release APK](https://github.com/JishnuPG-tech/Rivora/actions/workflows/release.yml/badge.svg)](https://github.com/JishnuPG-tech/Rivora/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](https://opensource.org/licenses/MIT)
[![Android MinSdk](https://img.shields.io/badge/MinSdk-24%20%2F%20Android%207.0%2B-blue.svg)](https://developer.android.com)

**High-performance multi-network download manager & BitTorrent engine with an integrated ad-blocking web browser for Android.**

</div>

---

## 🔥 What is Rivora?

Rivora is an advanced, power-user download manager and torrent client built natively in Kotlin and Jetpack Compose using Material Design 3. It aggregates available network interfaces (Wi-Fi and Cellular) for lightning-fast multi-stream downloads, supports BitTorrent magnet links and `.torrent` files, and features an integrated multi-tab web browser equipped with an open-source ad and tracker blocker.

---

## ✨ Key Features & Capabilities

### 🌐 1. Integrated Web Browser & Stream Capture
- **Multi-Tab Browsing**: Smooth tab management with a persistent bottom toolbar, back/forward navigation, desktop site toggle, and bookmarks.
- **Smart Download Detection**: Automatically detects downloadable files (ZIP, APK, ISO, documents, media streams) and BitTorrent magnet links as you browse, forwarding headers (Cookies, Referer, User-Agent) straight into Rivora's download engine.
- **Page Media Scanner**: Floating indicator badge listing all downloadable media and archive links found on the current webpage.

### 🛡️ 2. Advanced Open-Source Ad & Popup Shield
- **10,000+ Filter Rules**: Built-in blocklist combining **EasyList**, **AdAway**, and **Peter Lowe’s Ad Server List** for $O(1)$ domain and parent-subdomain matching.
- **Popup & Redirect Neutralization**: Intercepts script-triggered `window.open` popups, prevents third-party site redirect hijacking, and blocks malicious intent/market schemes.
- **Clickjacking & Overlay Guard**: Injects lightweight runtime scripts with a `MutationObserver` to automatically collapse invisible full-screen clickjacking overlays and ad banners.
- **Cosmetic Element Hiding**: Removes sponsored slots, ad frames, and tracking iframes before they render.
- **Site Allowlist & List Sync**: Toggle the ad shield per-site or sync fresh community blocklists with one tap.

### ⚡ 3. Multi-Network Download Engine & BitTorrent
- **Dual-Stream Acceleration**: Simultaneously download across Wi-Fi and Cellular networks for maximum throughput.
- **BitTorrent Support**: Full libtorrent4j-powered engine supporting magnet links, DHT discovery, public trackers, and sequential downloading.
- **Chunk Visualizer**: Live graphical matrix displaying download progress broken down into chunks across active streams.

---

## 🛠️ Recent Changes & Changelog (v1.1.7)

- **WebView Ad Shield Hardening**: Upgraded URL interceptor and navigation blocking to prevent popup windows, auto-redirects, and malicious clickjacking overlays.
- **Design System Alignment**: Unified browser start page, omnibox status badge, ad-block control sheet, and settings dialogs to match Rivora’s authentic Deep Carbon (`#0A0A0B`), Vibrant Orange (`#F36712`), and Crisp Silver (`#C5C5CB`) UI kit.
- **Automated GitHub Releases Workflow**: Added a robust GitHub Actions workflow (`.github/workflows/release.yml`) targeting Java 21, Android SDK 36, and Gradle 9.3.1 to automatically build and attach signed `release.apk` files to GitHub Releases.

---

## 📥 Download & Installation

Grab the latest pre-built APK from the [GitHub Releases](https://github.com/JishnuPG-tech/Rivora/releases) page, or build it yourself using Android Studio or Gradle:

```bash
gradle :app:assembleRelease
```

---

## 📜 License

Rivora is open-source software released under the [MIT License](LICENSE).

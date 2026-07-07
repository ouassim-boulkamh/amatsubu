<div align="center">

<img src="./.github/assets/logo.png" alt="Amatsubu logo" title="Amatsubu logo" width="80"/>

# Amatsubu

[![Platform](https://img.shields.io/badge/platform-Android-lightgrey)](#requirements)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue)](./LICENSE)

Native Android manga reading for your Suwayomi library, with Mihon's reader feel.

</div>

> **Amatsubu** *(雨粒)* means "raindrop".

## What It Is

Amatsubu is an Android client for [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server). You point it at a server you already run; Suwayomi handles the library, sources, extensions, downloads, tracking, history, and categories.

The app grew from [Mihon](https://github.com/mihonapp/mihon), keeping the native Android reading experience while swapping the data layer to Suwayomi.

## Features

- Native Android UI and reader based on Mihon.
- Server-backed library, browse, updates, history, tracking, and downloads.
- First-run server setup for URL, port, basic auth, and timeout.
- Reader state and chapter actions wired to Suwayomi.
- Server download queue controls, plus Amatsubu-only device copies for chapters you want saved on your phone.
- Client cache for recently loaded pages, with cache clearing and auto-clear settings.
- Client-side themes, security settings, onboarding, and local app backups that stay separate from server backups.

## Requirements

- Android 8.0 or newer.
- A reachable Suwayomi-Server instance.

## Security Posture

Amatsubu is a client for a Suwayomi server that you control. The app does not host manga, proxy third-party content, or bundle source credentials.

The Android network security configuration intentionally permits cleartext HTTP traffic and trusts user-installed certificate authorities. This keeps local Suwayomi servers, LAN-only reverse proxies, self-signed certificates, and source-provided HTTP assets usable. Use HTTPS for any Suwayomi server exposed outside a trusted local network.

WebView Safe Browsing is disabled to avoid sending visited source URLs to Google Safe Browsing services. This improves privacy for in-app browsing, but it also means Android WebView will not provide that additional malicious-page warning layer inside Amatsubu.

## Building

JDK 21 is required.

```powershell
.\gradlew.bat :app:assembleDebug
```

Useful checks:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugKotlin
```

## Credits & License

Amatsubu stands on the work of [Mihon](https://github.com/mihonapp/mihon), Tachiyomi, and the [Suwayomi](https://github.com/Suwayomi) project.

Licensed under the [Apache License 2.0](./LICENSE), matching the Mihon codebase this project is derived from. Third-party files keep their own compatible notices.

Amatsubu hosts no content and is not affiliated with content providers.

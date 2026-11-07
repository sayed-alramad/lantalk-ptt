# Changelog
All notable changes. Format: [Keep a Changelog](https://keepachangelog.com), versions follow tags.

## [Unreleased]
### Added
- Docs: ROADMAP, ARCHITECTURE, TESTING, CONTRIBUTING, ADR 0001.
- Release workflow that attaches the APK to GitHub Releases on `v*` tags.
- Committed debug keystore so all builds share one signature.
### Removed
- Unused Firebase / AppCheck dependencies and google-services plugin.

## Earlier work (pre-tag)
- Fix: UDP socket bound to a random port instead of 8888 (voice now flows).
- Diagnostics on screen, Wi-Fi binding option, Arabic-digit IP input, speaker routing.
- CI: SDK license acceptance, fixed Gradle version.

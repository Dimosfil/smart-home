# Technology Stack

Last reviewed: 2026-08-05

Canonical source: this file
Linked from: `README.md`

This is project documentation. Keep business rules, feature algorithms, workflow
contracts, state machines, and verification guarantees in project memory; keep
stack facts, commands, runtime assumptions, and operational notes here.

## Summary

- Primary stack: Kotlin Android application with Jetpack Compose
- Runtime model: one Android app process; platform BLE and DNS-SD callbacks feed
  a ViewModel; switch protocols are isolated behind an adapter
- Current confidence: manifests, dependencies, tests, lint, and debug assembly
  verified locally

## Components

| Layer | Technology | Evidence | Notes |
| --- | --- | --- | --- |
| Language/runtime | Kotlin 2.1.10, Java 17 bytecode | `build.gradle.kts`, `app/build.gradle.kts` | Android API 26+ |
| Frontend | Jetpack Compose BOM 2025.02.00, Material 3 | `app/build.gradle.kts`, `MainActivity.kt` | Russian prototype UI |
| Device discovery | Android BLE scan and `NsdManager` DNS-SD | `discovery/` | BLE and local Wi-Fi |
| Device control | `SwitchController`, local HTTP adapter | `control/` | Vendor/Matter/BLE adapters remain replaceable |
| Data/storage | In-memory `StateFlow` state | `MainViewModel.kt` | No persistence in MVP |
| Build/package | Gradle 8.11.1, Android Gradle Plugin 8.9.2 | wrapper and Gradle manifests | Debug APK verified |
| Test/quality | JUnit 4 and Android lint | `app/src/test/`, Gradle tasks | Physical-device checks outstanding |
| Deployment/runtime | Android SDK 35, target SDK 35 | `app/build.gradle.kts` | APK install through ADB/Android Studio |

## Commands

| Purpose | Command | Evidence |
| --- | --- | --- |
| Install | `.\gradlew.bat installDebug` | `tools/AGENT_RUNBOOK.md` |
| Run | Launch `Smart Home` after installation | `AndroidManifest.xml` |
| Test | `.\gradlew.bat testDebugUnitTest lintDebug` | Gradle build contract |
| Build | `.\gradlew.bat assembleDebug` | Gradle build contract |

## External Services

| Service | Role | Evidence | Boundary |
| --- | --- | --- | --- |
| Local smart switch | Device discovery and commands | `smart-switch-mvp.md` | Local network; protocol adapter required |

## Gaps

- Select the first physical switch model and document its provisioning and
  command protocol.
- Verify BLE scan and DNS-SD behavior on a physical Android device.
- Replace prototype cleartext HTTP with an authenticated secure device contract
  before production use.

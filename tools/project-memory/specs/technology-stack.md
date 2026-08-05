# Technology Stack

Last reviewed: 2026-08-05

Canonical source: this file
Linked from: `README.md`

This is project documentation. Keep business rules, feature algorithms, workflow
contracts, state machines, and verification guarantees in project memory; keep
stack facts, commands, runtime assumptions, and operational notes here.

## Summary

- Primary stack: Kotlin Android application with Jetpack Compose
- Runtime model: one Android app process; platform BLE, Wi-Fi scan, and DNS-SD
  callbacks feed a ViewModel; switch protocols are isolated behind an adapter
- Current confidence: manifests, dependencies, tests, lint, and debug assembly
  verified locally

## Components

| Layer | Technology | Evidence | Notes |
| --- | --- | --- | --- |
| Language/runtime | Kotlin 2.1.10, Java 17 bytecode | `build.gradle.kts`, `app/build.gradle.kts` | Android API 26+ |
| Frontend | Jetpack Compose BOM 2025.02.00, Material 3 | `app/build.gradle.kts`, `MainActivity.kt` | Russian prototype UI |
| Device discovery | Android BLE scan, `WifiManager` access-point scan, and `NsdManager` DNS-SD | `discovery/`, `onboarding/AndroidWifiNetworkScanner.kt` | BLE, selectable 2.4 GHz SSIDs, and local Wi-Fi services |
| Device onboarding/control | Smart Life SDK 7.8.0 Tuya adapter plus profile/provisioner/controller/remover boundaries and local HTTP controller | `tuya/`, `onboarding/`, `control/` | Tuya runtime requires app-specific security AAR and credentials |
| Data/storage | `StateFlow` UI state and SharedPreferences JSON device store | `OnboardingViewModel.kt`, `persistence/DeviceStore.kt` | Wi-Fi passwords are never persisted |
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
| Tuya Smart Life SDK | BLE discovery, combo activation, account/Home, DP control and device unbinding | `tuya/TuyaIntegration.kt`, `docs/tuya-setup.md` | App credentials, security component, registered SHA-256 and physical device are required |

## Gaps

- Select the first physical switch model and document its provisioning and
  command protocol.
- Verify BLE, Wi-Fi access-point scan, and DNS-SD behavior on a physical Android
  device.
- Replace prototype cleartext HTTP with an authenticated secure device contract
  before production use.

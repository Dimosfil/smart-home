# Agent Runbook

Every command should be copy-pasteable from the project root.

## Install

```powershell
.\gradlew.bat dependencies
```

## Run

```powershell
.\gradlew.bat installDebug
```

## Test

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

## Build

```powershell
.\gradlew.bat assembleDebug
```

## Smoke Check

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected result:

```text
Gradle reports `BUILD SUCCESSFUL` and creates
`app/build/outputs/apk/debug/app-debug.apk`.
```

## Logs

```powershell
adb logcat --pid=$(adb shell pidof com.dimosfil.smarthome)
```

## Environment Notes

- Requires JDK 17 and Android SDK Platform 35.
- `local.properties` contains the machine-local Android SDK path and is ignored.
- A physical Android device is required to verify BLE discovery reliably.
- The Wi-Fi prototype expects the HTTP contract documented in
  `tools/project-memory/specs/smart-switch-mvp.md`.

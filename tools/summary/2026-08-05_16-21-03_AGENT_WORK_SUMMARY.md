# Smart Home Android MVP — Agent Work Summary

Date: 2026-08-05

## Product Direction

- Build an Android application that discovers, provisions, and controls smart
  electronics, starting with a physical Tuya smart switch.
- Keep protocol-specific behavior behind adapters so Matter, Shelly, ESPHome,
  and other integrations can be added later.
- The MVP acceptance path is discovery, Wi-Fi provisioning, persisted device
  state, and a real on/off command with visible failures.

## Implemented Product Slice

- Eight-stage Compose workflow: device list, discovery, identification,
  preparation, Wi-Fi setup, provisioning progress, success, and control.
- Android BLE and DNS-SD discovery with runtime permissions and actionable
  errors.
- Smart Life App SDK 7.8.0 integration with the project-specific security AAR,
  local AppKey/AppSecret configuration, registered debug SHA-256 signature,
  automatic UID session, and Tuya Home creation/restoration.
- Tuya combo-device discovery and activation through `ScanDeviceBean` and
  `MultiModeActivatorBean`, including all mandatory scanned-device fields.
- Boolean power-DP discovery, state updates, and on/off publishing.
- Persistent device metadata and a generic local HTTP switch adapter retained
  as an independent integration example.

## Runtime Findings And Fixes

- A Huawei VOG-L29 was connected over ADB and the debug APK was installed.
- A repeatable activation crash was captured from Android's crash buffer. Tuya
  SDK dereferenced a missing `DeviceBean.uuid` because required values from the
  scan result were not explicitly copied into `MultiModeActivatorBean`.
- The activation request now copies UUID, device type, address, MAC, flag, and
  product ID, validates bound/missing-UUID states, and converts synchronous SDK
  exceptions into user-visible failures.
- The rebuilt app cold-started successfully on the phone, remained alive, and
  established its Tuya MQTT subscriptions with an empty crash buffer.

## Verification

- `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.
- Eleven unit tests pass with no failures.
- `TUYA_CONFIGURED` is enabled in the debug build.
- The APK signature matches the SHA-256 registered in Tuya Developer Platform.
- Secrets, the personalized security AAR, downloaded SDK archives, local JDK,
  and APK/build output are ignored and excluded from Git.

## Remaining Completion Evidence

- Repeat provisioning with the physical plug in pairing mode.
- Confirm the relay physically toggles from the app and the reported DP state
  follows the real device.
- Treat production signing, credential rotation, secure identity storage, and
  release hardening as later release work.

## Key Files

- Tuya adapter: `app/src/main/java/com/dimosfil/smarthome/tuya/TuyaIntegration.kt`
- Workflow coordinator: `app/src/main/java/com/dimosfil/smarthome/ui/OnboardingViewModel.kt`
- Compose UI: `app/src/main/java/com/dimosfil/smarthome/ui/SmartHomeApp.kt`
- Tuya setup guide: `docs/tuya-setup.md`
- Workflow contract: `tools/project-memory/specs/device-onboarding-workflow.md`

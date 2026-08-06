# Offline LAN architecture contract

Last reviewed: 2026-08-05

## Product decision

This contract defines the strict local-native reference profile inside the
larger multi-ecosystem product defined in `universal-device-platform.md`.
Internet is normally available to the product, but this reference profile must
support fresh provisioning, discovery, state reads, commands, and recovery with
router WAN and phone mobile data disabled. A vendor account or previously
cached cloud session must not be required for this profile.

The recommended first reference profile is a Shelly Gen2+/Gen3 plug with a
`Switch` component, preferably Shelly Plus Plug S (`SNPL-00112EU`) or the
appropriate regional Shelly Plug S Gen3 (`S3PL-00112EU`). Procurement and the
exact physical model remain unverified.

The current Tuya Smart Life adapter remains a first-class cloud-backed profile.
It is not evidence for this strict local-native acceptance because fresh
activation/binding requires vendor infrastructure. Verified Tuya models may be
classified separately as local-after-activation.

Human-facing research and official sources:
[`docs/offline-lan-device-research.md`](../../../docs/offline-lan-device-research.md).

## Required local flow

1. Factory-reset device exposes its local AP.
2. Android requests that AP with `WifiNetworkSpecifier` and binds provisioning
   traffic to the returned `Network`.
3. App uses Shelly `WiFi.Scan` and `WiFi.SetConfig`; credentials exist only in
   active-session memory.
4. Phone returns to the home Wi-Fi network.
5. App discovers `_shelly._tcp`/`_http._tcp`, validates generation and device
   information, and persists a stable device identity rather than an IP.
6. App reads `Switch.GetStatus`, calls `Switch.Set`, and verifies the resulting
   authoritative state with read-back.

## Architecture changes required

- Implemented: discovery entry is decoupled from Tuya session preparation, so
  an unavailable cloud profile does not block LAN adapters.
- Implemented: Shelly device/AP profiles, mDNS matcher, AP provisioner,
  JSON-RPC controller, command read-back, and endpoint rediscovery.
- Implemented: `_shelly._tcp.` NSD discovery; generic `_http._tcp.` remains an
  observation rather than proof of support.
- Keep the existing generic `/state` + `/switch` adapter as a prototype profile,
  not as the Shelly implementation.
- Implemented: provisioning sockets are routed through the Android `Network`
  selected for the device AP and callbacks are released on terminal paths.
- Treat IP addresses as replaceable endpoints. Persist device ID, generation,
  model, profile, room/name, capabilities, and protected auth metadata only.
- Add Android 17/target SDK 37 migration work for `ACCESS_LOCAL_NETWORK` before
  raising the target SDK.

The software path is not physical-device evidence. Procurement, real AP
commissioning, relay read-back, WAN-outage recovery, device authentication, and
firmware 2.0 HTTPS validation remain open acceptance items.

## Security invariants

- Never persist or log home Wi-Fi credentials.
- Set device authentication after commissioning when supported and store its
  secret through Android secure storage.
- Never install a trust-all TLS verifier. Firmware 2.0 HTTPS certificate trust
  and pinning/private-CA behavior must be proven before production acceptance.
- Cleartext HTTP, if used for the physical prototype, is limited to an isolated
  trusted LAN and remains an explicit production blocker.

## Acceptance contract

With WAN and mobile data disabled from the start, a clean app install and a
factory-reset reference device must:

1. provision onto a selected 2.4 GHz LAN;
2. be rediscovered and reopened without a cloud account;
3. expose connection and authoritative relay state;
4. execute a physical on/off action and confirm it by read-back;
5. surface wrong credentials, unreachable-device, permission, discovery,
   authentication/TLS, timeout, and acknowledgement failures;
6. survive app restart and IP address change without re-provisioning.

No physical product is declared supported until this test has captured device,
app, network, state-read, command, physical-relay, and failure evidence.

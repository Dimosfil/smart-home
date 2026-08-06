# Universal smart-home platform contract

Last reviewed: 2026-08-05

## Product goal

Smart Home is one user-facing Android application for devices from Smart Life /
Tuya, Shelly, Matter, ESPHome, and additional vendor ecosystems. The user must
not need to install each vendor's mobile application for normal operation after
a device is supported and onboarded through Smart Home.

Internet access is normally available but is not an operational guarantee. An
internet outage must not disable devices for which a documented local command
path exists. Cloud-only products remain usable through their vendor adapters
when online and must be labelled honestly when offline operation is impossible.

This goal does not promise protocol-level compatibility with every product sold
as a smart device. A product is supported only by an implemented and verified
profile.

## Capability classes

Every saved device has an explicit connectivity class:

1. `LOCAL_NATIVE`: provisioning and commands work directly over LAN/BLE/Matter.
2. `LOCAL_AFTER_ACTIVATION`: vendor cloud may be needed for initial activation,
   but cached credentials permit documented local commands during an outage.
3. `LOCAL_GATEWAY`: commands remain local through a supported Zigbee/Thread/BLE
   gateway or future Smart Home hub.
4. `CLOUD_ONLY`: the vendor exposes no supported local command path.

The UI shows `Работает локально`, `Локально после активации`, `Через локальный
хаб`, or `Требуется интернет`. It must not advertise generic offline support for
a brand when only selected models or firmware versions qualify.

## Runtime architecture

- A shared device registry owns rooms, names, capabilities, connection state,
  and the last authoritative state.
- Discovery, provisioning, control, removal, and event subscriptions are
  independent adapters selected by `DeviceProfile`.
- A command router prefers a verified local route, falls back to vendor cloud
  only when policy allows, and reports which route was used.
- Failure of Tuya login or any single ecosystem never blocks discovery or
  control through other adapters.
- Local credentials and vendor tokens are isolated per adapter and stored
  through Android secure storage.
- Automations declare where they execute: phone, device, vendor cloud, or local
  hub. The UI must disclose when an automation cannot run during an outage.

## Ecosystem roles

- Tuya / Smart Life: retain the current SDK adapter. Classify each verified
  device as local-after-activation or cloud-only; do not assume all Tuya models
  expose the same LAN behavior.
- Shelly: first `LOCAL_NATIVE` reference profile using AP provisioning, mDNS,
  and JSON-RPC.
- ESPHome: future `LOCAL_NATIVE` profile through Improv/native API.
- Matter: future local profile with a deliberate fabric ownership and
  commissioning design.
- Zigbee/Thread/BLE sub-devices: require a compatible radio gateway. A mobile
  app alone cannot replace the coordinator or border router.
- Home Assistant may be an optional integration, not a required user-installed
  application or the canonical product runtime.

## Availability boundary

An Android-only first release can control local devices while the phone is
present and running. Whole-home automations that must continue while the phone
is away, powered off, or killed require execution on the device itself or an
always-on local controller. If that requirement becomes part of the product,
Smart Home needs its own local hub/runtime or a supported existing gateway;
requiring Home Assistant is not implied.

## Delivery order

1. Implemented in software: keep the Tuya adapter; physical Tuya verification
   remains required.
2. Implemented: remove global Tuya-session gating from discovery and control.
3. Implemented in software: add Shelly as the first strict local reference
   profile; physical offline acceptance remains required.
4. Implemented: profile routing and connectivity-class badges. Physical outage
   tests remain required.
5. Add Matter and gateway-backed ecosystems according to verified demand.
6. Introduce an always-on Smart Home local runtime only when autonomous rules
   or non-Wi-Fi radios require it.

## Product acceptance

- One app displays devices from at least one cloud-backed and one local-native
  ecosystem in the same rooms and control model.
- Disconnecting WAN does not block the app shell or local discovery.
- Each local-capable reference device remains readable and controllable during
  the outage.
- Cloud-only devices show `Требуется интернет` instead of a generic offline or
  transport error.
- Restoring WAN reconnects cloud adapters without duplicating saved devices or
  losing local state.
- A failure in one adapter does not degrade other ecosystems.

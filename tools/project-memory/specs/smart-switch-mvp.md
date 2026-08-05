# Smart switch MVP contract

Last reviewed: 2026-08-05

## Product outcome

The Android application discovers nearby smart-device candidates over BLE and
local-network DNS-SD, lets the user select one, reads a compatible Wi-Fi
switch's current state, and sends an on/off command.

## Discovery workflow

1. The user grants Nearby devices permission for BLE scanning when desired.
2. The user starts discovery explicitly.
3. BLE advertisements and configured DNS-SD service types are collected.
4. Results identify their transport and available endpoint or signal strength.
5. Discovery stops when the user requests it or the ViewModel is cleared.

Wi-Fi discovery currently listens for `_smart-switch._tcp.`, `_http._tcp.`, and
`_esphomelib._tcp.` services. This does not guarantee discovery of devices that
use proprietary broadcast or cloud-only provisioning.

## Prototype Wi-Fi switch contract

- Base URL: the host and port resolved through DNS-SD.
- `GET /state`: returns `on`, `off`, `true`, `false`, `1`, `0`, or JSON with a
  boolean `on`, `enabled`, or `power` field.
- `POST /switch`: accepts `{"on": true}` or `{"on": false}`.
- A successful empty command response is interpreted as the requested state.
- Timeouts and non-2xx responses are visible to the user.

The paths and codec are isolated behind `SwitchController`; replace that
adapter for Matter, a vendor SDK, a different local API, authentication, or BLE
GATT characteristics.

## Safety and known gaps

- Cleartext HTTP is enabled only to support local prototype devices. Production
  use requires a device-scoped secure transport and authentication strategy.
- BLE control is not guessed from advertisements. A selected device model and
  its GATT/service contract are required before commands can be implemented.
- Physical-device and emulator verification remain required; unit tests cover
  only deterministic merging and response parsing.

# Smart Home

Android prototype for discovering and controlling smart electronic devices over
Bluetooth Low Energy and a local Wi-Fi network.

Technology stack: Kotlin, Jetpack Compose, Android BLE APIs, Android Network
Service Discovery (DNS-SD), and Gradle. See
`tools/project-memory/specs/technology-stack.md` for the verified inventory.

## Research and workflow contracts

- Device onboarding protocols and Smart Life/Tuya analysis:
  `docs/device-onboarding-protocols.md`
- Portable eight-stage onboarding contract:
  `tools/project-memory/specs/device-onboarding-workflow.md`
- Real Tuya SDK setup and phone verification:
  `docs/tuya-setup.md`

## Prototype goal

The first usable release should demonstrate one complete device workflow:

- connect or add at least one supported physical device;
- display connection status and current state;
- execute at least one meaningful control action;
- show understandable connection and command errors.

The first device category is a physical smart switch. Runtime discovery contains
only observations from Android BLE scanning and selected DNS-SD service types;
no simulated device is injected. A compatible Wi-Fi switch can be controlled
through the prototype HTTP contract documented in
`tools/project-memory/specs/smart-switch-mvp.md`.

## Current functionality

- runtime permission flow for Bluetooth discovery;
- BLE advertisement scanning with signal strength;
- Wi-Fi/mDNS discovery for smart-switch, HTTP, and ESPHome services;
- live 2.4 GHz Wi-Fi access-point scan with a selectable, refreshable SSID list;
- an empty discovery result at scan start, followed by live candidate updates;
- eight-stage device list, search, identification, installation, network,
  provisioning, success, and control workflow;
- profile/provisioner/controller adapter registries for real integrations;
- persistent device name, room, endpoint, connectivity, and last power state;
- state read and on/off commands for the prototype local HTTP adapter;
- confirmed device removal, including Tuya Home unbinding before local deletion;
- visible permission, discovery, transport, timeout, protocol, and HTTP errors.
- Smart Life App SDK 7.8.0 integration: automatic UID session/Home preparation, Tuya BLE scan,
  combo-device activation, DP power discovery, and on/off commands. A
  project-specific `security-algorithm.aar`, AppKey/AppSecret, registered
  signing certificate, and physical-device test are required to enable it.

BLE advertisements do not define a universal control protocol. Controlling a
physical BLE switch requires its model and GATT service/characteristic contract.

## Build and test

Requirements: JDK 17 and Android SDK Platform 35.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Исследования и контракты сценариев

- Анализ Smart Life/Tuya, Matter, BLE, Wi-Fi и gateway-протоколов:
  `docs/device-onboarding-protocols.md`
- Переносимый контракт восьмиэтапного подключения устройства:
  `tools/project-memory/specs/device-onboarding-workflow.md`

## Цель прототипа

Первый рабочий релиз должен показать полный сценарий управления устройством:

- подключить или добавить хотя бы одно поддерживаемое физическое устройство;
- показать статус подключения и текущее состояние;
- выполнить хотя бы одно полезное управляющее действие;
- понятно отображать ошибки подключения и выполнения команд.

Целевая платформа — Android, первая категория устройств — физический умный
выключатель. В runtime показываются только результаты системного BLE-поиска и
выбранные DNS-SD сервисы локальной сети; имитационные устройства не добавляются.
Совместимым Wi-Fi-выключателем можно управлять через прототипный HTTP-контракт,
описанный в
`tools/project-memory/specs/smart-switch-mvp.md`.

## Что уже реализовано

- запрос системного разрешения для Bluetooth-поиска;
- BLE-сканирование с отображением уровня сигнала;
- обнаружение Wi-Fi/mDNS сервисов умных выключателей, HTTP и ESPHome;
- актуальный обновляемый список доступных Wi-Fi сетей 2.4 GHz с выбором SSID;
- пустой список при старте поиска и добавление кандидатов по мере обнаружения;
- восемь экранов: список, поиск, найденное устройство, установка, сеть,
  подключение, успех и управление;
- реестр профилей и адаптеров для реальных интеграций;
- сохранение имени, комнаты, endpoint, статуса и последнего состояния питания;
- чтение состояния и команды включения/выключения через локальный HTTP-адаптер;
- удаление с подтверждением и отвязкой Tuya-устройства от Home;
- понятные ошибки разрешений, обнаружения, транспорта, таймаутов и HTTP.
- интеграция Smart Life App SDK 7.8.0: аккаунт и Home, Tuya BLE-поиск,
  активация combo-устройства, определение DP питания и команды ВКЛ/ВЫКЛ.
  Для включения требуются персональные `security-algorithm.aar`, AppKey/AppSecret,
  зарегистрированная подпись и проверка на физической розетке.

BLE-реклама не задаёт универсальный протокол управления. Для управления
физическим BLE-выключателем нужно знать его модель и GATT-сервисы/характеристики.

## Сборка и тесты

Требуются JDK 17 и Android SDK Platform 35.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

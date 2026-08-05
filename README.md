# Smart Home

Android prototype for discovering and controlling smart electronic devices over
Bluetooth Low Energy and a local Wi-Fi network.

Technology stack: Kotlin, Jetpack Compose, Android BLE APIs, Android Network
Service Discovery (DNS-SD), and Gradle. See
`tools/project-memory/specs/technology-stack.md` for the verified inventory.

## Prototype goal

The first usable release should demonstrate one complete device workflow:

- connect or add at least one supported physical device or emulator;
- display connection status and current state;
- execute at least one meaningful control action;
- show understandable connection and command errors.

The first device category is a smart switch. The app currently discovers BLE
advertisements and selected DNS-SD service types. A compatible Wi-Fi switch can
be controlled through the prototype HTTP contract documented in
`tools/project-memory/specs/smart-switch-mvp.md`.

## Current functionality

- runtime permission flow for Bluetooth discovery;
- BLE advertisement scanning with signal strength;
- Wi-Fi/mDNS discovery for smart-switch, HTTP, and ESPHome services;
- device selection and explicit discovery lifecycle;
- state read and on/off commands for the prototype local HTTP adapter;
- visible permission, discovery, transport, timeout, protocol, and HTTP errors.

BLE advertisements do not define a universal control protocol. Controlling a
physical BLE switch requires its model and GATT service/characteristic contract.

## Build and test

Requirements: JDK 17 and Android SDK Platform 35.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Цель прототипа

Первый рабочий релиз должен показать полный сценарий управления устройством:

- подключить или добавить хотя бы одно поддерживаемое физическое устройство
  либо эмулятор;
- показать статус подключения и текущее состояние;
- выполнить хотя бы одно полезное управляющее действие;
- понятно отображать ошибки подключения и выполнения команд.

Целевая платформа — Android, первая категория устройств — умный выключатель.
Приложение уже ищет BLE-рекламу и выбранные DNS-SD сервисы в локальной сети.
Совместимым Wi-Fi-выключателем можно управлять через прототипный HTTP-контракт,
описанный в `tools/project-memory/specs/smart-switch-mvp.md`.

## Что уже реализовано

- запрос системного разрешения для Bluetooth-поиска;
- BLE-сканирование с отображением уровня сигнала;
- обнаружение Wi-Fi/mDNS сервисов умных выключателей, HTTP и ESPHome;
- выбор устройства и управляемый жизненный цикл поиска;
- чтение состояния и команды включения/выключения через локальный HTTP-адаптер;
- понятные ошибки разрешений, обнаружения, транспорта, таймаутов и HTTP.

BLE-реклама не задаёт универсальный протокол управления. Для управления
физическим BLE-выключателем нужно знать его модель и GATT-сервисы/характеристики.

## Сборка и тесты

Требуются JDK 17 и Android SDK Platform 35.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

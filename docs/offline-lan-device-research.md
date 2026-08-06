# Работа Smart Home без интернета: исследование устройств и локальной сети

Дата: 2026-08-05

## Краткий вывод

Полностью локальный сценарий реализуем. Для первого физического MVP рекомендуется
розетка или реле Shelly поколения Gen2+/Gen3 с компонентом `Switch`: приложение
может подключить устройство к домашней Wi-Fi сети и затем читать состояние и
переключать реле напрямую по документированному JSON-RPC, без vendor cloud.

Практический референс для безопасной проверки без электромонтажа:

- Shelly Plus Plug S, модель `SNPL-00112EU`, Type F/E/C, до 12 A;
- либо Shelly Plug S Gen3, модель `S3PL-00112EU`, если она доступна в нужном
  исполнении розетки.

Перед покупкой нужно отдельно проверить наличие, тип вилки и региональную
версию. Устройство только с измерением мощности, например Shelly Plug PM Gen3,
не подходит: у него нет управляемого реле.

## Что означает «без интернета»

Приёмка выполняется в строгом режиме:

- WAN у роутера отключён до запуска сценария;
- мобильные данные телефона выключены;
- приложение установлено начисто и не имеет заранее закэшированной cloud-сессии;
- добавление устройства, обнаружение, чтение состояния и ВКЛ/ВЫКЛ проходят
  внутри локальной Wi-Fi сети;
- отказ внешнего DNS, vendor cloud или аккаунта не блокирует основной сценарий.

Android-разрешение `INTERNET` при этом остаётся необходимым: оно разрешает
сетевые сокеты, включая локальные, и само по себе не означает обращение к
внешнему интернету.

## Рекомендуемая архитектура: прямой Shelly JSON-RPC

Shelly Gen2+ предоставляет JSON-RPC 2.0 через HTTP и WebSocket. Для реле
документированы методы `Switch.GetStatus` и `Switch.Set`; пример локальной
команды: `GET /rpc/Switch.Set?id=0&on=true`. Устройства публикуют mDNS-сервисы
`_shelly._tcp` и `_http._tcp`, а TXT-запись сообщает поколение устройства.

Предлагаемый сценарий подключения:

1. Пользователь переводит устройство в заводской режим, и оно поднимает свою
   Wi-Fi точку доступа `Shelly...`.
2. Приложение через Android `WifiNetworkSpecifier` запрашивает подключение к
   этой локальной сети; пользователь подтверждает системный диалог.
3. Provisioning-запросы явно привязываются к полученному объекту `Network`,
   чтобы Android не отправил их через мобильную или домашнюю сеть.
4. Приложение вызывает `WiFi.Scan`, показывает найденные устройством сети
   2.4 GHz и передаёт выбранные SSID/пароль через `WiFi.SetConfig`.
5. Пароль хранится только в памяти активной операции. После ответа приложение
   освобождает запрос локальной сети и возвращается в домашнюю Wi-Fi сеть.
6. Устройство находится по `_shelly._tcp`/`_http._tcp`; профиль подтверждается
   через TXT и `Shelly.GetDeviceInfo`. IP-адрес не используется как постоянный
   идентификатор.
7. Начальное состояние читается через `Switch.GetStatus?id=0`.
8. ВКЛ/ВЫКЛ выполняется через `Switch.Set?id=0&on=...`.
9. Команда считается завершённой только после повторного чтения фактического
   состояния. WebSocket-уведомления можно добавить после MVP.

Этот путь хорошо ложится на существующие `DeviceProfile`, `DeviceProvisioner`
и `DeviceController`, но текущий generic HTTP-контракт `/state` и `/switch`
нужно заменить отдельным Shelly RPC-адаптером.

## Сравнение вариантов

| Вариант | Локальная работа | Подключение нового устройства без WAN | Цена интеграции | Решение |
| --- | --- | --- | --- | --- |
| Shelly Gen2+/Gen3 напрямую | HTTP/WS JSON-RPC, mDNS | Да, через локальную AP и `WiFi.SetConfig` | Низкая/средняя | Первый MVP |
| ESPHome + Improv | Локальный native API, BLE provisioning, mDNS | Да | Средняя/высокая для прямого Android-клиента | Лабораторная или вторая интеграция |
| Home Assistant как локальный hub | REST/WebSocket к одному локальному серверу | Зависит от интеграции устройства | Средняя; нужен постоянно работающий hub | Масштабирование на несколько брендов |
| Matter напрямую | Локальная защищённая fabric | Технически да | Высокая: commissioning, fabric, credentials | Долгосрочное направление |
| Текущий Tuya Smart Life SDK | Локальные команды возможны после активации | Нет для чистой установки: токен/привязка требуют WAN/cloud | Уже частично реализовано, но не соответствует ограничению | Оставить как необязательный cloud-профиль |

### ESPHome

ESPHome поддерживает BLE provisioning через Improv, fallback AP/captive portal,
mDNS и шифруемый native API. Athom Smart Plug EU V3 входит в каталог Made for
ESPHome и подходит как контролируемое тестовое устройство. Недостаток для этого
приложения — необходимость реализовать Android-клиент native API и управление
ключами шифрования либо поставить локальный Home Assistant.

### Home Assistant

Home Assistant удобен как локальный шлюз: приложение работает с одним REST или
WebSocket API, а интеграция Shelly общается с устройствами напрямую и не требует
Shelly Cloud. Компромисс — отдельный постоянно работающий сервер и управление
его токеном доступа; первичное подключение устройства оказывается вне или на
границе приложения.

### Matter

Matter даёт локальную IP-связь и fabric-модель доверия, однако приложение должно
решить, использует ли оно Google fabric/Google Home API или становится владельцем
собственной fabric. Для первого офлайн-MVP это несоразмерно сложнее прямого RPC.

### Tuya

Tuya документирует LAN control после активации и может предпочитать локальный
канал для команд. Но официальные сценарии cloud activation и LAN binding требуют
cloud-токен или WAN у роутера. Поэтому уже реализованный Tuya SDK нельзя считать
соответствующим строгой проверке «чистая установка + новое устройство + WAN
отключён». Недокументированные локальные ключи и reverse engineering в решение
не входят.

## Android-ограничения

- DNS-SD через `NsdManager` подходит для локального обнаружения.
- На Android 10+ `WifiNetworkSpecifier` создаёт локальное подключение после
  системного подтверждения; callback нужно освобождать после provisioning.
- На Android 13+ для Wi-Fi API нужен `NEARBY_WIFI_DEVICES` с совместимостью по
  location-разрешениям.
- При текущем target SDK 35 доступ к LAN покрывается разрешением `INTERNET`.
  Для Android 17 при target SDK 37+ потребуется `ACCESS_LOCAL_NETWORK` либо
  системный picker; для постоянного IoT-управления вероятнее нужен широкий
  доступ с отдельным runtime-согласием.
- Guest Wi-Fi, client isolation, VLAN или блокировка multicast могут скрыть
  устройство даже при успешном подключении к роутеру.

## Безопасность

- После commissioning устройству задаётся уникальный пароль, если модель это
  поддерживает; секрет хранится только в защищённом Android-хранилище.
- Shelly поддерживает HTTP Digest Authentication с SHA-256.
- В firmware 2.0 добавлен усиленный HTTPS-режим. Для новых устройств и разных
  путей обновления поведение сертификатов отличается, поэтому Android-доверие,
  pinning/частный CA и миграция HTTP -> HTTPS требуют отдельного технического
  spike.
- Нельзя отключать проверку сертификата глобальным trust-all решением.
- Временный cleartext HTTP допустим только как явно обозначенное ограничение
  прототипа в изолированной доверенной сети, не как production-контракт.

## Обязательные ошибки в интерфейсе

- отказ в Nearby Wi-Fi или Local Network permission;
- пользователь не подтвердил подключение к AP устройства;
- AP исчезла или provisioning-соединение оборвалось;
- неверный пароль/неподдерживаемая сеть;
- устройство покинуло AP, но не появилось в домашней LAN;
- mDNS заблокирован, guest isolation или другая подсеть;
- IP изменился, endpoint устарел;
- `401`/ошибка аутентификации;
- ошибка TLS или несовпадение сертификата;
- у найденной модели нет компонента `Switch`;
- таймаут, отклонённая команда или несовпадение read-back с запрошенным
  состоянием.

## Критерий физической приёмки

На чистой установке приложения и factory-reset устройстве, при отключённых WAN
и мобильных данных:

1. приложение подключает устройство к выбранной 2.4 GHz Wi-Fi сети;
2. повторно находит его после смены IP/возврата телефона в домашнюю сеть;
3. показывает online/offline и авторитетное состояние реле;
4. физически переключает нагрузку ВКЛ/ВЫКЛ и подтверждает результат read-back;
5. понятно показывает минимум: неверный пароль, недоступное устройство и
   неуспешную команду;
6. перезапуск приложения не требует внешнего аккаунта или WAN.

## Официальные источники

- Shelly: [Switch component](https://shelly-api-docs.shelly.cloud/gen2/ComponentsAndServices/Switch/),
  [RPC over HTTP/WS](https://shelly-api-docs.shelly.cloud/gen2/General/RPCChannels/),
  [Wi-Fi configuration](https://shelly-api-docs.shelly.cloud/gen2/ComponentsAndServices/WiFi/),
  [mDNS](https://shelly-api-docs.shelly.cloud/gen2/General/mDNS/),
  [authentication](https://shelly-api-docs.shelly.cloud/gen2/General/Authentication/),
  [HTTPS certificates](https://shelly-api-docs.shelly.cloud/gen2/General/CustomHTTPSCertificates/).
- Устройства Shelly: [Plus Plug S](https://www.shelly.com/blogs/documentation/shelly-plus-plug-s),
  [Plug S Gen3](https://www.shelly.com/products/shelly-plug-s-gen3),
  [Plug PM Gen3](https://www.shelly.com/products/shelly-plug-pm-gen3-white).
- ESPHome: [Improv BLE](https://esphome.io/components/esp32_improv/),
  [Wi-Fi](https://esphome.io/components/wifi/),
  [native API](https://esphome.io/components/api/),
  [Athom Smart Plug EU V3](https://devices.esphome.io/devices/athom-smart-plug-pg01v3-eu16a/).
- Home Assistant: [Shelly integration](https://www.home-assistant.io/integrations/shelly/),
  [REST API](https://developers.home-assistant.io/docs/api/rest/),
  [WebSocket API](https://developers.home-assistant.io/docs/api/websocket/).
- Matter: [commissioning on Android](https://developers.home.google.com/apis/android/commissioning),
  [discovery](https://developers.home.google.com/matter/primer/commissionable-and-operational-discovery),
  [fabric](https://developers.home.google.com/matter/primer/fabric).
- Tuya: [LAN device control](https://developer.tuya.com/en/docs/iot-device-dev/TuyaOS-iot_abi_lan_dev_ctrl?id=Kcogloltxvej1),
  [Wi-Fi activation](https://developer.tuya.com/en/docs/app-development/active_wifi_channel?id=Kdwxq4a3u289h),
  [LAN binding prerequisites](https://developer.tuya.com/en/docs/iot-device-dev/tuyaos-package-ipc-device?id=Kcv9vowt1w3rv).
- Android: [NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd),
  [Wi-Fi bootstrap](https://developer.android.com/develop/connectivity/wifi/wifi-bootstrap),
  [Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions),
  [local network permission](https://developer.android.com/privacy-and-security/local-network-permission).

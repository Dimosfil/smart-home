# Подключение умных устройств: протоколы, архитектура и ограничения

Актуальность исследования: 2026-08-05.

## Краткий вывод

Приложения уровня Smart Life не распознают произвольное устройство только по
факту наличия Bluetooth или Wi-Fi. Они поддерживают большое количество заранее
известных продуктов благодаря общей экосистеме:

- совместимому модулю и прошивке внутри устройства;
- зарегистрированному Product ID и уникальным реквизитам устройства;
- известному формату BLE-рекламы, Wi-Fi provisioning или gateway pairing;
- облачному сервису активации и привязки устройства к аккаунту;
- модели возможностей устройства;
- готовым или динамически загружаемым панелям управления.

Поэтому «найти любое устройство» на практике означает «параллельно искать
устройства всех поддерживаемых экосистемой типов». Неизвестному устройству всё
равно требуется отдельный профиль обнаружения, provisioning и управления.

## Как работает Smart Life

Типовой Tuya/Smart Life поток выглядит так:

1. Производитель создаёт продукт в Tuya Developer Platform.
2. Устройство получает Tuya-модуль или TuyaOS, Product ID и индивидуальные
   реквизиты во время производства.
3. Пользователь переводит устройство в режим сопряжения.
4. Устройство рекламирует поддерживаемый способ подключения: BLE, Wi-Fi AP,
   EZ/SmartConfig, Matter, gateway/sub-device или другой Tuya activator.
5. Приложение распознаёт формат и Product ID либо использует выбранную вручную
   категорию продукта.
6. Приложение получает из Tuya Cloud временный activation token.
7. SSID, пароль и token передаются устройству через поддерживаемый канал.
8. Устройство подключается к роутеру или gateway, активируется в облаке и
   привязывается к аккаунту, дому и комнате.
9. Приложение получает Data Point schema и панель продукта.
10. Команды передаются через локальную сеть, Bluetooth или облачные каналы, а
    фактическое состояние подтверждается обратным сообщением устройства.

Официальный [Smart Life Quick Start](https://developer.tuya.com/en/docs/iot/quick-start?id=Kaytf7h5yhp8y)
показывает автоматический поиск Wi-Fi+BLE combo устройств. Для Wi-Fi устройств
без BLE пользователь выбирает категорию, после чего запускается EZ или AP
pairing.

Tuya [Composite Scan](https://developer.tuya.com/en/docs/app-development/extension-activator-mult-scan?id=Kcy38exgyp6om)
объединяет восемь классов pairing: Wi-Fi EZ, password-free, Pegasus, wired
gateway, sub-device, gateway router, Bluetooth и Matter. Некоторые из этих
режимов не выполняют физическое сканирование, а представляют собой параллельно
запущенный процесс активации.

## Протоколы обнаружения и подключения

| Механизм | Обнаружение | Передача параметров сети | Последующее управление | Основное ограничение |
| --- | --- | --- | --- | --- |
| Wi-Fi + BLE combo | BLE advertisement с UUID, Product ID и capability | BLE GATT через vendor SDK | LAN, Bluetooth или cloud | Требуются совместимая прошивка и SDK |
| Wi-Fi AP / SoftAP | SSID точки доступа устройства | Локальный socket/HTTP/vendor protocol | Локальная сеть или cloud | Телефон временно переключается на сеть устройства |
| Wi-Fi EZ / SmartConfig | Физического ответа до активации может не быть | Закодированный UDP multicast/broadcast | Локальная сеть или cloud | Зависит от роутера, радиоэфира и firmware |
| Direct BLE | BLE advertisement | Wi-Fi не требуется либо credentials передаются через GATT | GATT read/write/notify | Нет универсального формата пользовательских характеристик |
| Bluetooth Mesh | BLE mesh provisioning | Через mesh provisioner | Телефон или Bluetooth gateway | Tuya Mesh и SIG Mesh — разные контракты |
| Zigbee | Gateway открывает join window | Конечному устройству Wi-Fi не передаётся | Zigbee clusters через gateway | Телефон обычно не является Zigbee coordinator |
| Matter | BLE, SoftAP или DNS-SD, плюс QR/manual code | Стандартный Matter network provisioning | Matter clusters по защищённой fabric | Только Matter-совместимые устройства |
| Локальный IP | mDNS/Zeroconf, SSDP/UPnP, DHCP или QR | Устройство уже в сети либо использует отдельный onboarding | Vendor HTTP, JSON-RPC, WebSocket, MQTT или API | Discovery не определяет протокол управления |
| QR/camera | QR устройства или QR на экране телефона | Данные закодированы в QR | Обычно cloud/vendor API | Формат и binding принадлежат экосистеме |
| Wired/NB-IoT/gateway | LAN/cloud registration | Wi-Fi может отсутствовать | Cloud или gateway | Нужен vendor binding contract |

### Wi-Fi + BLE combo

Это основной источник эффекта «устройство появилось само». BLE advertisement
содержит достаточно признаков, чтобы SDK выбрал подходящий activator. После
соединения приложение передаёт SSID, пароль и activation token. Современный
Tuya SDK может попросить само устройство просканировать доступные ему Wi-Fi
сети, что позволяет отфильтровать неподдерживаемые 5 GHz сети. Подробности:
[Tuya Android BLE pairing](https://developer.tuya.com/en/docs/app-development/android-bluetooth-ble?id=Karv7r2ju4c21).

### Wi-Fi AP / SoftAP

Неподключённое устройство создаёт собственную точку доступа. Телефон временно
подключается к ней и отправляет SSID, пароль домашней сети и token. После этого
устройство отключает AP, подключается к роутеру и активируется в облаке.
Официальный контракт: [Tuya AP Pairing](https://developer.tuya.com/en/docs/iot-device-dev/TuyaOS-iot_abi_network_config_AP?id=Kc67sz8ud0obw).

### Wi-Fi EZ / SmartConfig

Телефон кодирует SSID, пароль и token в свойства UDP broadcast/multicast
пакетов. Устройство слушает Wi-Fi в promiscuous/sniffer mode и восстанавливает
параметры сети. Метод удобен, но менее надёжен из-за несовместимости роутеров,
фильтрации multicast и радиопомех. Официальное описание:
[Tuya EZ Mode](https://developer.tuya.com/en/docs/iot-device-dev/integrated_sdk_ez_commissioning_guide?id=Kb9p8i00u3p6v).

### Direct BLE и GATT

Bluetooth LE стандартизирует advertising, соединение и модель GATT из services,
characteristics и descriptors. Смысл пользовательских характеристик может быть
vendor-specific. Наличие BLE-имени и MAC-адреса не сообщает приложению, куда и
в каком формате писать Wi-Fi credentials или команду питания. Для произвольного
BLE-устройства необходима спецификация UUID, framing, security, write/read и
notification поведения. Базовая модель описана в
[Bluetooth LE Primer](https://www.bluetooth.com/bluetooth-le-primer/).

### Zigbee и gateway

Телефон обычно не имеет Zigbee-радио и не подключает конечный Zigbee-девайс
напрямую. Сначала активируется gateway/coordinator, затем gateway открывает
join window и принимает sub-device. Приложение управляет устройством через
gateway и облачную модель продукта. Пример официального Tuya потока:
[Wireless Zigbee Gateway](https://developer.tuya.com/en/docs/iot/Zigbee_Wi-Fi_gateway?id=Kbg4vdcz4bbpr).

### Matter

Matter стандартизирует значительную часть межвендорного onboarding:

- QR или manual setup code;
- Passcode Authenticated Session Establishment;
- device attestation;
- выдачу fabric credentials;
- Wi-Fi или Thread network provisioning;
- operational discovery через DNS-SD;
- защищённые CASE-сессии;
- endpoints, clusters, attributes и commands.

См. [Matter commissioning](https://developers.home.google.com/matter/primer/commissioning)
и [Matter discovery](https://developers.home.google.com/matter/primer/commissionable-and-operational-discovery?hl=en).
На Android commissioning можно делегировать Google Play services через
[Google Home Commissioning API](https://developers.home.google.com/apis/android/commissioning)
либо вести собственную Matter fabric.

## Модель управления Tuya

Tuya абстрагирует функции продукта через Data Points (DP):

- Boolean — питание и другие флаги;
- Value — температура, яркость, мощность;
- Enum — режим работы;
- Bitmap — набор ошибок;
- String и Raw — сложные или vendor-specific данные.

DP schema определяет доступные функции, типы данных и направления обмена. Это
позволяет одному приложению строить разные панели и управлять разными классами
устройств. Официальная модель: [Tuya DP Model](https://developer.tuya.com/en/docs/iot-device-dev/TuyaOS-iot_abi_dp_ctrl?id=Kcoglhn5r7ajr).

На Android команда отправляется через `publishDps`. SDK может выбрать локальную
сеть, MQTT или HTTPS. Успешная отправка команды ещё не подтверждает физическое
действие: итоговое состояние должно прийти через DP update. См.
[Tuya Device Control](https://developer.tuya.com/en/docs/app-development/andoird_device_control?id=Kaixh4pfm8f0y).

## Ограничения Tuya/Smart Life

- Поддерживается широкая, но конечная экосистема Tuya, а не любой BLE/Wi-Fi
  продукт.
- Устройство может иметь strong binding и требовать удаления из предыдущего
  аккаунта.
- Некоторые продукты разрешено активировать только из определённых приложений.
- Custom app зависит от Tuya Cloud, региона, SDK lifecycle и политики данных.
- Закрытые Tuya BLE/AP/EZ контракты следует использовать через официальный SDK,
  а не воспроизводить по наблюдаемому трафику.

Binding и app restriction описаны в
[Tuya Device Pairing BizBundle](https://developer.tuya.com/en/docs/app-development/extension-sdk-tutorial-deviceconfig?id=Kd8k3w5na6q73).

## Другие архитектуры приложений

### Home Assistant

Home Assistant не применяет один универсальный протокол. Он маршрутизирует
результаты Bluetooth, Zeroconf/mDNS, SSDP, DHCP, HomeKit, MQTT и USB discovery в
конкретную integration. Integration содержит matcher, unique ID, config flow и
контроллер продукта. Даже общий `_http._tcp` должен фильтроваться по имени или
properties. См. [Home Assistant integration manifests](https://developers.home-assistant.io/docs/creating_integration_manifest/).

### ESPHome / Improv

ESPHome поддерживает открытый Improv BLE для передачи Wi-Fi credentials на
ESP32. Он предоставляет явные состояния authorization, provisioning,
provisioned и ошибки. Это удобная основа для полностью контролируемого
физического тестового устройства. См. [ESPHome Improv via BLE](https://esphome.io/components/esp32_improv/).

### Shelly

Shelly публикует локальный JSON-RPC и документирует Wi-Fi scan, configuration и
status. Устройство может быть настроено через локальную AP/BLE поверхность, а
затем управляться без обязательного vendor cloud. См.
[Shelly Wi-Fi API](https://shelly-api-docs.shelly.cloud/gen2/ComponentsAndServices/WiFi/)
и [Shelly RPC Protocol](https://shelly-api-docs.shelly.cloud/gen2/General/RPCProtocol/).

## Варианты развития Smart Home

### Tuya-first

Наиболее близкий к представленным скриншотам путь — интегрировать официальный
SmartLife App SDK и Device Pairing BizBundle.

Потребуются:

- Tuya Developer account и identity verification;
- SDK App с уникальным Android package name;
- SHA-256 сертификатов debug и release;
- `AppKey`, `AppSecret` и Tuya security component;
- модули account, home/room, pairing и device control;
- privacy disclosure и обработка регионов;
- хотя бы одно физическое Tuya-устройство для проверки binding и app
  restriction.

Официальная подготовка описана в
[SmartLife App SDK Preparation](https://developer.tuya.com/en/docs/app-development/preparation?id=Ka69nt983bhh5).

Плюс: самый широкий путь к Smart Life/Tuya устройствам и готовым pairing
сценариям. Минус: зависимость от Tuya Cloud и ограничения конкретного продукта.

### Matter-first

Приложение поддерживает только Matter-девайсы, но получает стандартный
commissioning и стандартные control clusters. Это межвендорный путь без попытки
поддержать несовместимые проприетарные продукты.

Плюс: стандартный контракт и переносимость. Минус: существующие Tuya, Zigbee,
BLE и Wi-Fi устройства без Matter не становятся совместимыми автоматически.

### Реестр адаптеров

Полноценное мультиэкосистемное приложение должно иметь реестр профилей:

```text
DeviceProfile
├── DiscoveryMatcher
├── Provisioner
├── Controller
├── CapabilitySchema
└── ControlPresentation
```

Каждая новая экосистема добавляет свой matcher, provisioner и controller, а
общий UI использует унифицированные состояния и capability model.

### Полностью локальный MVP

Для контролируемого end-to-end прототипа можно использовать физическое
ESPHome/Improv- или Shelly-устройство с документированным локальным протоколом.
Это обеспечивает проверяемый onboarding и управление без недоказанных
предположений о совместимости случайного потребительского устройства.

## Требования Android

Проект использует `targetSdk 35`. Для приложений, которые управляют Wi-Fi
подключениями на Android 13+, необходимо runtime-разрешение
`NEARBY_WIFI_DEVICES`; для части старых API и Android 12L и ниже сохраняется
совместимость через location permission. См.
[Android Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions).

Также необходимы:

- Bluetooth scan/connect permissions;
- Wi-Fi state и local-network lifecycle;
- безопасная отмена BLE/AP операций при уходе экрана в background;
- таймауты и повторное подключение;
- запрет постоянного хранения Wi-Fi-пароля после provisioning;
- защищённое хранение device tokens и account session;
- отдельные debug/release credentials внешних SDK вне Git.

## Текущее состояние проекта

| Возможность | Состояние |
| --- | --- |
| Список обнаруженных устройств | Есть, но без постоянного registry и комнат |
| BLE discovery | Есть, без Tuya/Matter/Product ID parser |
| mDNS discovery | Есть для нескольких service types |
| Device selection | Есть |
| Wi-Fi provisioning | Нет |
| Cloud activation/binding | Нет |
| Success/rename/room flow | Нет |
| Управление | Есть только прототипный локальный HTTP adapter |
| Физическая end-to-end проверка | Не выполнена |

Следующий архитектурный выбор — определить первую поддерживаемую экосистему.
До его принятия можно реализовать общий UI и state machine, но нельзя добавлять
имитационные устройства в runtime или считать реальный provisioning
подтверждённым.

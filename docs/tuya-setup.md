# Настройка реального Tuya-подключения

Актуально для Smart Life App SDK for Android 7.8.0 и package name
`com.dimosfil.smarthome`.

## Что уже реализовано в приложении

- инициализация Smart Life App SDK;
- автоматический вход или создание технического аккаунта по локальному UID;
- получение или автоматическое создание Tuya Home;
- BLE-сканирование Tuya-устройств через `ScanDeviceBean`;
- Wi-Fi + BLE combo activation через `MultiModeActivatorBean`;
- сохранение полученного Tuya `deviceId`;
- определение Boolean DP питания (`switch_1`, `switch`, `switch_led`, `power`
  или первый доступный Boolean DP);
- чтение состояния и команда ВКЛ/ВЫКЛ через `IThingDevice.publishDps`;
- отвязка устройства от Tuya Home через `IThingDevice.removeDevice` с
  сохранением локальной записи при ошибке облачного удаления.

## Однократная настройка Tuya Developer Platform

1. Создать SmartLife App SDK application.
2. Указать Android package name `com.dimosfil.smarthome`.
3. Собрать Android SDK 7.x и скачать персональный security component.
4. Скопировать полученный файл в
   `app/libs/security-algorithm.aar`. Этот файл персональный и игнорируется Git.
5. Добавить debug SHA-256 сертификата приложения в настройках Tuya SDK app:
   `86:26:35:8B:7A:7F:BC:16:04:8A:C8:6F:20:EA:AC:64:7C:2C:23:02:92:5D:55:7F:86:6E:02:A6:8C:C9:6E:90`.
6. В локальный игнорируемый `local.properties` добавить:

```properties
thing.appKey=YOUR_APP_KEY
thing.appSecret=YOUR_APP_SECRET
```

Ключи, AAR и пароли пользователей нельзя добавлять в Git или документацию.

## Проверка на телефоне

1. Пересобрать и установить debug APK.
2. Открыть приложение и дождаться автоматического подключения Tuya и создания
   Home. UID и случайный пароль технического аккаунта сохраняются только в
   приватных настройках приложения.
3. Перевести розетку в режим сопряжения. Если она уже привязана в Smart Life,
   сначала удалить её там или выполнить factory reset.
4. Нажать «Добавить устройство», выбрать найденный `Powered by Tuya` девайс,
   выбрать сеть 2.4 GHz и ввести пароль.
5. После успешной активации открыть устройство и проверить ВКЛ/ВЫКЛ.
6. Проверить «Удалить устройство»: подтвердить действие и убедиться, что после
   успешной отвязки устройство исчезло из списка. Для офлайн-устройства может
   потребоваться ручной factory reset перед повторным сопряжением.

Успех считается подтверждённым только после физического переключения реле и
обновления состояния в приложении.

## Официальные источники

- [Fast Integration with SmartLife App SDK for Android](https://developer.tuya.com/en/docs/app-development/integrated?id=Ka69nt96cw0uj)
- [Bluetooth LE Devices](https://developer.tuya.com/en/docs/app-development/android-bluetooth-ble?id=Karv7r2ju4c21)
- [Login with UID](https://developer.tuya.com/en/docs/app-development/useruid?id=Ka6a99lybyr0k)
- [Device Management](https://developer.tuya.com/en/docs/app-development/devicemanage?id=Ka6ki8r2rfiuu)

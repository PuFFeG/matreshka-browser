Matreshka Browser 0.2.1 для ARM64.

**Коротко:** Glean-телеметрия Mozilla отключена, сторонние Sentry, Adjust и Firebase удалены. Браузер не отправляет статистику просмотра; crash-отчёт Mozilla — только по кнопке.

## Идентификация APK

- Package: `ru.matreshka.browser`
- Application label: `matreshka-browser`
- versionName: `0.2.1`
- versionCode: `2016191883`
- ABI: `arm64-v8a`
- Android: 8.0+ (`minSdk 26`)
- База: Firefox/Fenix `160.0a1` (BuildID `20261022001515`)

## Что изменилось

- Удалены Sentry, Adjust и Firebase Messaging: зависимости, manifest-компоненты и startup-вызовы.
- Leanplum и Crashlytics в базе Fenix 160.0a1 отсутствуют.
- Загрузка Glean принудительно отключена; все пинги отбрасываются до сети.
- Удалены Glean-метрики, usage-reporting и crash-telemetry сервисы из запуска.
- `BuildConfig.TELEMETRY=false`, телеметрия по умолчанию выключена.
- Изменения разделены на `patches/fenix/0001-remove-third-party-telemetry.patch` и
  `patches/fenix/0002-disable-glean-telemetry.patch`.
- Версия и versionCode подняты так, чтобы обновляться поверх 0.2.0 и v160.0a1.

## Проверка

```sh
./scripts/verify-apk.sh matreshka-browser-0.2.1-arm64-v8a-release.apk
apksigner verify --print-certs matreshka-browser-0.2.1-arm64-v8a-release.apk
sha256sum -c SHA256SUMS
```

Сертификат: `CN=Matreshka, O=Matreshka, C=RU`
SHA-256 сертификата: `D697BD505F67DA0B36B46F15583CCD851FB0153BB1C15986F529B7B7209C655C`

Socorro crash reporting и Nimbus/Remote Settings Mozilla остаются.

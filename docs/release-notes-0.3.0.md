Matreshka Browser 0.3.0 для ARM64.

**Коротко:** база обновлена до Firefox 160.0.1 со всеми security-фиксами ветки 160 и Android-фиксом из 160.0.1. Glean-телеметрия Mozilla отключена, сторонние Sentry и Adjust удалены. Браузер не отправляет статистику просмотра; crash-отчёт Mozilla — только по кнопке.

## Идентификация APK

- Package: `ru.matreshka.browser`
- Application label: `matreshka-browser`
- versionName: `0.3.0`
- versionCode: `2016197402`
- ABI: `arm64-v8a`
- Android: 8.0+ (`minSdk 26`)
- База: Firefox/Fenix `160.0.1` (BuildID `20261120140537`)

## Что изменилось

- База обновлена с `160.0a1` (снапшот 22.10) до стабильного `160.0.1` (17.11).
- Включены security-исправления Firefox 159, 160 и 160.0.1: среди них sandbox escape, use-after-free в DOM/HTML parser/Audio-Video и privilege escalation в CanvasWebGL на Android (`CVE-2026-130020`).
- Патч телеметрии перенесён на новую базу: удалена регистрация Nimbus-пингов Glean (`NimbusPings.nimbusTargetingContext`), которая появилась в 160.0.1.
- Удалены Sentry, Adjust и Firebase Messaging; загрузка Glean принудительно отключена, телеметрия по умолчанию выключена.
- `BuildConfig.TELEMETRY=false`; Glean-метрики, usage-reporting и crash-telemetry сервисы не запускаются.
- Trust-конфигурация не менялась: те же 2 корня и 70 записей хостов, `updatedAt 2026-10-22`.

## Проверка

```sh
./scripts/verify-apk.sh matreshka-browser-0.3.0-arm64-v8a-release.apk
apksigner verify --print-certs matreshka-browser-0.3.0-arm64-v8a-release.apk
sha256sum -c SHA256SUMS
```

Сертификат: `CN=Matreshka, O=Matreshka, C=RU`
SHA-256 отпечаток сертификата подписи: `D697BD505F67DA0B36B46F15583CCD851FB0153BB1C15986F529B7B7209C655C`

Socorro crash reporting и Nimbus/Remote Settings Mozilla остаются.

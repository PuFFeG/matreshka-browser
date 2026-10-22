<div align="center">
  <img src="docs/images/hero.png" alt="Коробка с надписями «СЕРТИФИКАТЫ» и «ДОВЕРЕННЫЕ СЕРТИФИКАТЫ», из которой выглядывает одна большая матрёшка" width="900">
  <h1><code>matre$ka</code></h1>
  <p><strong>Android-браузер на полной базе Firefox, который доверяет сертификатам Минцифры только там, где это явно разрешено.</strong></p>
  <p>Fenix/Firefox 160.0a1 · ARM64 · отдельный контекст хранения для российских сайтов · открытый патч-стек.</p>
  <p>
    <img src="https://img.shields.io/badge/Android-8%2B%20%C2%B7%20minSdk%2026-3DDC84?style=for-the-badge&amp;logo=android&amp;logoColor=white" alt="Android 8+ · minSdk 26">
    <img src="https://img.shields.io/badge/ABI-arm64--v8a-f5a524?style=for-the-badge" alt="ARM64">
    <img src="https://img.shields.io/badge/Firefox-160.0a1-FF7139?style=for-the-badge" alt="Firefox 160 base">
    <img src="https://img.shields.io/badge/License-MPL--2.0-4C9AFF?style=for-the-badge" alt="MPL-2.0">
  </p>
</div>

> **Независимый форк.** `matre$ka` — общественный проект, не связанный с Mozilla и не одобренный ею. Изображения на этой странице — AI-иллюстрации, а не скриншоты приложения.

---

## Зачем

`matre$ka` решает четыре практические задачи, не притворяясь браузером, написанным с нуля:

- **Сайты на цепочках Russian Trusted.** Обычный Firefox не доверяет сертификатам Минцифры. Здесь доверие включается точечно — только для хостов из списка.
- **Список не может быть вечным.** Для `.ru`/`.рф` есть fallback, но он принимает только цепочки к двум встроенным корням. Домен вне списка получает янтарное предупреждение.
- **Данные не должны смешиваться.** Вкладки на российских хостах живут в отдельном `contextId=matreshka`, а не в общем cookie jar.
- **Нужен полноценный браузер.** В основе — Fenix: поиск, настройки, DoH, QUIC, расширения и sync, а не минимальное демо.

## Что нового

| <img src="docs/images/feature-trust.png" width="250" alt="Иллюстрация списка доверия"><br>**Доверие по списку** | <img src="docs/images/feature-warning.png" width="250" alt="Иллюстрация предупреждения"><br>**Янтарное предупреждение** | <img src="docs/images/feature-isolation.png" width="250" alt="Иллюстрация изоляции данных"><br>**Изоляция хранения** |
| :---: | :---: | :---: |
| 70 записей хостов и 2 российских корня, обновление от 2026-10-22. Для доменов вне списка работает `.ru`/`.рф` fallback, но только если цепочка сходится к встроенному корню. | Если сертификат Russian Trusted выпущен для домена вне списка, список устарел или соединение не защищено, иконка сайта становится янтарным щитом. Подпись: `Сертификат Минцифры: домен вне списка`. Тап открывает объяснение и переход к списку. | Новые вкладки на российских хостах получают `contextId=matreshka`: отдельные cookies, localStorage, IndexedDB, service workers и cache. |

| **Редактируемый список** | **Полноценная база Firefox** |
| --- | --- |
| Список можно дополнять вручную, удалять хосты, импортировать по HTTPS и очищать изолированные данные. Источники: APK-ассет, удалённый список, пользовательские записи. Корни остаются только в APK. | Поиск и выбор движка, настройки, HTTPS-Only, видимый DNS-over-HTTPS, QUIC/HTTP3 включён по умолчанию, расширения, история, закладки, вкладки, collections и sync. Автоматический переводной pop-up по умолчанию выключен. |
| **Воспроизводимый патч-стек** | **ARM64 и Android 8+** |
| Ядро — один патч `patches/core/matreshka-trust.patch`, сверху — файловый overlay Fenix. `scripts/apply.sh` идемпотентен, а `git apply --check` ловит несовпадение с базой. | Релиз собран под `arm64-v8a`, `minSdk 26` (Android 8.0+), package `ru.matreshka.browser`. Debug-сборка использует отдельный `ru.matreshka.browser.debug`. |

## Как это работает

Слой Matreshka не подменяет TLS и не ставит свой прокси. Он добавляет в проверку сертификатов host-scoped набор доверенных корней и отдельно помечает вкладки, которым нужен изолированный storage.

[Открыть схему в полном размере](docs/architecture.svg)

<img src="docs/architecture.svg" alt="Схема архитектуры matre$ka" width="100%">

1. **Fenix UI** остаётся обычным интерфейсом Firefox: адресная строка, поиск, настройки, расширения.
2. **`BrowserToolbarMiddleware` / `TrustWarning`** оценивают выбранную вкладку и показывают янтарный щит, если домен вне списка или данные устарели.
3. **`TrustRepository` / `TrustConfig`** собирают эффективный список из APK-ассета, удалённого HTTPS-списка и пользовательских записей, нормализуют IDN и отдают компактный bundle.
4. **`MatreshkaTrustBridge`** передаёт bundle в `GeckoRuntimeSettings` через reflection. Стоковый GeckoView от этого не ломается, а пропатченный — получает список и корни.
5. **`MatreshkaStorageMiddleware`** до `EngineMiddleware` назначает `contextId=matreshka` новым вкладкам, чей стартовый URL относится к российскому trust-слою.

<details>
<summary>Технические детали: bundle, prefs и reflection</summary>

- Bundle — компактный текст: первая строка со списком хостов через запятую, далее по одной base64-encoded DER-сертификату.
- В ядре добавлены prefs `matreshka.trust.enabled`, `matreshka.trust.ru_fallback` и `matreshka.trust.json`.
- `matreshka.trust.ru_fallback` разрешает `.ru`/`.рф` хостам использовать встроенные корни, но цепочка всё равно должна валидироваться до одного из них. Произвольные сертификаты не принимаются.
- `GetCertTrust()` повышает bundled-сертификат до trust anchor только для CA и только при точном совпадении DER.
- Изменение `matreshka.trust.json` переразбирается C++-кодом на лету, без пересборки GeckoView.
- `TrustRepository` считает список устаревшим через 60 дней после `updatedAt`, а sub CA — близким к истечению за 90 дней до `notAfter`.
</details>

## Изоляция данных

| Что | Обычный контекст | `contextId=matreshka` |
| --- | --- | --- |
| Cookie jar | общий | тот же `cookies.sqlite`, но строки с отдельным `originAttributes` |
| localStorage / IndexedDB / service workers | общие | отдельные storage-директории и partition |
| HTTP cache / permissions / профиль | общие | общие — контекст их не разделяет |
| Runtime | один | тот же; это не второй профиль и не второй процесс |
| Когда выбирается контекст | при создании вкладки | при создании вкладки |

Честно: физическая база `cookies.sqlite` одна. Разделение обеспечивается `originAttributes` и отдельными директориями storage; это не два профиля и не два `GeckoRuntime`. `contextId` фиксируется при создании `GeckoSession`, поэтому вкладка, открытая на обычном хосте и затем перешедшая на российский, останется в старом partition. Custom tabs не партиционируются; private tabs всегда используют private storage.

---

## Скачать и установить

[Страница релизов](../../releases/latest) · [Прямая ссылка на ARM64 APK](../../releases/latest/download/matreshka-160.0a1-arm64-v8a-release.apk)

| Параметр | Значение |
| --- | --- |
| Название | `matre$ka` |
| Платформа | Android 8.0+ (`minSdk 26`) |
| ABI | `arm64-v8a` |
| Package | `ru.matreshka.browser` |
| Debug package | `ru.matreshka.browser.debug` |
| База | Firefox/Fenix `160.0a1`, BuildID `20261022001515` |
| Лицензия | MPL-2.0 |

Сертификат подписи (SHA-256):

```text
D697BD505F67DA0B36B46F15583CCD851FB0153BB1C15986F529B7B7209C655C
```

Проверьте сертификат и целостность файла:

```sh
apksigner verify --print-certs matreshka-160.0a1-arm64-v8a-release.apk
sha256sum -c SHA256SUMS
```

`SHA256SUMS` скачивайте из того же релиза, что и APK. Fingerprint выше — это digest сертификата подписи, а не SHA-256 самого APK. Не устанавливайте APK из зеркал и пересланных файлов.

## Сборка из исходников

| Компонент | Требование |
| --- | --- |
| Исходники | дерево Firefox `160.0a1` с `mobile/android/fenix` |
| JDK | 17; задайте `JAVA_HOME` |
| Android SDK/NDK, Rust, Python | через `./mach bootstrap` |
| ABI | `arm64-v8a`, `MOZCONFIG=mozconfig-arm64` |

```sh
export FIREFOX_SRC="$HOME/src/firefox"
export JAVA_HOME="$HOME/.mozbuild/jdk/jdk-17.0.20.1+1"

# core patch + Fenix overlay, идемпотентно
./scripts/apply.sh "$FIREFOX_SRC"

# ARM64 release
MOZCONFIG="$FIREFOX_SRC/mozconfig-arm64" \
  ./scripts/build-release.sh "$FIREFOX_SRC"
```

Подпись и релизный каталог:

```sh
export MATRESHKA_KEYSTORE="$HOME/keys/matreshka-release.jks"
export MATRESHKA_KEYSTORE_PASSWORD_FILE="$HOME/keys/matreshka-release.password"

./scripts/sign-apk.sh \
  "$FIREFOX_SRC/obj-aarch64-unknown-linux-android/gradle/build/mobile/android/fenix/app/outputs/apk/release/fenix-arm64-v8a-release.apk" \
  "dist/matreshka-160.0a1-arm64-v8a-release.apk"

(cd dist && sha256sum matreshka-160.0a1-arm64-v8a-release.apk > SHA256SUMS)
```

Или одной командой, если ключ и переменные уже настроены:

```sh
VERSION=160.0a1 FIREFOX_SRC="$FIREFOX_SRC" ./scripts/release.sh
```

`release.sh` сам вызывает `apply.sh`, `build-release.sh` и `sign-apk.sh`, затем пишет `dist/SHA256SUMS`. Для сборки GeckoView нужен полный build, не artifact mode: иначе изменённый C++-код проверки сертификатов не перекомпилируется.

Пути артефактов:

```text
$FIREFOX_SRC/obj-*-android/gradle/build/mobile/android/fenix/app/outputs/apk/release/fenix-arm64-v8a-release.apk
dist/matreshka-160.0a1-arm64-v8a-release.apk
dist/SHA256SUMS
```

Ключ подписи хранится локально и не входит в репозиторий. Потеря ключа не позволит ставить обновления поверх уже установленной версии. Детали интеграции — в [fenix/README.md](fenix/README.md).

---

## Структура репозитория

`app/` — отдельный форк `reference-browser`; он не вендорится в этот репозиторий.

```text
matreshka-browser/
├── README.md
├── LICENSE
├── docs/
│   ├── architecture.svg
│   └── images/
│       ├── CREDITS.md
│       ├── hero.png
│       ├── feature-trust.png
│       ├── feature-isolation.png
│       ├── feature-warning.png
│       └── social-preview.png
├── fenix/
│   ├── README.md
│   ├── apply.sh
│   └── files/app/...
├── patches/
│   └── core/
│       └── matreshka-trust.patch
└── scripts/
    ├── apply.sh
    ├── build-release.sh
    ├── release.sh
    └── sign-apk.sh
```

## Обновление форка

| Компонент | Текущий пин |
| --- | --- |
| Firefox base | `160.0a1`, BuildID `20261022001515` |
| Trust list | 2026-10-22, 70 хостов + 2 корня |
| Reference-browser fork | ветка `matreshka` |

**Firefox/Fenix.** Зафиксируйте новый upstream snapshot и BuildID, перегенерируйте `patches/core/matreshka-trust.patch` под новое дерево, проверьте `git apply --check`, прогоните `./scripts/apply.sh "$FIREFOX_SRC"`, соберите ARM64 release и пройдите smoke-тест trust/warning/storage. Затем опубликуйте новый GitHub Release с APK и `SHA256SUMS`.

**Reference-browser fork.** В отдельном клоне `reference-browser` используется ветка `matreshka`; изменения размечены `// MATRESHKA-BEGIN` / `// MATRESHKA-END`. Скрипты синхронизации живут в самом форке:

```sh
git remote add upstream https://github.com/mozilla-mobile/reference-browser
./scripts/sync-upstream.sh
./scripts/make-patches.sh
```

При конфликтах сохраняйте минимальный diff внутри маркеров, затем повторяйте rebase и сборку.

## Ограничения и безопасность

> Проект не обещает анонимность, не заменяет VPN/Tor и не «лечит» перехват трафика. Он меняет правила доверия для конкретных хостов и изолирует их storage.

- **Partition, а не профиль.** `contextId=matreshka` — это разделение внутри одного `GeckoRuntime`. Физически `cookies.sqlite` один; строки разделены через `originAttributes`, а storage-директории — отдельные.
- **Вкладка фиксируется при создании.** Non-blank вкладка, которая позже перешла на российский хост, остаётся в старом partition. Custom tabs не партиционируются.
- **Warning — индикатор, а не гарантия.** Он отражает состояние сертификата и списка, но не репутацию сайта и не отсутствие MITM.
- **Нет GOST.** Патч не добавляет ГОСТ-TLS; сайты, которым нужны только ГОСТ-шифры, могут не открыться.
- **Корни нельзя обновить по HTTPS.** Удалённый список добавляет только хосты. Корни остаются в APK; sub CA истекает 2027-03-06, список считается устаревшим через 60 дней.
- **Debug и брендинг.** В текущем релизе остаются debug-меню, часть телеметрии/Nimbus/Adjust/Sentry и Mozilla-строки/иконки. Это вынесено в roadmap.
- **Ключ подписи локальный.** `release-keystore` не в репозитории. Храните keystore и пароль в надёжном backup.
- **Пока нет Play/F-Droid.** APK ставится вручную, обновления — вручную с GitHub Releases.
- **ARM64 runtime.** ARM64-релиз собран, но физический ARM64-тест ещё нужен: x86_64-эмулятор его не устанавливает.

---

## Лицензия

Проект распространяется под [Mozilla Public License 2.0](LICENSE). Если вы распространяете изменённый APK, исходники изменённых файлов должны быть доступны на условиях MPL-2.0.

Mozilla, Firefox и соответствующие логотипы являются товарными знаками Mozilla Foundation. Проект не связан с Mozilla и не одобрен ею.

## Roadmap

- [ ] Physical hardening: отдельное физическое хранение для российского partition.
- [ ] Удалить телеметрию/Nimbus/Adjust/Sentry и вычистить Mozilla-брендинг.
- [ ] Новый значок и визуальный брендинг `matre$ka`.
- [ ] Сборка ARMv7.
- [ ] Публикация в F-Droid/Obtainium.
- [ ] E2E-тесты изоляции cookies и storage.

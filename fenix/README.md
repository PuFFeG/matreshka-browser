# Fenix integration

This directory contains the Fenix-side part of `matre$ka`, applied on top of a Firefox source
tree at `mobile/android/fenix`.

## What it changes

- `app/src/main/java/ru/matreshka/browser/`: `TrustConfig`, `TrustRepository`,
  `TrustPartition`, `MatreshkaTrustBridge`, `TrustWarning`, `MatreshkaSitesActivity`.
- `GeckoProvider.kt`: builds the Matreshka trust bundle and passes it to
  `GeckoRuntimeSettings` through reflection; can re-apply it after the list changes.
- `Core.kt`: installs `MatreshkaStorageMiddleware` before `EngineMiddleware`, so new tabs on
  Russian-trust hosts get `contextId = matreshka`.
- `BrowserToolbarMiddleware.kt`: replaces the site-info icon with the amber Matreshka shield
  when the certificate is out of list or the list is stale; tapping opens the explanation
  dialog and a shortcut to the list.
- `SettingsFragment.kt` + `preferences.xml`: “Список сайтов Минцифры” under Advanced.
- `AndroidManifest.xml`: non-exported `MatreshkaSitesActivity`.
- `static_strings.xml`: Russian UI strings and the `matre$ka` app name.
- `app/build.gradle`: release ships as `ru.matreshka.browser` without the `.firefox` suffix
  or Mozilla's `sharedUserId`.
- `app/src/release/...`: release app name and manifest overrides.
- `Settings.kt`: translation pop-up is off by default; DoH settings stay visible without
  waiting for Nimbus.

The core trust patch lives in [`../patches/core/`](../patches/core/), not here. It adds
`matreshka.trust.enabled`, `matreshka.trust.ru_fallback` and `matreshka.trust.json`, plus the
C++ verification hook.

## Apply

```sh
./fenix/apply.sh /path/to/firefox/mobile/android/fenix
```

Or apply both the core patch and this overlay:

```sh
FIREFOX_SRC=/path/to/firefox ./scripts/apply.sh
```

The overlay copies full files, so it is idempotent but not a rebase-aware patch. Re-check it
against every new upstream snapshot.

## Build

Debug x86_64 for emulator testing:

```sh
cd /path/to/firefox
export JAVA_HOME="$HOME/.mozbuild/jdk/jdk-17.0.20.1+1"
export MOZCONFIG=$PWD/mozconfig
./mach gradle fenix:assembleDebug
```

ARM64 release:

```sh
export MOZCONFIG=$PWD/mozconfig-arm64
./mach gradle fenix:assembleRelease
```

Output:

```text
obj-aarch64-unknown-linux-android/gradle/build/mobile/android/fenix/app/outputs/apk/release/fenix-arm64-v8a-release.apk
```

Signing and the release flow are in [`../scripts/`](../scripts/). The release APK is
`ru.matreshka.browser`, minSdk 26, native `arm64-v8a`.

## Verified

- App starts as `matre$ka`; address bar search and suggestions work.
- `sberbank.ru` loads through the listed Russian Trusted chain.
- `expobank.ru` loads through the `.ru` fallback and shows “Сертификат Минцифры: домен вне
  списка”; tapping the shield opens the dialog.
- Cookies for `expobank.ru` have
  `originAttributes = ^geckoViewUserContextId=gvctx6d6174726573686b61` (`matreshka`), while
  ordinary sites have an empty `originAttributes` in the same `cookies.sqlite`.
- Storage directories are separate:
  `storage/default/https+++expobank.ru^geckoViewUserContextId=gvctx6d6174726573686b61`.
- Settings → DNS over HTTPS is visible and functional.
- `MatreshkaTrust: Passed matreshka trust configuration via matreshkaTrust` appears in logcat.

## Limitations

- One physical `cookies.sqlite`, partitioned by `originAttributes`; this is not a second
  profile or a second `GeckoRuntime`.
- `contextId` is fixed when the session is created; a non-blank tab that later navigates to a
  Russian host stays in its original partition. Custom tabs are not partitioned.
- HTTP cache, site permissions and the profile remain shared.
- Telemetry/Nimbus/Adjust/Sentry and some Mozilla branding are still present in the current
  release.
- No GOST TLS. The `.ru`/`.рф` fallback only accepts chains rooted in the two bundled roots.
- The ARM64 APK still needs testing on a physical ARM64 device.

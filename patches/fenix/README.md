# Fenix patches

These patches are applied **after** `fenix/apply.sh` has copied the Matreshka overlay. The
series is ordered and applied by `scripts/apply-fenix-patches.sh`, which is called from
`scripts/apply.sh`.

| Patch | Purpose |
| --- | --- |
| `0001-remove-third-party-telemetry.patch` | Remove Sentry, Adjust and Firebase Messaging from the release APK and from startup paths. |
| `0002-disable-glean-telemetry.patch` | Disable Glean upload/collection and remove Glean metrics/crash services. |

## Leanplum and Crashlytics

Fenix 160.0a1 no longer contains Leanplum or Crashlytics. There is no Gradle dependency,
Kotlin/Java class or startup hook for either SDK in this tree. Patch 0001 removes the actual
third-party SDKs that are still present:

- **Sentry** — third-party crash upload (`lib-crash-sentry`, `sentry-android-core`).
- **Adjust** — third-party marketing attribution (`adjust-android`).
- **Firebase Messaging** — Google push/initialization SDK (`lib-push-firebase`); push was
  already inert because no Firebase project is configured, but the provider and dependencies
  are now gone.

Mozilla Socorro (`lib-crash`) remains as the in-app crash-reporting path. Nimbus/Remote
Settings remains for experiments and Remote Settings.

## Glean

Patch 0002:

- initializes Glean with `uploadEnabled = false`;
- installs `MatreshkaGleanUploader`, a `PingUploader` that drops every ping before any network
  request, including deletion-request pings;
- stops registering Fenix custom pings;
- removes `GleanMetricsService`, `GleanUsageReportingMetricsService` and
  `GleanCrashReporterService` from Fenix startup;
- removes the onboarding opt-out ping; and
- sets `BuildConfig.TELEMETRY=false` and the `isTelemetryEnabled` default to `false`.

This covers Fenix Kotlin Glean and the native crash-ping path. Firefox Telemetry/FOG on
Android is separate and is already off or has no uploader in this build.

## Maintenance

Generate patches against the tree **after** the overlay is applied. When rebasing onto a new
Firefox snapshot, apply the overlay first, then rebase these two patches in order. If a patch
conflicts, resolve or rebase it; do not replace whole upstream files with the old overlay.

# Android launcher icon overlay

Copy the `res/` tree into the Android app module's `src/main/res/` (or the matching product-flavor source set). The overlay provides:

- `drawable/matreshka_launcher_background.xml`
- `drawable/matreshka_launcher_foreground.xml`
- `drawable/matreshka_launcher_monochrome.xml`
- `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml`
- `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`

The manifest keeps `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`. The same files are mirrored under `../fenix/files/app/src/main/res/`, so `../fenix/apply.sh` applies the icon together with the rest of the Fenix overlay. Check every build variant and any monochrome/adaptive icon declarations before release.

This is a new matryoshka mark, not a Firefox or Yandex-derived logo. The overlay supplies the Android 8+ adaptive icon resource; it does not patch a Fenix repository by itself.

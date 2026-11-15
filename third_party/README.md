# Third-party components

## `cmfa-aar`

Minimal Gradle project used to build the ClashMetaForAndroid `core` and `common`
AARs consumed by `android/app`.

- Upstream: https://github.com/MetaCubeX/ClashMetaForAndroid
- Version: v2.11.37
- License: GPL-3.0

The native libraries `libclash.so` and `libbridge.so` are extracted from the
CMFA release APK. They are intentionally ignored by Git; see `BUILD.md`.

## `cmfa-src`

Source snapshot of ClashMetaForAndroid v2.11.37 retained for audit and rebuild
reference. The Android APK build uses the AARs produced by `cmfa-aar`, not this
snapshot directly.

The snapshot's `.gitmodules` references the mihomo submodule. The submodule
content is not checked in. Initialize it only when rebuilding the CMFA native
libraries.

## Licensing

See the repository `NOTICE`, `COPYING`, and `LICENSE` files. CMFA is GPL-3.0;
the combined Android application is distributed under GPL-3.0.

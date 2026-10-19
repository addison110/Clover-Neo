# Clover Neo - imageboard browser for Android

[![Build](https://github.com/addison110/Clover-Neo/actions/workflows/build.yml/badge.svg)](https://github.com/addison110/Clover-Neo/actions/workflows/build.yml)

[Releases](https://github.com/addison110/Clover-Neo/releases) | [Issues](https://github.com/addison110/Clover-Neo/issues)

Clover Neo is a maintained fork of [Clover](https://github.com/Floens/Clover), a fast Android app for browsing imageboards such as 4chan and 8chan. It adds inline replying, thread watching, notifications, themes, pass support, filters and a whole lot more. Clover Neo is licensed under the GPL and will always be free.

Clover Neo installs as its own app (`io.github.addison110.cloverneo`) and can be installed next to the original Clover. Settings, bookmarks and history are not carried over from Clover.

Requires Android 6.0 or newer.

## Issues and features
Issues can be reported on the [Issues page](https://github.com/addison110/Clover-Neo/issues). Please search before reporting an issue to avoid duplicates!

## Building
You need JDK 17 or newer and the Android SDK (set `ANDROID_HOME` or `sdk.dir` in `Clover/local.properties`).

```sh
cd Clover
./gradlew assembleDefaultDebug    # debug APK in app/build/outputs/apk/default/debug/
./gradlew check                   # checkstyle, lint and unit tests
```

The build flavors are `default` (updates itself from GitHub releases), `dev` and `fdroid` (no in-app updater).

To sign release builds, create `Clover/app/keys.properties`, see `Clover/app/build.gradle`.

## Releasing
The in-app updater of the `default` flavor reads the latest GitHub release of this repository.

1. Bump `versionName` and `versionCode` in `Clover/app/build.gradle` (`versionCode` is XXYYZZ for vXX.YY.ZZ).
2. Build and sign the release APK: `./gradlew assembleDefaultRelease`.
3. Create a GitHub release with a tag matching `versionName` (e.g. `v3.1.0`) and attach the APK. The release notes are shown in the update dialog.

## Translations
Translations live in `Clover/app/src/main/res/values-*/strings.xml`. Pull requests with new or updated translations are welcome.

## License
Clover Neo is [GPLv3](COPYING.txt), [licenses of the used libraries](Clover/app/src/main/assets/html/licenses.html).

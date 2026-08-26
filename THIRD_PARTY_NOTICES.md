# Third-Party Notices

TxnSheet uses the third-party software listed below. Versions reflect
`gradle/libs.versions.toml`.

This file is an attribution index, not a substitute for distributing any complete
license or notice texts required by the applicable terms. Review the resolved
dependency graph again for every release.

## Runtime dependencies

### AndroidX and Jetpack — Apache License 2.0

Includes AndroidX Core KTX 1.18.0; Activity Compose 1.13.0; Lifecycle Runtime,
Runtime Compose, and ViewModel Compose 2.10.0; Navigation Compose 2.9.8; Core
Splashscreen 1.2.0; Compose UI, UI Tooling Preview, Foundation, Material 3, and
Material Icons Extended aligned by Compose BOM 2026.06.01; Room Runtime and Room
KTX 2.8.4; and WorkManager Runtime KTX 2.11.2.

Copyright The Android Open Source Project.

- [AndroidX source and project information](https://android.googlesource.com/platform/frameworks/support/)
- [AndroidX Apache-2.0 license](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt)

The Compose BOM is dependency-alignment metadata and is not packaged as application
bytecode.

### Kotlin — Apache License 2.0

Includes the Kotlin standard-library family associated with Kotlin 2.3.21.

Copyright JetBrains s.r.o. and Kotlin contributors.

- [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [Kotlin third-party license information](https://github.com/JetBrains/kotlin/blob/master/license/README.md)
- [Kotlin notices](https://github.com/JetBrains/kotlin/blob/master/license/NOTICE.txt)

The Kotlin distribution includes separately licensed third-party components identified
in its official notices.

### kotlinx.coroutines — Apache License 2.0

Includes `kotlinx-coroutines-android` and `kotlinx-coroutines-play-services` 1.10.2.

Copyright JetBrains s.r.o. and Kotlin contributors.

- [kotlinx.coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)

### OkHttp — Apache License 2.0

Includes OkHttp 4.12.0.

Copyright 2019 Square, Inc.

- [OkHttp license](https://github.com/square/okhttp/blob/master/LICENSE.txt)
- [OkHttp 4.12.0 artifact metadata](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.pom)

### Google Play services Auth — Google terms

Includes `com.google.android.gms:play-services-auth:21.6.0`.

The official artifact metadata declares the Android Software Development Kit License
Agreement. This dependency is not represented here as an Apache-2.0 component. Use of
Google APIs is also subject to the Google APIs Terms of Service.

- [Official artifact POM](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-auth/21.6.0/play-services-auth-21.6.0.pom)
- [Android SDK License Agreement](https://developer.android.com/studio/terms)
- [Google APIs Terms of Service](https://developers.google.com/terms)

## Debug-only dependencies

### Compose UI Tooling — Apache License 2.0

AndroidX Compose UI Tooling, aligned by Compose BOM 2026.06.01, is included only in
debug builds.

- [AndroidX Apache-2.0 license](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt)

## Test-only dependencies

These dependencies are not included in the production APK.

### JUnit 4.13.2 — Eclipse Public License 1.0

- [JUnit 4 license](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt)

### Hamcrest Core 1.3 — BSD 3-Clause License

Hamcrest Core is a transitive test dependency of JUnit 4.13.2.

- [Hamcrest license](https://github.com/hamcrest/JavaHamcrest/blob/master/LICENSE)

### kotlinx-coroutines-test 1.10.2 — Apache License 2.0

- [kotlinx.coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)

## Build-time dependencies

These tools or processors are used to build the application and are not shipped as
application runtime code.

### Android Gradle Plugin 9.3.1 and legacy KAPT integration — Apache License 2.0

- [Android build-tools source](https://android.googlesource.com/platform/tools/base/)
- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

### Kotlin Compose compiler plugin 2.3.21 — Apache License 2.0

- [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [Kotlin third-party license information](https://github.com/JetBrains/kotlin/blob/master/license/README.md)

### Room compiler 2.8.4 — Apache License 2.0

- [AndroidX Apache-2.0 license](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt)

### Gradle Wrapper 9.5.0 — Apache License 2.0

- [Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE)

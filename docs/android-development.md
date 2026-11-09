# Android development checks

Moved out of the README; nothing was removed.

The `android` Gradle project compiles the plugin against the published PAM Native
1.0.18 plugin API. Pass the verified AAR path with `-PpamPluginApi=/path/to/api.aar`
and run `testDebugUnitTest` using Gradle 9.4.1, JDK 17 and Android SDK 36.
The Android workflow downloads the official AAR, checks its SHA-256, compiles the
camera integration and uploads the Kotlin test results.

The JVM tests cover duplicate suppression, its expiry boundary, clearing and
bounded retention. The physical Android harness verifies camera permission,
preview streaming, disable/enable, background/foreground recovery and release.

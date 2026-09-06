<!-- pam:product-page:start -->
<div align="center">

# PAM Native Scanner

**Production barcode scanning as a focused native capability.**

Combine a native camera preview with bounded QR and barcode results, deduplication, and lifecycle-safe sessions.

[![Latest version](https://img.shields.io/packagist/v/pushinbr/pam-native-scanner?style=flat-square&label=stable)](https://packagist.org/packages/pushinbr/pam-native-scanner)
[![CI](https://img.shields.io/github/actions/workflow/status/push-in/pam-native-scanner/ci.yml?branch=main&style=flat-square&label=CI)](https://github.com/push-in/pam-native-scanner/actions)
![PHP](https://img.shields.io/badge/PHP-8.5-777BB4?style=flat-square&logo=php&logoColor=white)
![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-15%2B-000000?style=flat-square&logo=apple&logoColor=white)

**[Documentation](https://push-in.github.io/pam-docs/native/overview/) · [Quick start](#quick-start) · [What you can build](#what-you-can-build) · [PAM ecosystem](https://push-in.github.io/pam-docs/ecosystem/) · [Issues](https://github.com/push-in/pam-native-scanner/issues)**

</div>

---

## Why PAM Native Scanner

Combine a native camera preview with bounded QR and barcode results, deduplication, and lifecycle-safe sessions. The public API is strictly typed for PHP 8.5; expensive or frame-sensitive work stays in Rust or the platform SDK instead of crossing the application boundary every frame.

| | |
| --- | --- |
| **Best for** | A focused capability you can add to any PAM Native application |
| **Native path** | ML Kit · VisionKit/Vision |
| **Application model** | Composer package + generated native integration |
| **Design rule** | Independent module; no feed, vertical, or application template bundled |

## What you can build

- Retail and warehouse scanning
- Tickets, access, and identity flows
- QR onboarding and payment payloads

## Quick start

Already have a PAM Native project? Add only this capability:

```bash
pam composer require pushinbr/pam-native-scanner
pam doctor --fix
```

New to PAM? Follow the **[five-minute PAM Native setup](https://push-in.github.io/pam-docs/native/overview/)** once, then return here. Your application stays a normal Composer project with a committed lockfile.
<!-- pam:product-page:end -->

## See it in action

Real-time QR and barcode scanning with native previews. Android uses CameraX `1.6.1` and the bundled ML Kit model `17.3.0`, so first use does not depend on a model download. iOS uses AVFoundation and Vision.

```bash
pam add scanner
pam doctor
```

```php
return Pam\Native\Scanner\ScannerView::make(
    Pam\Native\Scanner\BarcodeFormat::QrCode,
    Pam\Native\Scanner\BarcodeFormat::Ean13,
)->duplicateInterval(1200)->onResult(function (Pam\Native\Scanner\ScanResult $result): void {});
```

The analyzer keeps only the latest frame, runs outside the UI thread, closes every image proxy and suppresses duplicate values for a configurable interval. Camera permission denial and native failures are typed events. Camera usage metadata and Android permission merging are supplied by the plugin.

Platform support: Android API 26+, iOS 15+, PAM Native 0.8.x.

## What installation does

`pam add scanner` resolves the official compatible package, performs a non-mutating Composer preflight, updates the normal `composer.json` and `composer.lock`, refreshes generated native integration when required, and leaves the project ready for `pam doctor` validation.

Use `pam packages` to inspect availability and `pam remove scanner` to uninstall the capability safely. Direct Composer commands are an advanced interoperability path; PAM is the supported application workflow.

## API guide

| API | Responsibility |
| --- | --- |
| `ScannerView` | Render the native preview and configure scanning. |
| `BarcodeFormat` | Select only the barcode symbologies the product needs. |
| `ScanResult` | Receive value, format, and normalized scan data. |
| `ScannerEventKind` | Handle typed results, permission failures, and native errors. |
| `CameraFacing` | Select front or back camera. |

All coded states, kinds, and variants are sequential integer-backed enums. Use enum cases in application code; do not depend on raw wire numbers.

## Production checklist

- Request camera permission before mounting an enabled scanner.
- Restrict formats to improve speed and reduce false positives.
- Validate scanned values before navigation, network calls, or payment actions.
- Run `pam doctor`, `pam test`, and a signed release build on every supported platform.
- Exercise denial, cancellation, backgrounding, process restart, and offline behavior before release.

## Android development checks

The `android` Gradle project compiles the plugin against the published PAM Native
1.0.18 plugin API. Pass the verified AAR path with `-PpamPluginApi=/path/to/api.aar`
and run `testDebugUnitTest` using Gradle 9.4.1, JDK 17 and Android SDK 36.
The Android workflow downloads the official AAR, checks its SHA-256, compiles the
camera integration and uploads the Kotlin test results.

The current JVM tests cover duplicate suppression, its expiry boundary, clearing
and bounded retention. They do not certify actual camera permissions, preview
rendering or asynchronous lifecycle behavior on a device. Those checks and iOS
parity remain required before publishing the lifecycle changes.

## Troubleshooting

- **Preview is black:** verify permission, lifecycle visibility, and camera availability.
- **The same code fires repeatedly:** increase `duplicateInterval()`.
- **A format is never recognized:** include it explicitly in `make()`.
- **Native integration is stale:** run `pam doctor --fix`, rebuild the native host, and inspect the first reported diagnostic.

## Compatibility and support

This package targets PAM Native `0.8.x`, Android API 26+, and iOS 15+ unless a platform-specific section above states a stricter requirement. Platform SDKs, credentials, entitlements, physical hardware, and store configuration remain application responsibilities.

- [PAM documentation](https://push-in.github.io/pam-docs/introduction/)
- [PAM Native overview](https://push-in.github.io/pam-docs/native/overview/)
- [Plugin and native capability model](https://push-in.github.io/pam-docs/native/plugins/)
- [Report an issue](https://github.com/push-in/pam-native-scanner/issues)

Security vulnerabilities should be reported through the repository security policy or GitHub private vulnerability reporting, not a public issue.

## QR codes in selected images (unreleased)

`QrImages::decode($uri, $complete, $failure)` decodes a local image without opening the camera. Android accepts a granted `content://` URI or `file://` URI; iOS accepts a local `file://` URI. Remote URLs are rejected. Obtain access through the platform image/file picker first.

The completion callback receives a list of `ScanResult` objects: an empty list means no QR was found, and multiple results must be presented for selection rather than silently choosing a payment. Up to 16 distinct QR values are returned. Each result uses `BarcodeFormat::QrCode` and `BarcodeValueKind::Unknown`; contents are never opened or executed automatically.

Images are reduced to at most 2048 pixels on either axis before recognition. This bounds decoded image allocation but can reduce recognition of very small codes in large images. Only one decode runs per module at a time. Callers must ignore stale results after navigation or cancellation. The failure callback receives a generic error, without the local path.

Current validation: Android compilation and sizing tests plus PHP transport contracts. iOS compilation, real image fixtures and physical-device permission/picker/lifecycle tests remain required before release.

### Android image instrumentation

The Android CI artifact `scanner-image-instrumentation-apk` includes an instrumentation runner using the real ML Kit module. To execute the locally built APK on an authorized physical device, set `ANDROID_SERIAL` and run `bash scripts/test-images-android.sh` (optionally pass the downloaded APK path). The script refuses emulator serials and the emulator system property. It installs only the scanner test package and checks the instrumentation result. No camera permission is requested by these image-only tests.

The APK was compiled locally; physical execution remains pending. Vision image recognition tests ran successfully on the macOS CI runner; that is separate from Android recognition and device/picker permission validation.

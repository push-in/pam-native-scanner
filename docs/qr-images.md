# QR codes in selected images

Moved out of the README; nothing was removed.

`QrImages::decode($uri, $complete, $failure)` decodes a local image without opening the camera. It accepts the private `pam-file:///...` URI returned by PAM Native `Files::pick()`. Android also accepts a granted `content://` URI or `file://` URI; iOS accepts a local `file://` URI. Remote URLs are rejected. Obtain access through the platform image/file picker first.

The completion callback receives a list of `ScanResult` objects: an empty list means no QR was found, and multiple results must be presented for selection rather than silently choosing a payment. Up to 16 distinct QR values are returned. Each result uses `BarcodeFormat::QrCode` and `BarcodeValueKind::Unknown`; contents are never opened or executed automatically.

Images are reduced to at most 2048 pixels on either axis before recognition. Android first snapshots the selected stream into app-private cache with a 32 MiB input limit, then reads bounds and pixels from that same snapshot; the temporary copy is removed on success or failure. This bounds decoded image allocation but can reduce recognition of very small codes in large images. Only one decode runs per module at a time. Callers must ignore stale results after navigation or cancellation. The failure callback receives a generic error, without the local path.

Current validation: PHP transport contracts, Android compilation and sizing tests,
real image fixtures on Android and iOS, and Android camera lifecycle on a physical
Samsung device. Application picker UX still requires validation in the consuming app.

## Android image instrumentation

The Android CI artifact `scanner-image-instrumentation-apk` includes an instrumentation runner using the real ML Kit module. To execute the locally built APK on an authorized physical device, set `ANDROID_SERIAL` and run `bash scripts/test-images-android.sh` (optionally pass the downloaded APK path). The script refuses emulator serials and the emulator system property. It installs only the scanner test package and checks the instrumentation result. No camera permission is requested by these image-only tests.

The harness has passed on a physical Samsung SM-G973F: real ML Kit recognition,
permission grant, preview streaming, disable/enable, background/foreground and
release. Vision image recognition also passes with real fixtures on the macOS CI runner.

## Generate a QR image

```php
QrImages::generate('https://example.test/profile', 440,
    complete: function (string $uri): void {
        // Display with Image::source($uri). Release when the image leaves the screen.
        QrImages::release($uri);
    },
    failure: function (string $message): void { /* Show a text/link fallback. */ },
);
```

Generation works offline: ZXing Core 3.5.4 on Android and Core Image on iOS. The payload is UTF-8, 1–2048 bytes, without NUL; dimensions are 128–1024 pixels. The PNG has a white background, medium error correction and a four-module quiet zone. A size too small for the payload fails instead of cropping the QR. Files live in the private `pam-qr-generated` cache directory. Call `release` on replacement/unmount, including a late result after the owner unmounted. It only accepts files generated in that directory. Interrupted-process leftovers are bounded to 16 files and pruned after 24 hours on the next generation.

Android generation instrumentation can run without camera permission using `-e generationOnly true` with `dev.pam.scanner.ScannerImageInstrumentation`: validates URL/otpauth/Unicode roundtrip via ML Kit, dimensions, bounds and cleanup. The existing full image/camera suite remains available without the argument. iOS generation test source is `tests/ios-generation/main.swift`, compiled with `QrImageGenerator.swift` and `QrImageDecoder.swift` in an iOS test target; it covers the same roundtrip, dimensions, bounds and cleanup. iOS requires Apple SDK execution.

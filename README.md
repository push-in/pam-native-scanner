# PAM Native Scanner

## Start here

This is a Composer extension for PAM Native. Install the PAM Runtime, create a native project, and then add this package through PAM’s verified Composer toolchain:

```bash
curl --proto '=https' --proto-redir '=https' --tlsv1.2 \
    --connect-timeout 15 --max-time 60 --max-filesize 1048576 -fsSL \
    https://github.com/push-in/pam/releases/latest/download/install.sh | sh

pam init my-app --template native
cd my-app
pam composer require pushinbr/pam-native-scanner
pam doctor --fix
```


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

Platform support: Android API 26+, iOS 15+, PAM Native 0.6.x.


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

## Troubleshooting

- **Preview is black:** verify permission, lifecycle visibility, and camera availability.
- **The same code fires repeatedly:** increase `duplicateInterval()`.
- **A format is never recognized:** include it explicitly in `make()`.
- **Native integration is stale:** run `pam doctor --fix`, rebuild the native host, and inspect the first reported diagnostic.

## Compatibility and support

This package targets PAM Native `0.6.x`, Android API 26+, and iOS 15+ unless a platform-specific section above states a stricter requirement. Platform SDKs, credentials, entitlements, physical hardware, and store configuration remain application responsibilities.

- [PAM documentation](https://push-in.github.io/pam-docs/introduction/)
- [PAM Native overview](https://push-in.github.io/pam-docs/native/overview/)
- [Plugin and native capability model](https://push-in.github.io/pam-docs/native/plugins/)
- [Report an issue](https://github.com/push-in/pam-native-scanner/issues)

Security vulnerabilities should be reported through the repository security policy or GitHub private vulnerability reporting, not a public issue.

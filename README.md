# PAM Native Scanner

Real-time QR and barcode scanning with native previews. Android uses CameraX `1.6.1` and the bundled ML Kit model `17.3.0`, so first use does not depend on a model download. iOS uses AVFoundation and Vision.

```bash
composer require pushinbr/pam-native-scanner
pam mobile codegen
pam mobile ios:prepare
```

```php
return Pam\Native\Scanner\ScannerView::make(
    Pam\Native\Scanner\BarcodeFormat::QrCode,
    Pam\Native\Scanner\BarcodeFormat::Ean13,
)->duplicateInterval(1200)->onResult(function (Pam\Native\Scanner\ScanResult $result): void {});
```

The analyzer keeps only the latest frame, runs outside the UI thread, closes every image proxy and suppresses duplicate values for a configurable interval. Camera permission denial and native failures are typed events. Camera usage metadata and Android permission merging are supplied by the plugin.

Platform support: Android API 26+, iOS 15+, PAM Native 0.6.x.

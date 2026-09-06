# Synthetic QR fixtures

`single.png` encodes `pam-image-one`. `multiple.png` contains two separate codes encoding `pam-first` and `pam-second`.

Generated using the already installed Endroid QR Code PHP writer with 280 pixel code size and 40 pixel margin. The multiple fixture places the two generated images side by side without resampling. These contain no payment, personal or production data.

The instrumentation test uses the actual QrImageModule/ML Kit pipeline. It also creates a blank image and a corrupt file, and verifies remote URLs are refused. Building the APK alone does not prove these recognition tests passed; execute it on an authorized physical device.

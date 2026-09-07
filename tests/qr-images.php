<?php

declare(strict_types=1);

use Pam\Native\Internal\Wire;
use Pam\Native\ModuleResultStatus;
use Pam\Native\Modules\NativeModules;
use Pam\Native\Modules\NativeModuleTransport;
use Pam\Native\Scanner\BarcodeFormat;
use Pam\Native\Scanner\QrImages;

$test('image decoding accepts local sources and preserves every detected QR', static function (): void {
    $transport = new class implements NativeModuleTransport {
        public function invoke(int $requestId, string $module, string $method, string $payload, Closure $complete): void
        {
            $uri = Wire::decodeMap($payload)['uri'] ?? null;
            if ($module !== 'scanner.image' || $method !== 'decodeQrImage'
                || !in_array($uri, ['file:///tmp/image.png', 'pam-file:///imports/qr%20pix.png'], true)) {
                throw new RuntimeException('Incorrect image request');
            }
            $complete(ModuleResultStatus::Success, Wire::map(['values' => json_encode(['first', 'second'], JSON_THROW_ON_ERROR)]));
        }
    };
    NativeModules::useTransport($transport);
    try {
        $results = null;
        QrImages::decode('file:///tmp/image.png', static function (array $values) use (&$results): void { $results = $values; }, static function (): void { throw new RuntimeException('Unexpected failure'); });
        if (count($results ?? []) !== 2 || $results[0]->value !== 'first' || $results[1]->value !== 'second' || $results[0]->format !== BarcodeFormat::QrCode) {
            throw new RuntimeException('QR results were lost');
        }
        QrImages::decode('pam-file:///imports/qr%20pix.png', static function (): void {}, static function (): void { throw new RuntimeException('Private PAM file rejected'); });
        try {
            QrImages::decode('https://example.test/image.png', static function (): void {}, static function (): void {});
            throw new RuntimeException('Remote image accepted');
        } catch (InvalidArgumentException) {}
        foreach (['pam-file:///', 'pam-file:///../secret.png', 'pam-file:///images/%00.png'] as $invalid) {
            try {
                QrImages::decode($invalid, static function (): void {}, static function (): void {});
                throw new RuntimeException('Unsafe private image accepted');
            } catch (InvalidArgumentException) {}
        }
    } finally {
        NativeModules::useTransport(null);
    }
});

$test('image decoding rejects malformed native results and reports missing QR as an empty list', static function (): void {
    $transport = new class implements NativeModuleTransport {
        public string $json = '{}';
        public function invoke(int $requestId, string $module, string $method, string $payload, Closure $complete): void
        {
            $complete(ModuleResultStatus::Success, Wire::map(['values' => $this->json]));
        }
    };
    NativeModules::useTransport($transport);
    try {
        foreach (['null', '{}', '[7]', '[""]', '{"key":"value"}', json_encode(array_fill(0, 17, 'code')), '['] as $json) {
            $transport->json = $json;
            $failed = false;
            QrImages::decode('content://picker/image', static function (): void { throw new RuntimeException('Malformed result accepted'); }, static function () use (&$failed): void { $failed = true; });
            if (!$failed) throw new RuntimeException('Missing failure callback');
        }
        $transport->json = '[]';
        $results = null;
        QrImages::decode('file:///tmp/empty.png', static function (array $values) use (&$results): void { $results = $values; }, static function (): void { throw new RuntimeException('Empty image failed'); });
        if ($results !== []) throw new RuntimeException('Expected an empty result');
    } finally {
        NativeModules::useTransport(null);
    }
});

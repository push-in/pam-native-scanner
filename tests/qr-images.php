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

$test('generation validates bounds, preserves payload, rejects foreign files and releases owned PNG', static function (): void {
    $transport = new class implements NativeModuleTransport {
        public array $calls = [];
        public string $uri = 'file:///cache/pam-qr-generated/qr-123abc.png';
        public function invoke(int $requestId, string $module, string $method, string $payload, Closure $complete): void {
            $this->calls[] = [$module, $method, Wire::decodeMap($payload)];
            $complete(ModuleResultStatus::Success, Wire::map(['uri' => $this->uri]));
        }
    };
    NativeModules::useTransport($transport);
    try {
        $output = null;
        QrImages::generate('otpauth://totp/Zé?secret=TEST', 440, static function (string $uri) use (&$output): void { $output = $uri; }, static function (): void { throw new RuntimeException('Unexpected failure'); });
        if ($output !== $transport->uri || $transport->calls[0] != ['scanner.image', 'generateQrImage', ['text' => 'otpauth://totp/Zé?secret=TEST', 'pixels' => 440]]) throw new RuntimeException('Lost payload or size');
        QrImages::release($output);
        if ($transport->calls[1][1] !== 'releaseQrImage') throw new RuntimeException('No cleanup');
        foreach ([['', 256], [str_repeat('a', 2049), 256], ["x\0y", 256], ["\xff", 256], ['ok', 127], ['ok', 1025]] as [$text, $pixels]) {
            try { QrImages::generate($text, $pixels, static function (): void {}, static function (): void {}); throw new RuntimeException('Invalid generation accepted'); } catch (InvalidArgumentException) {}
        }
        foreach (['file:///cache/image.png', 'https://host/pam-qr-generated/qr-ab.png', 'file:///cache/pam-qr-generated/qr-ab.png?x'] as $uri) {
            try { QrImages::release($uri); throw new RuntimeException('Foreign file accepted'); } catch (InvalidArgumentException) {}
        }
        $transport->uri = 'https://example.test/image.png'; $failed = false;
        QrImages::generate('ok', 256, static function (): void { throw new RuntimeException('Remote result accepted'); }, static function () use (&$failed): void { $failed = true; });
        if (!$failed) throw new RuntimeException('Missing failure');
    } finally { NativeModules::useTransport(null); }
});

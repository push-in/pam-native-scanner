<?php

declare(strict_types=1);

namespace Pam\Native\Scanner;

use Closure;
use InvalidArgumentException;
use Pam\Native\Internal\Wire;
use Pam\Native\Modules\NativeModuleResult;
use Pam\Native\Modules\NativeModules;
use UnexpectedValueException;

final class QrImages
{
    private function __construct() {}

    /** @param Closure(list<ScanResult>): void $complete @param Closure(string): void $failure */
    public static function decode(string $uri, Closure $complete, Closure $failure): int
    {
        if (!self::validLocalUri($uri)) {
            throw new InvalidArgumentException('Select a local image URI.');
        }
        return NativeModules::call('scanner.image', 'decodeQrImage', ['uri' => $uri], static function (NativeModuleResult $result) use ($complete, $failure): void {
            if (!$result->succeeded()) {
                $failure('Unable to decode the selected image.');
                return;
            }
            try {
                $payload = Wire::decodeMap($result->payload);
                if (!is_string($payload['values'] ?? null)) throw new UnexpectedValueException;
                $values = json_decode($payload['values'], false, 4, JSON_THROW_ON_ERROR);
                if (!is_array($values) || !array_is_list($values) || count($values) > 16) throw new UnexpectedValueException;
                $results = [];
                foreach ($values as $value) {
                    if (!is_string($value) || $value === '' || strlen($value) > 16384) throw new UnexpectedValueException;
                    $results[] = new ScanResult($value, BarcodeFormat::QrCode, BarcodeValueKind::Unknown);
                }
            } catch (\Throwable) {
                $failure('Invalid response from the QR image decoder.');
                return;
            }
            $complete($results);
        });
    }

    /** Generate a private temporary PNG. Caller must release the URI when no longer displayed.
     * @param Closure(string): void $complete @param Closure(string): void $failure
     */
    public static function generate(string $text, int $pixels, Closure $complete, Closure $failure): int
    {
        if ($text === '' || strlen($text) > 2048 || preg_match('//u', $text) !== 1 || str_contains($text, "\0")) {
            throw new InvalidArgumentException('QR payload must contain 1–2048 UTF-8 bytes without NUL.');
        }
        if ($pixels < 128 || $pixels > 1024) throw new InvalidArgumentException('QR image size must be 128–1024 pixels.');
        return NativeModules::call('scanner.image', 'generateQrImage', ['text' => $text, 'pixels' => $pixels], static function (NativeModuleResult $result) use ($complete, $failure): void {
            if (!$result->succeeded()) { $failure('Unable to generate the QR image.'); return; }
            try {
                $uri = Wire::decodeMap($result->payload)['uri'] ?? null;
                if (!is_string($uri) || !self::validGeneratedUri($uri)) throw new UnexpectedValueException;
            } catch (\Throwable) { $failure('Invalid response from the QR image generator.'); return; }
            $complete($uri);
        });
    }

    /** Delete only a PNG owned by this generator; never an arbitrary local image. */
    public static function release(string $uri): int
    {
        if (!self::validGeneratedUri($uri)) throw new InvalidArgumentException('Expected a generated QR image URI.');
        return NativeModules::call('scanner.image', 'releaseQrImage', ['uri' => $uri], static function (NativeModuleResult $result): void {});
    }

    private static function validGeneratedUri(string $uri): bool
    {
        return strlen($uri) <= 8192 && preg_match('~\Afile:///[^\x00-\x1f\x7f?#]+/pam-qr-generated/qr-[a-f0-9-]+\.png\z~i', $uri) === 1;
    }

    private static function validLocalUri(string $uri): bool
    {
        if (strlen($uri) > 8192 || preg_match('/\A(?:file|content|pam-file):\/\/[^\x00-\x1f\x7f]+\z/', $uri) !== 1) return false;
        if (!str_starts_with(strtolower($uri), 'pam-file://')) return true;
        if (preg_match('/%(?![0-9a-f]{2})/i', $uri) === 1) return false;
        if (preg_match('/\Apam-file:\/\/\/([^?#]+)\z/i', $uri, $match) !== 1) return false;
        $segments = explode('/', rawurldecode($match[1]));
        return $segments !== [''] && array_all($segments, static fn (string $segment): bool =>
            $segment !== '' && $segment !== '.' && $segment !== '..'
            && !str_contains($segment, '\\') && preg_match('/[\x00-\x1f\x7f]/', $segment) !== 1
        );
    }
}

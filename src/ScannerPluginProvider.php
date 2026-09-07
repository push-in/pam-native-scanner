<?php

declare(strict_types=1);

namespace Pam\Native\Scanner;

use Closure;
use Pam\Native\Plugin\PluginProvider;
use Pam\Native\TemplateRegistry;

final class ScannerPluginProvider implements PluginProvider
{
    public function register(): void
    {
        TemplateRegistry::component(
            'QrScanner',
            static function (array $props, array $_children, ?object $_scope): ScannerView {
                $events = is_array($props['__pamComponentEvents'] ?? null)
                    ? $props['__pamComponentEvents']
                    : [];

                $scanner = ScannerView::make(BarcodeFormat::QrCode)
                    ->facing(self::facing($props['facing'] ?? null))
                    ->torch(self::boolean($props['torch'] ?? false))
                    ->enabled(self::boolean($props['enabled'] ?? true))
                    ->duplicateInterval(self::integer($props['duplicateInterval'] ?? 1500));

                $detected = $events['detected'] ?? null;
                if ($detected instanceof Closure) {
                    $scanner = $scanner->onResult($detected);
                }

                $failure = $events['failure'] ?? null;
                if ($failure instanceof Closure) {
                    $scanner = $scanner->onError(
                        static fn (ScannerEventKind $kind, string $message): mixed => $failure([
                            'kind' => $kind->value,
                            'message' => $message,
                        ]),
                    );
                }

                return $scanner;
            },
        );
    }

    public function boot(): void {}

    private static function facing(mixed $value): CameraFacing
    {
        if ($value instanceof CameraFacing) {
            return $value;
        }

        return CameraFacing::tryFrom(self::integer($value ?? CameraFacing::Back->value))
            ?? CameraFacing::Back;
    }

    private static function boolean(mixed $value): bool
    {
        if (is_bool($value)) {
            return $value;
        }

        return filter_var($value, FILTER_VALIDATE_BOOL, FILTER_NULL_ON_FAILURE) ?? false;
    }

    private static function integer(mixed $value): int
    {
        return is_int($value) || (is_string($value) && preg_match('/^-?\d+$/D', $value) === 1)
            ? (int) $value
            : 0;
    }
}

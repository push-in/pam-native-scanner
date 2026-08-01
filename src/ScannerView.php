<?php

declare(strict_types=1);

namespace Pam\Native\Scanner;

use Closure;
use Pam\Native\Element;
use Pam\Native\Internal\Wire;
use Pam\Native\Renderable;
use Pam\Native\UI\CustomView;

final class ScannerView implements Renderable
{
    /** @var array<string, string|int|float|bool> */
    private array $properties = ['facing' => 1, 'torch' => false, 'enabled' => true, 'duplicateIntervalMillis' => 1500, 'formats' => '1'];
    private ?Closure $resultHandler = null;
    private ?Closure $errorHandler = null;

    public static function make(BarcodeFormat ...$formats): self
    {
        $scanner = new self();
        $formats = $formats === [] ? [BarcodeFormat::QrCode] : array_values(array_unique($formats, SORT_REGULAR));
        $scanner->properties['formats'] = implode(',', array_map(static fn (BarcodeFormat $format): int => $format->value, $formats));
        return $scanner;
    }
    public function facing(CameraFacing $facing): self { return $this->with('facing', $facing->value); }
    public function torch(bool $enabled = true): self { return $this->with('torch', $enabled); }
    public function enabled(bool $enabled = true): self { return $this->with('enabled', $enabled); }
    public function duplicateInterval(int $milliseconds): self { return $this->with('duplicateIntervalMillis', max(0, min(60_000, $milliseconds))); }
    /** @param Closure(ScanResult): void $handler */
    public function onResult(Closure $handler): self { $copy = clone $this; $copy->resultHandler = $handler; return $copy; }
    /** @param Closure(ScannerEventKind, string): void $handler */
    public function onError(Closure $handler): self { $copy = clone $this; $copy->errorHandler = $handler; return $copy; }

    public function toElement(): Element
    {
        return CustomView::make('scanner.camera', $this->properties)->onNativeEvent(function (string $payload): void {
            $values = Wire::decodeMap($payload);
            $event = ScannerEventKind::tryFrom((int) ($values['event'] ?? 3)) ?? ScannerEventKind::Failure;
            if ($event === ScannerEventKind::Detected && $this->resultHandler !== null) {
                ($this->resultHandler)(new ScanResult(
                    (string) ($values['value'] ?? ''),
                    BarcodeFormat::tryFrom((int) ($values['format'] ?? 14)) ?? BarcodeFormat::Unknown,
                    BarcodeValueKind::tryFrom((int) ($values['valueKind'] ?? 1)) ?? BarcodeValueKind::Unknown,
                ));
            } elseif ($this->errorHandler !== null) {
                ($this->errorHandler)($event, (string) ($values['message'] ?? 'Scanner failure'));
            }
        });
    }
    private function with(string $key, string|int|float|bool $value): self { $copy = clone $this; $copy->properties[$key] = $value; return $copy; }
}

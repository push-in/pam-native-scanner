<?php
declare(strict_types=1); namespace Pam\Native\Scanner; final readonly class ScanResult{public function __construct(public string $value,public BarcodeFormat $format,public BarcodeValueKind $valueKind) {}}

<?php
declare(strict_types=1);
$roots=['Pam\\Native\\Scanner\\'=>dirname(__DIR__).'/src/','Pam\\Native\\'=>dirname(__DIR__,2).'/../pam-native/packages/native/src/'];spl_autoload_register(static function(string$c)use($roots):void{foreach($roots as$p=>$r)if(str_starts_with($c,$p)){$f=$r.str_replace('\\','/',substr($c,strlen($p))).'.php';if(is_file($f))require$f;return;}});
use Pam\Native\Element;use Pam\Native\Scanner\BarcodeFormat;use Pam\Native\Scanner\CameraFacing;use Pam\Native\Scanner\ScannerView;
$tests=[];$test=static function(string$n,Closure$f)use(&$tests):void{$tests[$n]=$f;};
$test('builds a typed native scanner',static function():void{$scanner=ScannerView::make(BarcodeFormat::QrCode,BarcodeFormat::Ean13)->facing(CameraFacing::Back)->torch()->duplicateInterval(800);if(!$scanner->toElement()instanceof Element)throw new RuntimeException('not renderable');});
$test('scanner builder remains immutable',static function():void{$base=ScannerView::make();$changed=$base->torch();if($base===$changed)throw new RuntimeException('builder mutated');});
$test('all coded variants are sequential integers',static function():void{foreach([BarcodeFormat::cases(),CameraFacing::cases()]as$cases){$values=array_map(static fn($c)=>$c->value,$cases);if($values!==range(1,count($values)))throw new RuntimeException('non-sequential enum');}});
$failed=0;foreach($tests as$n=>$f){try{$f();fwrite(STDOUT,"PASS $n\n");}catch(Throwable$e){$failed++;fwrite(STDERR,"FAIL $n: {$e->getMessage()}\n");}}fwrite(STDOUT,count($tests)." tests, $failed failures\n");exit($failed?1:0);

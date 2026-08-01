<?php
declare(strict_types=1); namespace Pam\Native\Scanner; enum BarcodeValueKind:int{case Unknown=1;case Text=2;case Url=3;case Email=4;case Phone=5;case Sms=6;case Wifi=7;case Contact=8;case Calendar=9;case Geo=10;case DriverLicense=11;case Isbn=12;case Product=13;}

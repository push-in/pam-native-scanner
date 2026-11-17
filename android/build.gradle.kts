plugins { id("com.android.library") version "9.2.0" }

android {
    namespace = "dev.pam.scanner"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "dev.pam.scanner.ScannerImageInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.4")
    compileOnly(files(providers.gradleProperty("pamPluginApi").get()))
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    // Unbundled ML Kit: Google Play services ships the barcode model, so the
    // app no longer carries libbarhopper (~5 MB per ABI) and its models.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-base:18.7.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(files(providers.gradleProperty("pamPluginApi").get()))
}

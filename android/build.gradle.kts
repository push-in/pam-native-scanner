plugins { id("com.android.library") version "9.2.0" }

android {
    namespace = "dev.pam.scanner"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "dev.pam.scanner.ScannerImageInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(files(providers.gradleProperty("pamPluginApi").get()))
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(files(providers.gradleProperty("pamPluginApi").get()))
}

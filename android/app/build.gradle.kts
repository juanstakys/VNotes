plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.vnotes"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.vnotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "3.3.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    signingConfigs { getByName("debug") { storeFile = rootProject.file(".signing/debug.keystore") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.maxHeapSize = "512m" } }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("ai.moonshine:moonshine-voice:0.1.5")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
}

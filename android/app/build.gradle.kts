plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.vnotes"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.vnotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.2.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    signingConfigs { getByName("debug") { storeFile = rootProject.file(".signing/debug.keystore") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation("ai.moonshine:moonshine-voice:0.1.5")
    testImplementation("junit:junit:4.13.2")
}

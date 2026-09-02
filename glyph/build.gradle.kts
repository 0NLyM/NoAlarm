plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nothing.ketchum"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

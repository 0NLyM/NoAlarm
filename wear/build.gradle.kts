import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Stessa chiave di firma del telefono (vedi app/build.gradle.kts): un solo
 * keystore per entrambi, cosi' la stessa release CI pubblica un watch APK
 * gia' installabile invece di uno non firmato che adb non puo' mettere sul
 * watch.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

val storeFilePath = secret("storeFile", "NOALARM_STORE_FILE")
val hasSigning = storeFilePath != null && rootProject.file(storeFilePath).exists()

android {
    namespace = "com.noalarm.watch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noalarm.watch"
        // Wear OS 3+: e' la versione minima con cui la Data Layer API e' affidabile.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = secret("storePassword", "NOALARM_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "NOALARM_KEY_ALIAS")
                keyPassword = secret("keyPassword", "NOALARM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.play.services.wearable)
}

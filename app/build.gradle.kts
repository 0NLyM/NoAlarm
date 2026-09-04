import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firma di release. Le credenziali arrivano da `keystore.properties`
 * (locale, non versionato) oppure dalle variabili d'ambiente usate dalla CI.
 * Se mancano, il build di release resta non firmato invece di fallire.
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
    namespace = "com.noalarm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noalarm"
        // La Glyph Matrix SDK reale dichiara minSdk 33: sotto quella soglia il
        // merge del manifest fallirebbe. Coerente con l'unico device che la usa.
        minSdk = 33
        targetSdk = 35
        versionCode = 13
        versionName = "1.3.2"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = secret("storePassword", "NOALARM_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "NOALARM_KEY_ALIAS")
                keyPassword = secret("keyPassword", "NOALARM_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    // SDK ufficiale di Nothing (Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
    // Va inclusa nell'APK: a runtime comunica via AIDL con il servizio di sistema, non e'
    // una libreria fornita dal framework.
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}

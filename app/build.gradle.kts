import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// La chiave di firma vive FUORI dal repo (e fuori da OneDrive), non è mai
// versionata ed è sempre la stessa: cambiarla significherebbe non poter più
// aggiornare le installazioni esistenti.
//
// Dove si trova si dice in uno di questi modi, in ordine:
//   1. local.properties (fuori dal repo):  surplus.keystore.props=/percorso/keystore.properties
//   2. variabile d'ambiente:               SURPLUS_KEYSTORE_PROPS
//   3. un file keystore.properties nella cartella del progetto (ignorato da git)
// Il file indicato contiene storeFile, storePassword, keyAlias, keyPassword.
// Se manca, la build non si rompe: la release resta semplicemente non firmata.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val keystorePropsFile = (
    localProps.getProperty("surplus.keystore.props")
        ?: System.getenv("SURPLUS_KEYSTORE_PROPS")
    )?.let { file(it) } ?: rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

android {
    namespace = "it.gaspa.surplus"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.gaspa.surplus"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "2.7.4"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: offusca e rimuove il codice inutile. Le regole in
            // proguard-rules.pro proteggono i due punti delicati (lettura del
            // file dati e scanner). NON attivare anche shrinkResources senza
            // aver provato lo scanner su un telefono vero: le risorse di ZXing
            // sono referenziate dalla sua Activity, non dal nostro codice.
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            signingConfig =
                if (keystorePropsFile.exists()) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

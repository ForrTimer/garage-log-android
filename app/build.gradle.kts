import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// The Drive-sync web OAuth client ID isn't a secret, but it's environment-specific setup
// (created by whoever runs this build in their own Google Cloud project), so it's read from
// local.properties — same file that already holds sdk.dir — rather than hardcoded in source.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")

// Bob's Managed Agent IDs, written by managed-agents/setup.sh into a gitignored ids.env. They're
// account-specific rather than secret; a build without the file has blank IDs and Bob says he
// isn't set up.
val bobIds = Properties().apply {
    val file = rootProject.file("managed-agents/ids.env")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun bobId(key: String): String = bobIds.getProperty(key, "").trim()

android {
    namespace = "com.garagelog.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.garagelog.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "BOB_AGENT_ID", "\"${bobId("BOB_AGENT_ID")}\"")
        buildConfigField("String", "BOB_ENVIRONMENT_ID", "\"${bobId("BOB_ENVIRONMENT_ID")}\"")
        buildConfigField("String", "BOB_NOTES_STORE_ID", "\"${bobId("BOB_MEMORY_STORE_ID")}\"")
        buildConfigField("String", "BOB_DATA_STORE_ID", "\"${bobId("BOB_DATA_STORE_ID")}\"")
    }

    buildTypes {
        release {
            // R8 (full mode by default since AGP 8) strips unused code and resources: most of the
            // material-icons-extended library, for one. Every library here ships its own keep rules,
            // and the app avoids reflection (Firestore through maps, generated serializers).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed until a release keystore exists, so a release build can be installed and
            // tested (Google sign-in is registered to the debug key's SHA-1). Replace before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Shared garages (multi-user): Auth + Firestore only. No Analytics, deliberately.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.security.crypto)
}

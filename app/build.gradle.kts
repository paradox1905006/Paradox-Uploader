import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.dparadox.paradoxuploader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dparadox.paradoxuploader"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.7.1"
        // Where the app looks for new versions. NOTE: api.github.com enforces a 60 requests/hour
        // limit PER IP for anonymous callers. Phones behind a carrier's shared (CGNAT) IP, or
        // behind a VPN, share that IP with many other people and hit the limit constantly, while
        // WiFi (a less-shared home/office IP) usually doesn't - this is why "check for updates"
        // can fail on mobile data but work on WiFi.
        //
        // raw.githubusercontent.com is a CDN, not the rate-limited API, so update.json committed
        // to the repo and read from there has no such limit. Bump "versionName" in that file on
        // every release.
        buildConfigField("String", "UPDATE_CHECK_URL", "\"https://raw.githubusercontent.com/paradox1905006/Paradox-Uploader/main/update.json\"")
        // Telegram API credentials from my.telegram.org. Optional here: the app can also ask for them at login.
        buildConfigField("int", "TELEGRAM_API_ID", (localProps.getProperty("TELEGRAM_API_ID") ?: "0").trim())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProps.getProperty("TELEGRAM_API_HASH") ?: ""}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TDLib ships ~20 MB of native code per CPU type; keep only what phones need.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.zxing.core)

    // TDLib (Telegram's official MTProto client). Drop a TDLib Android AAR into app/libs/ — see README_TELEGRAM.md.
    // Community prebuilt TDLib 1.8.61 via JitPack (github.com/fazilus/tdlib-android). To use your own build instead,
    // remove this line and put your tdlib .aar in app/libs/.
    implementation("com.github.fazilus:tdlib-android:1.8.61")
    implementation(fileTree("libs") { include("*.aar", "*.jar") })

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
}

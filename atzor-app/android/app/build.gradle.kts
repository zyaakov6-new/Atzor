import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Upload-key signing; keystore/keystore.properties is machine-local. BACK IT UP.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.atzor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.atzor.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 39
        versionName = "1.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Forces a modern Fragment version: zxing-android-embedded pulls in 1.1.0
    // transitively, which fails release lint for the ActivityResult APIs.
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Crashlytics client library: safe to compile against even before Firebase
    // is configured (see CrashReporter.kt, every call is wrapped so it no-ops
    // until google-services.json exists). The Gradle plugins that need the
    // json file are applied conditionally at the bottom of this file.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-crashlytics")
    // Anonymous usage analytics (DAU / retention). The ads-identifier transitive
    // is excluded so no advertising ID is ever pulled in, see AndroidManifest
    // for the matching permission removal and the privacy stance.
    implementation("com.google.firebase:firebase-analytics") {
        exclude(group = "com.google.android.gms", module = "play-services-ads-identifier")
    }

    // Play Integrity API (optional runtime checks; declaration pack lives in docs/).
    implementation("com.google.android.play:integrity:1.4.0")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")

    // Instrumented UI tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Only wired up once you drop a real google-services.json from the Firebase
// Console into this app/ directory (see README's "Crash reporting" section).
// Without it, these two plugins are never applied and the build is unaffected.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

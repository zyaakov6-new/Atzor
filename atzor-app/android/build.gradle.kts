plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Declared but not applied here: app/build.gradle.kts applies these two
    // only if google-services.json exists, so the build stays green without
    // Firebase configured. See app/build.gradle.kts and ios/../SETUP.md sibling
    // note in README for the one manual step (create a Firebase project).
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

plugins {
    id("com.android.application")
}

android {
    namespace = "com.pathlis.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pathlis.offline"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}


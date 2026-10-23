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
    versionCode = 3
    versionName = "1.2"
}

}

dependencies {
implementation("androidx.core:core:1.15.0")

// HTTP requests for Supabase Auth and database
implementation("com.squareup.okhttp3:okhttp:4.12.0")

}

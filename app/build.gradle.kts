plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.bikepacking.karoo"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.bikepacking.karoo"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(libs.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    testImplementation(libs.gson)
}

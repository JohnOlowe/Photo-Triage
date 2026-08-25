plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "damjay.photo.triage"
  compileSdk = 36
  buildToolsVersion = "36.0.0"

  defaultConfig {
    applicationId = "damjay.photo.triage"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    viewBinding = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(libs.androidx.core)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.constraintlayout)
  implementation("com.github.yuyakaido:CardStackView:v2.3.4")

    // Glide for efficient image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Recommended: Material components for standardizing your action buttons
    implementation("com.google.android.material:material:1.11.0")

    // JVM unit tests for the pure-logic helpers (no device needed)
    testImplementation("junit:junit:4.13.2")
}

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

  // Pin every build to one keystore so the APK signature never changes between
  // builds. Credentials live in gradle.properties (not hard-coded here).
  signingConfigs {
    create("stable") {
      val storeFilePath = (project.findProperty("PHOTO_TRIAGE_STORE_FILE") as? String)
        ?: "keystore/photo-triage.keystore"
      storeFile = rootProject.file(storeFilePath)
      storePassword = project.findProperty("PHOTO_TRIAGE_STORE_PASSWORD") as? String
      keyAlias = project.findProperty("PHOTO_TRIAGE_KEY_ALIAS") as? String
      keyPassword = project.findProperty("PHOTO_TRIAGE_KEY_PASSWORD") as? String
    }
  }

  buildTypes {
    getByName("debug") {
      signingConfig = signingConfigs.getByName("stable")
    }
    // To also sign release builds with the same stable key, uncomment:
    // getByName("release") {
    //   signingConfig = signingConfigs.getByName("stable")
    // }
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
  implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
  implementation("com.github.yuyakaido:CardStackView:v2.3.4")

    // Glide for efficient image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // JVM unit tests for the pure-logic helpers (no device needed)
    testImplementation("junit:junit:4.13.2")
}

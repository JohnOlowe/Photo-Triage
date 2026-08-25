pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jcenter.bintray.com/") }
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "Photo Triage"

include(":app")

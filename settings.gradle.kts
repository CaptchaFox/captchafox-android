pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "captchafox-android-sdk"

include(":captchafox-android")
include(":captchafox-android-compose")
include(":public-interface-tests")
include(":live-tests")
include(":example")

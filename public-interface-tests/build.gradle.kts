plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Tests that use the artifacts only as an app does, through their public interface.
android {
    namespace = "com.captchafox.android.publicinterface"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        managedDevices {
            localDevices {
                create("testDevice") {
                    device = "Pixel 6"
                    apiLevel = libs.versions.test.device.api.get().toInt()
                    systemImageSource = "google"
                }
            }
        }
    }
}

dependencies {
    androidTestImplementation(project(":captchafox-android"))
    androidTestImplementation(project(":captchafox-android-compose"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.material3)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

plugins {
    alias(libs.plugins.android.library)
}

// Tests against cdn.captchafox.com that use the view only as an app does.
android {
    namespace = "com.captchafox.android.live"
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
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

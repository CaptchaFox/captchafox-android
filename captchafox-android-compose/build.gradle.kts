import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.captchafox.android.compose"
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

kotlin {
    explicitApi()
    // Apps read the library with their own Kotlin compiler; 2.2 is what kotlinx-coroutines needs already.
    coreLibrariesVersion = "2.2.20"
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        languageVersion = KotlinVersion.KOTLIN_2_2
        apiVersion = KotlinVersion.KOTLIN_2_2
    }
}

dependencies {
    api(project(":captchafox-android"))
    // Without the BOM, so that apps get these versions or later and keep their own for the rest of Compose.
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Compose's test library brings an Espresso that fails on recent Android versions.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.compose.material3)
    androidTestImplementation(libs.androidx.webkit)
    androidTestImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = "CaptchaFox for Android, Compose"
        description = "Shows the CaptchaFox widget in Jetpack Compose and hands its token to the app."
        inceptionYear = "2026"
    }
}

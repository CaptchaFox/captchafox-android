import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.captchafox.android"
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.uiautomator)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = "CaptchaFox for Android"
        description = "Shows the CaptchaFox widget in Android apps and hands its token to the app."
        inceptionYear = "2026"
    }
}

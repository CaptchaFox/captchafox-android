plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
}

// The instrumented tests run on the managed device "testDevice", which Gradle creates; the modules run one after another.
tasks.register("deviceTest") {
    group = "verification"
    description = "Runs the instrumented tests on the managed device, without the network."
    dependsOn(
        ":captchafox-android:testDeviceDebugAndroidTest",
        ":captchafox-android-compose:testDeviceDebugAndroidTest",
        ":public-interface-tests:testDeviceDebugAndroidTest",
    )
}

tasks.register("liveTest") {
    group = "verification"
    description = "Runs the tests against cdn.captchafox.com with the public test site keys on the managed device."
    dependsOn(":live-tests:testDeviceDebugAndroidTest")
}

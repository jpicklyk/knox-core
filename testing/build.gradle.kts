plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.library.compose)
    alias(libs.plugins.convention.android.hilt)
}

android {
    namespace = "net.sfelabs.knox.core.testing"
}

dependencies {
    api(libs.androidx.compose.ui.test)
    api(libs.androidx.test.core)
    api(libs.androidx.test.espresso.core)
    api(libs.androidx.test.rules)
    api(libs.androidx.test.runner)
    api(libs.androidx.test.uiautomator)
    api(libs.hilt.android.testing)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    implementation(project(":knox-core:common"))
    implementation(project(":knox-core:android"))
    implementation(libs.kotlin.reflect)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
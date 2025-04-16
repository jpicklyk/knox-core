plugins {
    id("convention.android.library")
    id("convention.android.library.compose")
    id("convention.android.hilt")
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
    implementation(libs.kotlin.reflect)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
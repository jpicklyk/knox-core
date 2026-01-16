plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    namespace = "net.sfelabs.knox.core.ui"
}

dependencies {

    implementation(libs.androidx.core.ktx)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)
    /*
    api(libs.androidx.compose.foundation)

    api(libs.androidx.compose.runtime)
    */
    api(libs.androidx.lifecycle.viewModelCompose)

    //debugImplementation(libs.androidx.compose.ui.tooling.preview)
    //implementation(libs.androidx.compose.ui.tooling.preview)


    testImplementation(libs.junit)
    androidTestImplementation(project(":knox-core:testing"))
}
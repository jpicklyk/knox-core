plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.library.compose)
    alias(libs.plugins.convention.android.hilt)
    //id("dagger.hilt.android.plugin")
    //id("kotlin-kapt")
}

android {
    namespace = "net.sfelabs.knox.core.common"
}

dependencies {
    implementation(libs.androidx.collections)
    implementation(libs.kotlinx.coroutines.android)
    //implementation(libs.jackson.module)
    implementation(project(":knox-core:usecase-executor"))
    implementation(project(":knox-core:android"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.androidx.compose.ui.android)
    //implementation(libs.hilt.android)
    //kapt("com.google.dagger:hilt-compiler")
    androidTestImplementation(project(":knox-core:testing"))
    androidTestImplementation(project(":knox-core:usecase-executor"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk.core)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.mockk.android)

}


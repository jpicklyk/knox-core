plugins {
    id("convention.android.library")
    id("convention.android.hilt")
    id("convention.android.library.compose")
    //id("dagger.hilt.android.plugin")
    //id("kotlin-kapt")
}

android {
    namespace = "net.sfelabs.knox.common"
}

dependencies {
    implementation(libs.androidx.collections)
    implementation(libs.kotlinx.coroutines.android)
    //implementation(libs.jackson.module)
    implementation(project(":knox-core:usecase-executor"))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.viewModelCompose)
    api(libs.androidx.datastore)
    api(libs.androidx.datastore.preferences)
    testImplementation(libs.androidx.compose.ui.android)
    //implementation(libs.hilt.android)
    //kapt("com.google.dagger:hilt-compiler")
    androidTestImplementation(project(":knox-core:testing"))
    androidTestImplementation(project(":knox-core:usecase-executor"))
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk.core)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.mockk.android)

}


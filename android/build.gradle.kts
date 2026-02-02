plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.hilt)
}

android {
    namespace = "net.sfelabs.knox.core.android"

}

dependencies {
    implementation(projects.knoxCore.usecaseExecutor)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.hilt)
}

android {
    namespace = "net.sfelabs.knox.android"

}

dependencies {
    implementation(project(":knox-core:usecase-executor"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
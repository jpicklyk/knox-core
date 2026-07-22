import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.hilt)
}

extensions.configure<LibraryExtension> {
    namespace = "net.sfelabs.knox.core.android"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
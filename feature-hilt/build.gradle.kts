plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.hilt)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.sfelabs.knox.core.feature.hilt"

    packaging {
        resources {
            excludes += arrayOf(
                "/META-INF/{LICENSE.md,LICENSE-notice.md}"
            )
        }
    }
}

dependencies {
    implementation(project(":knox-core:usecase-executor"))
    implementation(project(":knox-core:feature"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.mockk.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
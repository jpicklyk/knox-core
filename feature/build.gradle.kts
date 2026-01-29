plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":knox-core:usecase-executor"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlin.poet)
    implementation(libs.kotlin.poet.ksp)
    implementation(libs.ksp.gradlePlugin)

    testImplementation(libs.junit)
    testImplementation(libs.mockk.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
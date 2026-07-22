plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(projects.knoxCore.usecaseExecutor)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
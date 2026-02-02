plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.io)
    //ksp(libs.kotlinx.io)
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlin.poet)
    implementation(libs.kotlin.poet.ksp)
    implementation(projects.knoxCore.usecaseExecutor)
    implementation(projects.knoxCore.feature)
    // For shared models/interfaces
}

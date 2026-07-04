plugins {
    alias(libs.plugins.lifeos.android.library)
    alias(libs.plugins.lifeos.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.designsystem)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
}

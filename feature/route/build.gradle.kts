plugins {
    alias(libs.plugins.lifeos.android.library)
    alias(libs.plugins.lifeos.android.compose)
    alias(libs.plugins.lifeos.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.ai)
    implementation(projects.core.network)
    implementation(projects.core.ui)
    implementation(projects.core.service)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.osmdroid)
    testImplementation(libs.turbine)
}
